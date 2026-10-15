package vip.mate.dataagent.dataset.file;

/** 文件探测/读取的资源上限。 */
public record FileReadOptions(int sampleRows, int maxColumns, long maxBytes) {
    public FileReadOptions {
        if (sampleRows <= 0 || sampleRows > 10_000) throw new IllegalArgumentException("sampleRows out of range");
        if (maxColumns <= 0 || maxColumns > 100) throw new IllegalArgumentException("maxColumns out of range");
        if (maxBytes <= 0 || maxBytes > 100L * 1024 * 1024) throw new IllegalArgumentException("maxBytes out of range");
    }
    public static FileReadOptions defaults() { return new FileReadOptions(1000, 100, 100L * 1024 * 1024); }
}
