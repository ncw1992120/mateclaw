package vip.mate.dataagent.objectref;

import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.Item;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vip.mate.dataagent.dataset.DatasetAccessContext;
import vip.mate.dataagent.dataset.DatasetReadErrorCode;
import vip.mate.dataagent.dataset.DatasetReadException;
import vip.mate.dataagent.dataset.ObjectRef;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/** MinIO/S3 临时对象引用服务；对象键不向 Runner 暴露可推测的 bucket 路径。 */
@Service
public class S3ObjectRefService implements ObjectRefService {
    private final MinioClient client;
    private final String bucket;
    private final long ttlSeconds;

    public S3ObjectRefService(
            @Value("${mateclaw.object-ref.endpoint:http://minio:9000}") String endpoint,
            @Value("${mateclaw.object-ref.access-key:}") String accessKey,
            @Value("${mateclaw.object-ref.secret-key:}") String secretKey,
            @Value("${mateclaw.object-ref.bucket:mateclaw-temp}") String bucket,
            @Value("${mateclaw.object-ref.ttl-seconds:3600}") long ttlSeconds) {
        MinioClient.Builder builder = MinioClient.builder().endpoint(endpoint);
        if (!accessKey.isBlank() && !secretKey.isBlank()) {
            builder.credentials(accessKey, secretKey);
        }
        this.client = builder.build();
        this.bucket = bucket;
        this.ttlSeconds = ttlSeconds;
    }

    @Override
    public ObjectRef put(DatasetAccessContext context, String format, InputStream content) {
        requireContext(context);
        if (format == null || format.isBlank() || content == null) throw invalid("format and content are required");
        Path temp = null;
        try {
            temp = Files.createTempFile("mateclaw-object-", ".bin");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (OutputStream out = Files.newOutputStream(temp)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = content.read(buffer)) != -1) { out.write(buffer, 0, read); digest.update(buffer, 0, read); }
            }
            ensureBucket();
            String objectId = context.taskId() + "/" + UUID.randomUUID();
            client.putObject(PutObjectArgs.builder().bucket(bucket).object(objectId)
                    .stream(Files.newInputStream(temp), Files.size(temp), -1)
                    .contentType(contentType(format)).build());
            long expiresAt = Instant.now().plusSeconds(ttlSeconds).toEpochMilli();
            return new ObjectRef(objectId, context.workspaceId(), context.taskId(), format,
                    "sha256:" + HexFormat.of().formatHex(digest.digest()), expiresAt);
        } catch (DatasetReadException e) { throw e;
        } catch (Exception e) { throw new DatasetReadException(DatasetReadErrorCode.SOURCE_UNAVAILABLE, "对象写入失败", e);
        } finally { if (temp != null) try { Files.deleteIfExists(temp); } catch (IOException ignored) { } }
    }

    @Override
    public InputStream open(DatasetAccessContext context, ObjectRef reference) {
        requireContext(context);
        boolean managedDatasetObject = reference != null && reference.objectId() != null
                && reference.objectId().startsWith("datasets/" + context.workspaceId() + "/");
        if (reference == null || (!managedDatasetObject && !context.taskId().equals(reference.taskId()))
                || !context.workspaceId().equals(reference.workspaceId())) throw accessDenied();
        if (!managedDatasetObject && reference.expiresAt() <= Instant.now().toEpochMilli()) {
            throw new DatasetReadException(DatasetReadErrorCode.ACCESS_DENIED, "对象已过期");
        }
        try {
            InputStream input = client.getObject(GetObjectArgs.builder().bucket(bucket).object(reference.objectId()).build());
            return managedDatasetObject ? input : new DigestVerifyingInputStream(input, reference.digest());
        } catch (Exception e) { throw new DatasetReadException(DatasetReadErrorCode.SOURCE_UNAVAILABLE, "对象读取失败", e); }
    }

    @Override
    public void expire(ObjectRef reference) {
        if (reference == null) return;
        try { client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(reference.objectId()).build()); }
        catch (ErrorResponseException e) {
            if (e.errorResponse() != null && e.errorResponse().code() != null
                    && (e.errorResponse().code().equals("NoSuchKey") || e.errorResponse().code().equals("NoSuchBucket"))) return;
            throw new DatasetReadException(DatasetReadErrorCode.SOURCE_UNAVAILABLE, "对象删除失败", e);
        }
        catch (Exception e) { throw new DatasetReadException(DatasetReadErrorCode.SOURCE_UNAVAILABLE, "对象删除失败", e); }
    }

    private void ensureBucket() throws Exception {
        if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build()))
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
    }

    private void requireContext(DatasetAccessContext context) { if (context == null) throw accessDenied(); }
    private DatasetReadException accessDenied() { return new DatasetReadException(DatasetReadErrorCode.ACCESS_DENIED, "对象引用上下文不匹配"); }
    private DatasetReadException invalid(String message) { return new DatasetReadException(DatasetReadErrorCode.INVALID_REQUEST, message); }
    private String contentType(String format) { return switch (format.toLowerCase()) { case "parquet" -> "application/vnd.apache.parquet"; case "arrow" -> "application/vnd.apache.arrow.stream"; default -> "application/octet-stream"; }; }

    private static final class DigestVerifyingInputStream extends FilterInputStream {
        private final MessageDigest digest;
        private final String expected;
        private boolean verified;
        DigestVerifyingInputStream(InputStream delegate, String expected) throws Exception { super(delegate); this.digest = MessageDigest.getInstance("SHA-256"); this.expected = expected; }
        @Override public int read() throws IOException { int value = super.read(); if (value >= 0) digest.update((byte) value); else verify(); return value; }
        @Override public int read(byte[] b, int off, int len) throws IOException { int n = super.read(b, off, len); if (n > 0) digest.update(b, off, n); else if (n < 0) verify(); return n; }
        @Override public void close() throws IOException { try { verify(); } finally { super.close(); } }
        private void verify() throws IOException { if (verified) return; verified = true; String actual = "sha256:" + HexFormat.of().formatHex(digest.digest()); if (!actual.equals(expected)) throw new IOException("object digest mismatch"); }
    }
}
