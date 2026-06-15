package vip.mate.dataagent.service;

import vip.mate.llm.model.ActiveModelsInfo;
import vip.mate.llm.model.ModelConfigEntity;

/**
 * 模型配置管理服务接口
 */
public interface DataAgentModelService {

    /**
     * 设置激活模型
     *
     * @param modelId 模型配置 ID
     * @return 激活模型信息
     */
    ActiveModelsInfo setActiveModel(Long modelId);

    /**
     * 获取默认模型（异常时返回 null）
     *
     * @return 默认模型配置，不存在或异常时返回 null
     */
    ModelConfigEntity getDefaultModelSafe();

    /**
     * 获取当前激活模型（异常时返回 null）
     *
     * @return 激活模型信息，不存在或异常时返回 null
     */
    ActiveModelsInfo getActiveModelSafe();
}
