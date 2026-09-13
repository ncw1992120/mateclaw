package vip.mate.dataagent.dataset;

/** 所有数据源共享的目录与读取契约。 */
public interface DatasetSourceAdapter {
    boolean supports(DatasetSourceType sourceType);

    DatasetInputDescriptor describe(DatasetAccessContext context, long datasetId);

    DatasetBatch read(DatasetAccessContext context, DatasetReadRequest request);
}
