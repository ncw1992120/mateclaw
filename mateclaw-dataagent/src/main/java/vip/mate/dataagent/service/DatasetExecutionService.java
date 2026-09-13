package vip.mate.dataagent.service;

import vip.mate.dataagent.dataset.*;
import java.util.Map;

public interface DatasetExecutionService {
    DatasetInputDescriptor descriptor(DatasetAccessContext context, long datasetId, String inputName);
    DatasetBatch preview(DatasetAccessContext context, DatasetReadRequest request);
    Map<String,Object> prepareScriptTask(String taskId, DatasetAccessContext context, Map<String,Long> inputs, String script, Map<String,Object> parameters);
}
