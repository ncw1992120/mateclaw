package vip.mate.dataagent.dataset;

/** 数据集字段的稳定描述。 */
public record DatasetColumn(
        String name,
        String title,
        String dataType,
        boolean nullable,
        String semanticRole) {
    public DatasetColumn {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("column name must not be blank");
        }
        if (dataType == null || dataType.isBlank()) {
            throw new IllegalArgumentException("column dataType must not be blank");
        }
    }
}
