package vip.mate.dataagent.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import vip.mate.dataagent.dataset.*;
import vip.mate.dataagent.dataset.demo.StrategyReadoutDemoDatasetFixtures;
import vip.mate.dataagent.service.DatasetExecutionService;
import vip.mate.dataagent.service.DatasetManageService;
import vip.mate.dataagent.service.code.ScriptTaskPreparationService;
import java.util.*;

@Service
public class DatasetExecutionServiceImpl implements DatasetExecutionService {
    private final List<DatasetSourceAdapter> adapters; private final ScriptTaskPreparationService preparation; private final DatasetManageService datasets;
    private StrategyReadoutDemoDatasetFixtures strategyReadoutFixtures;
    public DatasetExecutionServiceImpl(List<DatasetSourceAdapter> adapters, ScriptTaskPreparationService preparation, DatasetManageService datasets){this.adapters=adapters;this.preparation=preparation;this.datasets=datasets;}
    @Autowired(required = false)
    public void setStrategyReadoutFixtures(StrategyReadoutDemoDatasetFixtures fixtures) { this.strategyReadoutFixtures = fixtures; }
    @Override public DatasetInputDescriptor descriptor(DatasetAccessContext context,long id,String inputName){
        DatasetInputDescriptor authorizedDescriptor = datasets.getInputDescriptor(context, id, inputName);
        if (strategyReadoutFixtures != null && strategyReadoutFixtures.supports(id)) {
            return strategyReadoutFixtures.describe(context, id, inputName);
        }
        return adapters.stream().filter(a -> a.supports(authorizedDescriptor.sourceType())).findFirst()
                .orElseThrow(() -> new DatasetReadException(DatasetReadErrorCode.INVALID_REQUEST,"未找到数据集 Adapter"))
                .describe(context,id);
    }
    @Override public DatasetBatch preview(DatasetAccessContext context,DatasetReadRequest request){
        if (strategyReadoutFixtures != null && strategyReadoutFixtures.supports(request.datasetId())) {
            datasets.getInputDescriptor(context, request.datasetId(), request.inputName());
            return strategyReadoutFixtures.read(context, request);
        }
        return adapterFor(context,request.datasetId(),request.inputName()).read(context,request);
    }
    @Override public Map<String,Object> prepareScriptTask(String taskId,DatasetAccessContext context,Map<String,Long> inputs,String script,Map<String,Object> parameters){var p=preparation.prepare(taskId,context.workspaceId(),context.userId(),inputs,script,parameters);return Map.of("taskId",p.taskId(),"script",p.script(),"inputCatalog",p.inputCatalog(),"parameters",p.parameters(),"readToken",p.readToken());}
    private DatasetSourceAdapter adapterFor(DatasetAccessContext context,long id,String inputName){if(context==null||!context.canRead(id))throw new DatasetReadException(DatasetReadErrorCode.ACCESS_DENIED,"无权读取数据集"); DatasetInputDescriptor descriptor=datasets.getInputDescriptor(context,id,inputName); return adapters.stream().filter(a->a.supports(descriptor.sourceType())).findFirst().orElseThrow(()->new DatasetReadException(DatasetReadErrorCode.INVALID_REQUEST,"未找到数据集 Adapter"));}
}
