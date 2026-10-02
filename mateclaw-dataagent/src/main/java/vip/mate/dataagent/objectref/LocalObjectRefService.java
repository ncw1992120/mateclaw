package vip.mate.dataagent.objectref;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import vip.mate.dataagent.dataset.DatasetAccessContext;
import vip.mate.dataagent.dataset.DatasetReadErrorCode;
import vip.mate.dataagent.dataset.DatasetReadException;
import vip.mate.dataagent.dataset.ObjectRef;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/** Filesystem-backed temporary object references, scoped to their creating task and workspace. */
@Service
public class LocalObjectRefService implements ObjectRefService {
    private final LocalObjectFileStore files;
    private final long ttlSeconds;

    @Autowired
    public LocalObjectRefService(
            @Value("${mateclaw.storage.root:${java.io.tmpdir}/mateclaw-storage}") Path storageRoot,
            @Value("${mateclaw.storage.object-ref.ttl-seconds:3600}") long ttlSeconds) {
        this(new LocalObjectFileStore(storageRoot), ttlSeconds);
    }

    private LocalObjectRefService(LocalObjectFileStore files, long ttlSeconds) {
        this.files = files;
        this.ttlSeconds = ttlSeconds;
    }

    @Override
    public ObjectRef put(DatasetAccessContext context, String format, InputStream content) {
        requireContext(context);
        if (format == null || format.isBlank() || content == null) throw invalid("format and content are required");
        if (context.taskId().contains("/") || context.taskId().contains("\\")
                || context.taskId().equals(".") || context.taskId().equals("..")) {
            throw invalid("task id is not a valid object key segment");
        }
        String objectId = context.taskId() + "/" + UUID.randomUUID();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            files.write(objectId, new DigestInputStream(content, digest));
            long expiresAt = Instant.now().plusSeconds(ttlSeconds).toEpochMilli();
            return new ObjectRef(objectId, context.workspaceId(), context.taskId(), format,
                    "sha256:" + HexFormat.of().formatHex(digest.digest()), expiresAt);
        } catch (DatasetReadException e) {
            throw e;
        } catch (Exception e) {
            throw new DatasetReadException(DatasetReadErrorCode.SOURCE_UNAVAILABLE, "对象写入失败", e);
        }
    }

    @Override
    public InputStream open(DatasetAccessContext context, ObjectRef reference) {
        requireContext(context);
        boolean managedDatasetObject = reference != null && reference.objectId() != null
                && reference.objectId().startsWith("datasets/" + context.workspaceId() + "/");
        if (reference == null || (!managedDatasetObject && !context.taskId().equals(reference.taskId()))
                || !context.workspaceId().equals(reference.workspaceId())) throw accessDenied();
        if (!managedDatasetObject && reference.expiresAt() <= Instant.now().toEpochMilli()) {
            try {
                files.delete(reference.objectId());
            } catch (Exception ignored) {
                // Expiry rejection is authoritative; cleanup is best effort.
            }
            throw new DatasetReadException(DatasetReadErrorCode.ACCESS_DENIED, "对象已过期");
        }
        try {
            InputStream input = files.open(reference.objectId());
            return managedDatasetObject ? input : new DigestVerifyingInputStream(input, reference.digest());
        } catch (DatasetReadException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw new DatasetReadException(DatasetReadErrorCode.INVALID_REQUEST, "对象键无效", e);
        } catch (Exception e) {
            throw new DatasetReadException(DatasetReadErrorCode.SOURCE_UNAVAILABLE, "对象读取失败", e);
        }
    }

    @Override
    public void expire(ObjectRef reference) {
        if (reference == null) return;
        try {
            files.delete(reference.objectId());
        } catch (IOException e) {
            throw new DatasetReadException(DatasetReadErrorCode.SOURCE_UNAVAILABLE, "对象删除失败", e);
        } catch (IllegalArgumentException e) {
            throw new DatasetReadException(DatasetReadErrorCode.INVALID_REQUEST, "对象键无效", e);
        }
    }

    private void requireContext(DatasetAccessContext context) {
        if (context == null) throw accessDenied();
    }

    private DatasetReadException accessDenied() {
        return new DatasetReadException(DatasetReadErrorCode.ACCESS_DENIED, "对象引用上下文不匹配");
    }

    private DatasetReadException invalid(String message) {
        return new DatasetReadException(DatasetReadErrorCode.INVALID_REQUEST, message);
    }

    private static final class DigestVerifyingInputStream extends FilterInputStream {
        private final MessageDigest digest;
        private final String expected;
        private boolean verified;

        private DigestVerifyingInputStream(InputStream delegate, String expected) throws Exception {
            super(delegate);
            this.digest = MessageDigest.getInstance("SHA-256");
            this.expected = expected;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) digest.update((byte) value);
            else verify();
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = super.read(buffer, offset, length);
            if (read > 0) digest.update(buffer, offset, read);
            else if (read < 0) verify();
            return read;
        }

        @Override
        public void close() throws IOException {
            IOException failure = null;
            try {
                byte[] buffer = new byte[8192];
                while (read(buffer) != -1) {
                    // Drain unread bytes so verification covers the complete stored object.
                }
            } catch (IOException e) {
                failure = e;
            }
            try {
                super.close();
            } catch (IOException e) {
                if (failure == null) failure = e;
                else failure.addSuppressed(e);
            }
            if (failure != null) throw failure;
        }

        private void verify() throws IOException {
            if (verified) return;
            verified = true;
            String actual = "sha256:" + HexFormat.of().formatHex(digest.digest());
            if (!actual.equals(expected)) throw new IOException("object digest mismatch");
        }
    }
}
