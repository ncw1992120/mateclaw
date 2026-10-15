package vip.mate.dataagent.dataset.file;

import vip.mate.dataagent.dataset.ObjectRef;

/** 已登记文件数据集的内部配置；路径只允许是平台对象键。 */
public record FileDatasetDefinition(ObjectRef objectRef, String fileName, String format, int schemaVersion) {
    public FileDatasetDefinition {
        if (objectRef == null || fileName == null || fileName.isBlank()
                || format == null || format.isBlank() || schemaVersion <= 0) {
            throw new IllegalArgumentException("incomplete file dataset definition");
        }
    }
}
