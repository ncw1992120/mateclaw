package vip.mate.dataagent.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vip.mate.dataagent.service.DataAgentModelService;
import vip.mate.llm.model.ActiveModelsInfo;
import vip.mate.llm.model.ModelConfigEntity;
import vip.mate.llm.model.ModelSlotRequest;
import vip.mate.sdk.service.MateClawRuntime;

/**
 * 模型配置管理服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataAgentModelServiceImpl implements DataAgentModelService {

    private final MateClawRuntime runtime;

    @Override
    public ActiveModelsInfo setActiveModel(Long modelId) {
        ModelConfigEntity model = runtime.getModel(modelId);
        ModelSlotRequest slotRequest = new ModelSlotRequest();
        slotRequest.setProviderId(model.getProvider());
        slotRequest.setModel(model.getModelName());
        return runtime.setActiveModel(slotRequest);
    }

    @Override
    public ModelConfigEntity getDefaultModelSafe() {
        try {
            return runtime.getDefaultModel();
        } catch (Exception e) {
            log.debug("获取默认模型失败: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public ActiveModelsInfo getActiveModelSafe() {
        try {
            return runtime.getActiveModel();
        } catch (Exception e) {
            log.debug("获取激活模型失败: {}", e.getMessage());
            return null;
        }
    }
}
