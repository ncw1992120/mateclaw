package vip.mate.dataagent.dataset.file;

/** 持久上传对象引用；与任务级 ObjectRef 分离，不包含任何存储凭据。 */
public record StoredFileRef(String objectId, Long workspaceId, Long ownerId, String fileName,
                            String format, long size, String digest) {
    public StoredFileRef {
        if (objectId == null || objectId.isBlank() || workspaceId == null || ownerId == null
                || fileName == null || fileName.isBlank() || format == null || format.isBlank()
                || size < 0 || digest == null || digest.isBlank()) throw new IllegalArgumentException("incomplete stored file reference");
        if (objectId.contains("..") || objectId.startsWith("/") || objectId.contains("\\")) throw new IllegalArgumentException("invalid object key");
    }
}
