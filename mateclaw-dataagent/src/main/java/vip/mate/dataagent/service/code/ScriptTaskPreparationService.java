package vip.mate.dataagent.service.code;

import org.springframework.stereotype.Service;
import vip.mate.dataagent.dataset.*;
import vip.mate.dataagent.service.DatasetManageService;

import java.util.*;

/** 在提交 Runner 前固化输入别名、权限上下文和短期读取令牌。 */
@Service
public class ScriptTaskPreparationService {
    private final DatasetManageService datasets; private final ScriptTaskInputRegistry registry; private final ScriptDatasetReadTokenService tokens;
    public ScriptTaskPreparationService(DatasetManageService datasets, ScriptTaskInputRegistry registry, ScriptDatasetReadTokenService tokens) { this.datasets=datasets; this.registry=registry; this.tokens=tokens; }
    public PreparedTask prepare(String taskId, Long workspaceId, Long userId, Map<String,Long> inputDatasets, String script, Map<String,Object> parameters) {
        if (inputDatasets==null||inputDatasets.isEmpty()||script==null||script.isBlank()) throw new IllegalArgumentException("task inputs and script are required");
        DatasetAccessContext context=new DatasetAccessContext(workspaceId,userId,taskId,new HashSet<>(inputDatasets.values()));
        Map<String,DatasetInputDescriptor> descriptors=new LinkedHashMap<>(); inputDatasets.forEach((alias,id)->descriptors.put(alias,datasets.getInputDescriptor(context,id,alias))); registry.register(taskId,descriptors);
        String token=tokens.issue(taskId,workspaceId,900); return new PreparedTask(taskId,script,descriptors,parameters==null?Map.of():Map.copyOf(parameters),token);
    }
    public record PreparedTask(String taskId,String script,Map<String,DatasetInputDescriptor> inputCatalog,Map<String,Object> parameters,String readToken) {}
}
