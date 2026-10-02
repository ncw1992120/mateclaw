package vip.mate.dataagent.service.impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vip.mate.dataagent.dataset.DatasetAccessContext;
import vip.mate.dataagent.dataset.DatasetReadErrorCode;
import vip.mate.dataagent.dataset.DatasetReadException;
import vip.mate.dataagent.dataset.file.StoredFileRef;
import vip.mate.dataagent.objectref.LocalObjectFileStore;
import vip.mate.dataagent.service.DatasetFileStorageService;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Persistent uploaded dataset files, isolated beneath their workspace key prefix. */
@Service
public class LocalDatasetFileStorageService implements DatasetFileStorageService {
    private static final long MAX_FILE_BYTES = 100L * 1024 * 1024;
    private static final long MAX_ARCHIVE_UNCOMPRESSED_BYTES = 500L * 1024 * 1024;
    private static final int MAX_ARCHIVE_ENTRIES = 2_000;
    private static final long MAX_ARCHIVE_COMPRESSION_RATIO = 100L;

    private final LocalObjectFileStore files;

    public LocalDatasetFileStorageService(
            @Value("${mateclaw.storage.root:${java.io.tmpdir}/mateclaw-storage}") Path storageRoot) {
        this(new LocalObjectFileStore(storageRoot));
    }

    LocalDatasetFileStorageService(LocalObjectFileStore files) {
        this.files = files;
    }

    @Override
    public StoredFileRef put(DatasetAccessContext context, Long ownerId, String fileName,
                             InputStream content, long size) {
        require(context);
        if (ownerId == null || fileName == null || fileName.isBlank() || content == null
                || size < 0 || size > MAX_FILE_BYTES) {
            throw invalid("invalid file upload");
        }
        String format = extension(fileName);
        Path temporary = null;
        long count = 0;
        try {
            temporary = Files.createTempFile("mateclaw-upload-", ".bin");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (OutputStream output = Files.newOutputStream(temporary)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = content.read(buffer)) != -1) {
                    count += read;
                    if (count > MAX_FILE_BYTES) throw invalid("file exceeds 100MB");
                    output.write(buffer, 0, read);
                    digest.update(buffer, 0, read);
                }
            }
            if (size != 0 && size != count) throw invalid("declared file size mismatch");
            validateSignature(format, temporary);
            validateArchive(format, temporary);

            String objectId = "datasets/" + context.workspaceId() + "/" + UUID.randomUUID();
            try (InputStream input = Files.newInputStream(temporary)) {
                files.write(objectId, input);
            }
            return new StoredFileRef(objectId, context.workspaceId(), ownerId, fileName, format, count,
                    "sha256:" + HexFormat.of().formatHex(digest.digest()));
        } catch (DatasetReadException e) {
            throw e;
        } catch (Exception e) {
            throw new DatasetReadException(DatasetReadErrorCode.SOURCE_UNAVAILABLE, "文件上传失败", e);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // Best-effort cleanup of the validated upload spool.
                }
            }
        }
    }

    @Override
    public InputStream open(DatasetAccessContext context, StoredFileRef reference) {
        require(context);
        if (!belongsToWorkspace(context, reference)) {
            throw new DatasetReadException(DatasetReadErrorCode.ACCESS_DENIED, "文件引用上下文不匹配");
        }
        try {
            return files.open(reference.objectId());
        } catch (IllegalArgumentException e) {
            throw new DatasetReadException(DatasetReadErrorCode.INVALID_REQUEST, "文件对象键无效", e);
        } catch (Exception e) {
            throw new DatasetReadException(DatasetReadErrorCode.SOURCE_UNAVAILABLE, "文件读取失败", e);
        }
    }

    @Override
    public void delete(DatasetAccessContext context, StoredFileRef reference) {
        require(context);
        if (!belongsToWorkspace(context, reference)) {
            throw new DatasetReadException(DatasetReadErrorCode.ACCESS_DENIED, "文件引用上下文不匹配");
        }
        try {
            files.delete(reference.objectId());
        } catch (IllegalArgumentException e) {
            throw new DatasetReadException(DatasetReadErrorCode.INVALID_REQUEST, "文件对象键无效", e);
        } catch (Exception e) {
            throw new DatasetReadException(DatasetReadErrorCode.SOURCE_UNAVAILABLE, "文件删除失败", e);
        }
    }

    private boolean belongsToWorkspace(DatasetAccessContext context, StoredFileRef reference) {
        return reference != null
                && context.workspaceId().equals(reference.workspaceId())
                && reference.objectId().startsWith("datasets/" + context.workspaceId() + "/");
    }

    private void require(DatasetAccessContext context) {
        if (context == null) throw new DatasetReadException(DatasetReadErrorCode.ACCESS_DENIED, "缺少数据集授权上下文");
    }

    private DatasetReadException invalid(String message) {
        return new DatasetReadException(DatasetReadErrorCode.INVALID_REQUEST, message);
    }

    private String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) throw invalid("file extension is required");
        String format = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!Set.of("csv", "json", "xlsx", "parquet").contains(format)) {
            throw invalid("unsupported file format: " + format);
        }
        return format;
    }

    private void validateSignature(String format, Path file) throws IOException {
        byte[] head;
        try (InputStream input = Files.newInputStream(file)) {
            head = input.readNBytes(256);
        }
        if (head.length == 0) throw invalid("empty file");
        if ("parquet".equals(format)
                && !(head.length >= 4 && head[0] == 'P' && head[1] == 'A' && head[2] == 'R' && head[3] == '1')) {
            throw invalid("Parquet signature mismatch");
        }
        if ("xlsx".equals(format) && !(head.length >= 2 && head[0] == 'P' && head[1] == 'K')) {
            throw invalid("XLSX signature mismatch");
        }
        if ("json".equals(format)) {
            String text = new String(head, java.nio.charset.StandardCharsets.UTF_8).trim();
            if (!(text.startsWith("[") || text.startsWith("{"))) throw invalid("JSON signature mismatch");
        }
    }

    private void validateArchive(String format, Path file) throws IOException {
        if (!"xlsx".equals(format)) return;
        long entries = 0;
        long uncompressed = 0;
        long compressed = 0;
        try (ZipFile zip = new ZipFile(file.toFile())) {
            var iterator = zip.entries();
            while (iterator.hasMoreElements()) {
                ZipEntry entry = iterator.nextElement();
                if (entry.isDirectory()) continue;
                if (++entries > MAX_ARCHIVE_ENTRIES) throw invalid("XLSX contains too many archive entries");
                long entrySize = entry.getSize();
                long compressedSize = entry.getCompressedSize();
                if (entrySize > 0) uncompressed = Math.addExact(uncompressed, entrySize);
                if (compressedSize > 0) compressed = Math.addExact(compressed, compressedSize);
                if (uncompressed > MAX_ARCHIVE_UNCOMPRESSED_BYTES) {
                    throw invalid("XLSX uncompressed size exceeds limit");
                }
            }
        } catch (ArithmeticException e) {
            throw invalid("XLSX archive size overflow");
        }
        if (uncompressed > 0 && (compressed == 0
                || uncompressed / Math.max(1L, compressed) > MAX_ARCHIVE_COMPRESSION_RATIO)) {
            throw invalid("XLSX compression ratio exceeds limit");
        }
    }
}
