package vip.mate.dataagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vip.mate.dataagent.auth.service.WorkspaceGuard;
import vip.mate.dataagent.model.DatasetEntity;
import vip.mate.dataagent.repository.DatasetMapper;

import java.util.Objects;

/** 执行前按组件归属解析当前输入，Runner 只读取本次任务的 prepared input。 */
@Service
@RequiredArgsConstructor
public class ComponentDatasetResolver {
    private final DatasetMapper datasets;
    private final WorkspaceGuard workspaceGuard;

    public void resolve(long dashboardId, String componentId, JsonNode inputs) {
        if (componentId == null || componentId.isBlank()) return; // 兼容旧版根级脚本
        Long workspaceId = workspaceGuard.currentWorkspaceId();
        for (JsonNode input : inputs) {
            String alias = input.path("inputName").asText();
            DatasetEntity scoped = datasets.selectOne(new LambdaQueryWrapper<DatasetEntity>()
                    .eq(DatasetEntity::getWorkspaceId, workspaceId)
                    .eq(DatasetEntity::getDashboardId, dashboardId)
                    .eq(DatasetEntity::getComponentId, componentId)
                    .eq(DatasetEntity::getName, alias)
                    .eq(DatasetEntity::getDeleted, 0));
            if (scoped != null) {
                ((ObjectNode) input).put("datasetId", String.valueOf(scoped.getId()));
                continue;
            }
            // 存量公共数据集可通过显式 ID 引用；有组件归属的记录不能跨组件或按旧别名兜底。
            DatasetEntity legacy = datasets.selectById(input.path("datasetId").asLong());
            if (legacy != null && !Objects.equals(legacy.getDeleted(), 1)
                    && Objects.equals(legacy.getWorkspaceId(), workspaceId)
                    && legacy.getDashboardId() == null && legacy.getComponentId() == null) continue;
            throw new IllegalArgumentException("当前组件未找到数据集别名「" + alias
                    + "」，请重新保存该组件的数据集配置后查询");
        }
    }
}
