package vip.mate.dataagent.service.impl;

import io.minio.*;
import io.minio.errors.ErrorResponseException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vip.mate.dataagent.dataset.*;
import vip.mate.dataagent.dataset.file.StoredFileRef;
import vip.mate.dataagent.service.DatasetFileStorageService;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** 持久上传文件的 S3/MinIO 存储；对象键按 workspace 隔离。 */
@Service
public class S3DatasetFileStorageService implements DatasetFileStorageService {
    private static final long MAX_FILE_BYTES = 100L * 1024 * 1024;
    private static final long MAX_ARCHIVE_UNCOMPRESSED_BYTES = 500L * 1024 * 1024;
    private static final int MAX_ARCHIVE_ENTRIES = 2_000;
    private static final long MAX_ARCHIVE_COMPRESSION_RATIO = 100L;
    private final MinioClient client; private final String bucket;

    public S3DatasetFileStorageService(@Value("${mateclaw.object-ref.endpoint:http://minio:9000}") String endpoint,
                                       @Value("${mateclaw.object-ref.access-key:}") String access,
                                       @Value("${mateclaw.object-ref.secret-key:}") String secret,
                                       @Value("${mateclaw.object-ref.bucket:mateclaw-temp}") String bucket) {
        MinioClient.Builder b=MinioClient.builder().endpoint(endpoint); if(!access.isBlank()&&!secret.isBlank()) b.credentials(access,secret); this.client=b.build(); this.bucket=bucket;
    }
    @Override public StoredFileRef put(DatasetAccessContext context, Long ownerId, String fileName, InputStream content, long size) {
        require(context); if(ownerId==null||fileName==null||fileName.isBlank()||content==null||size<0||size>MAX_FILE_BYTES) throw invalid("invalid file upload");
        String format=extension(fileName); Path temp=null; long count=0; try {
            temp=Files.createTempFile("mateclaw-upload-", ".bin"); MessageDigest digest=MessageDigest.getInstance("SHA-256");
            try(OutputStream out=Files.newOutputStream(temp)){ byte[] buf=new byte[8192]; int n; while((n=content.read(buf))!=-1){ if((count+=n)>MAX_FILE_BYTES) throw invalid("file exceeds 100MB"); out.write(buf,0,n); digest.update(buf,0,n); } }
            if(size!=0&&size!=count) throw invalid("declared file size mismatch"); ensureBucket(); String objectId="datasets/"+context.workspaceId()+"/"+UUID.randomUUID();
            validateSignature(format, temp); validateArchive(format, temp);
            try(InputStream in=Files.newInputStream(temp)){ client.putObject(PutObjectArgs.builder().bucket(bucket).object(objectId).stream(in,count,-1).contentType(contentType(format)).build()); }
            return new StoredFileRef(objectId,context.workspaceId(),ownerId,fileName,format,count,"sha256:"+HexFormat.of().formatHex(digest.digest()));
        } catch(DatasetReadException e){throw e;} catch(Exception e){throw new DatasetReadException(DatasetReadErrorCode.SOURCE_UNAVAILABLE,"文件上传失败",e);} finally {if(temp!=null)try{Files.deleteIfExists(temp);}catch(IOException ignored){}}
    }
    @Override public InputStream open(DatasetAccessContext context, StoredFileRef ref){ require(context); if(ref==null||!context.workspaceId().equals(ref.workspaceId())||!ref.objectId().startsWith("datasets/"+context.workspaceId()+"/")) throw new DatasetReadException(DatasetReadErrorCode.ACCESS_DENIED,"文件引用上下文不匹配"); try{return client.getObject(GetObjectArgs.builder().bucket(bucket).object(ref.objectId()).build());}catch(Exception e){throw new DatasetReadException(DatasetReadErrorCode.SOURCE_UNAVAILABLE,"文件读取失败",e);} }
    @Override public void delete(DatasetAccessContext context, StoredFileRef ref){ require(context); if(ref==null||!context.workspaceId().equals(ref.workspaceId())) throw new DatasetReadException(DatasetReadErrorCode.ACCESS_DENIED,"文件引用上下文不匹配"); try{client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(ref.objectId()).build());}catch(ErrorResponseException e){if(e.errorResponse()!=null&&"NoSuchKey".equals(e.errorResponse().code()))return;throw new DatasetReadException(DatasetReadErrorCode.SOURCE_UNAVAILABLE,"文件删除失败",e);}catch(Exception e){throw new DatasetReadException(DatasetReadErrorCode.SOURCE_UNAVAILABLE,"文件删除失败",e);} }
    private void ensureBucket() throws Exception{if(!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build()))client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());}
    private void require(DatasetAccessContext c){if(c==null)throw new DatasetReadException(DatasetReadErrorCode.ACCESS_DENIED,"缺少数据集授权上下文");}
    private DatasetReadException invalid(String s){return new DatasetReadException(DatasetReadErrorCode.INVALID_REQUEST,s);}
    private String extension(String n){int i=n.lastIndexOf('.');if(i<0)throw invalid("file extension is required");String f=n.substring(i+1).toLowerCase(Locale.ROOT);if(!java.util.Set.of("csv","json","xlsx","parquet").contains(f))throw invalid("unsupported file format: "+f);return f;}
    private String contentType(String f){return switch(f){case "json"->"application/json";case "csv"->"text/csv";case "xlsx"->"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";case "parquet"->"application/vnd.apache.parquet";default->"application/octet-stream";};}
    private void validateSignature(String format, Path file) throws IOException {
        byte[] head; try (InputStream in = Files.newInputStream(file)) { head = in.readNBytes(256); }
        if (head.length == 0) throw invalid("empty file");
        if ("parquet".equals(format) && !(head.length >= 4 && head[0]=='P' && head[1]=='A' && head[2]=='R' && head[3]=='1')) throw invalid("Parquet signature mismatch");
        if ("xlsx".equals(format) && !(head.length >= 2 && head[0]=='P' && head[1]=='K')) throw invalid("XLSX signature mismatch");
        if ("json".equals(format)) { String text=new String(head, java.nio.charset.StandardCharsets.UTF_8).trim(); if (!(text.startsWith("[") || text.startsWith("{"))) throw invalid("JSON signature mismatch"); }
    }
    private void validateArchive(String format, Path file) throws IOException {
        if (!"xlsx".equals(format)) return;
        long entries = 0, uncompressed = 0, compressed = 0;
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
                if (uncompressed > MAX_ARCHIVE_UNCOMPRESSED_BYTES) throw invalid("XLSX uncompressed size exceeds limit");
            }
        } catch (ArithmeticException e) {
            throw invalid("XLSX archive size overflow");
        }
        if (uncompressed > 0 && (compressed == 0 || uncompressed / Math.max(1L, compressed) > MAX_ARCHIVE_COMPRESSION_RATIO)) {
            throw invalid("XLSX compression ratio exceeds limit");
        }
    }
}
