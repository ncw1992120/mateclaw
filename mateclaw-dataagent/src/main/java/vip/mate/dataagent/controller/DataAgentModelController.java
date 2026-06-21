package vip.mate.dataagent.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import vip.mate.common.result.R;
import vip.mate.dataagent.dto.ModelActiveRequest;
import vip.mate.dataagent.service.DataAgentModelService;
import vip.mate.llm.model.*;
import vip.mate.sdk.service.MateClawRuntime;

import java.util.List;
import java.util.Map;

/**
 * 模型配置代理控制器
 * <p>
 * 通过 MateClawRuntime 提供模型管理 API，
 * 包括 Provider 管理、模型 CRUD、模型发现与测试等。
 */
@RestController
@RequestMapping("/v1/models")
@RequiredArgsConstructor
@Tag(name = "模型配置管理", description = "模型配置与供应商管理接口")
public class DataAgentModelController {

    private final MateClawRuntime runtime;
    private final DataAgentModelService modelService;

    /**
     * 获取 Provider 列表（仅 enabled）
     */
    @GetMapping
    @Operation(summary = "Provider 列表", description = "获取已启用的供应商列表")
    public R<List<ProviderInfoDTO>> listProviders() {
        return R.ok(runtime.listProviders());
    }

    /**
     * 获取 Provider 全量目录（含未启用）
     */
    @GetMapping("/catalog")
    @Operation(summary = "Provider 目录", description = "获取全量供应商目录（含未启用）")
    public R<List<ProviderInfoDTO>> listCatalog() {
        return R.ok(runtime.listProviderCatalog());
    }

    /**
     * 启用 Provider
     */
    @PostMapping("/{providerId}/enable")
    @Operation(summary = "启用 Provider", description = "启用指定供应商")
    public R<EnableResult> enableProvider(@PathVariable String providerId) {
        return R.ok(runtime.enableProvider(providerId));
    }

    /**
     * 禁用 Provider
     */
    @PostMapping("/{providerId}/disable")
    @Operation(summary = "禁用 Provider", description = "禁用指定供应商")
    public R<EnableResult> disableProvider(@PathVariable String providerId) {
        return R.ok(runtime.disableProvider(providerId));
    }

    /**
     * 更新 Provider 配置
     */
    @PutMapping("/{providerId}/config")
    @Operation(summary = "更新 Provider 配置", description = "更新供应商的 API Key、Base URL 等配置")
    public R<ProviderInfoDTO> updateProviderConfig(@PathVariable String providerId,
                                                   @RequestBody ProviderConfigRequest request) {
        return R.ok(runtime.updateProviderConfig(providerId, request));
    }

    /**
     * 创建自定义 Provider
     */
    @PostMapping("/custom-providers")
    @Operation(summary = "创建自定义 Provider", description = "添加自定义模型供应商")
    public R<ProviderInfoDTO> createCustomProvider(@RequestBody CreateCustomProviderRequest request) {
        return R.ok(runtime.createCustomProvider(request));
    }

    /**
     * 删除自定义 Provider
     */
    @DeleteMapping("/custom-providers/{providerId}")
    @Operation(summary = "删除自定义 Provider", description = "删除指定自定义供应商")
    public R<Void> deleteCustomProvider(@PathVariable String providerId) {
        runtime.deleteCustomProvider(providerId);
        return R.ok();
    }

    /**
     * 向 Provider 添加模型
     */
    @PostMapping("/{providerId}/models")
    @Operation(summary = "添加模型", description = "向指定供应商添加模型")
    public R<ProviderInfoDTO> addProviderModel(@PathVariable String providerId,
                                               @RequestBody AddProviderModelRequest request) {
        return R.ok(runtime.addModelToProvider(providerId, request));
    }

    /**
     * 从 Provider 删除模型
     */
    @DeleteMapping("/{providerId}/models/{modelId}")
    @Operation(summary = "删除模型", description = "从指定供应商删除模型")
    public R<ProviderInfoDTO> removeProviderModel(@PathVariable String providerId,
                                                  @PathVariable String modelId) {
        return R.ok(runtime.removeModelFromProvider(providerId, modelId));
    }

    /**
     * 获取启用模型列表
     */
    @GetMapping("/enabled")
    @Operation(summary = "启用模型列表", description = "获取所有已启用的模型配置")
    public R<List<ModelConfigEntity>> listEnabledModels() {
        return R.ok(runtime.listEnabledModels());
    }

    /**
     * 获取所有已启用的模型（含 chat 和 embedding 类型）
     */
    @GetMapping("/all-enabled")
    @Operation(summary = "全部启用模型", description = "获取所有已启用的模型配置，包含对话和向量类型")
    public R<List<ModelConfigEntity>> listAllEnabledModels() {
        return R.ok(runtime.listAllEnabledModels());
    }

    /**
     * 获取所有模型（含启用和禁用）
     */
    @GetMapping("/all")
    @Operation(summary = "全部模型", description = "获取所有模型配置，包含启用和禁用状态")
    public R<List<ModelConfigEntity>> listAllModels() {
        return R.ok(runtime.listAllModels());
    }

    /**
     * 获取默认模型
     */
    @GetMapping("/default")
    @Operation(summary = "默认模型", description = "获取当前默认模型配置")
    public R<ModelConfigEntity> getDefaultModel() {
        return R.ok(modelService.getDefaultModelSafe());
    }

    /**
     * 获取当前激活模型
     */
    @GetMapping("/active")
    @Operation(summary = "激活模型", description = "获取当前激活的模型信息")
    public R<ActiveModelsInfo> getActiveModel() {
        return R.ok(modelService.getActiveModelSafe());
    }

    /**
     * 设置当前激活模型
     */
    @PutMapping("/active")
    @Operation(summary = "设置激活模型", description = "通过模型 ID 设置当前激活模型")
    public R<ActiveModelsInfo> setActiveModel(@RequestBody ModelActiveRequest request) {
        return R.ok(modelService.setActiveModel(request.getModelId()));
    }

    /**
     * 获取模型详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "模型详情", description = "根据 ID 获取模型配置详情")
    public R<ModelConfigEntity> getModel(@PathVariable Long id) {
        return R.ok(runtime.getModel(id));
    }

    /**
     * 创建模型
     */
    @PostMapping
    @Operation(summary = "创建模型", description = "新增模型配置")
    public R<ModelConfigEntity> createModel(@RequestBody ModelConfigEntity entity) {
        return R.ok(runtime.createModel(entity));
    }

    /**
     * 更新模型
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新模型", description = "更新模型配置")
    public R<ModelConfigEntity> updateModel(@PathVariable Long id, @RequestBody ModelConfigEntity entity) {
        entity.setId(id);
        return R.ok(runtime.updateModel(entity));
    }

    /**
     * 删除模型
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除模型", description = "删除指定模型配置")
    public R<Void> deleteModel(@PathVariable Long id) {
        runtime.deleteModel(id);
        return R.ok();
    }

    /**
     * 设置默认模型
     */
    @PostMapping("/{id}/default")
    @Operation(summary = "设置默认模型", description = "将指定模型设为默认模型")
    public R<ModelConfigEntity> setDefaultModel(@PathVariable Long id) {
        return R.ok(runtime.setDefaultModel(id));
    }

    /**
     * 发现远端模型
     */
    @PostMapping("/{providerId}/discover")
    @Operation(summary = "发现模型", description = "发现指定供应商的远端可用模型")
    public R<DiscoverResult> discoverModels(@PathVariable String providerId) {
        return R.ok(runtime.discoverModels(providerId));
    }

    /**
     * 批量添加发现的模型
     */
    @PostMapping("/{providerId}/discover/apply")
    @Operation(summary = "应用发现模型", description = "批量添加发现的模型到供应商")
    public R<Map<String, Integer>> applyDiscoveredModels(@PathVariable String providerId,
                                                          @RequestBody Map<String, List<String>> body) {
        return R.ok(runtime.applyDiscoveredModels(providerId, body.get("modelNames")));
    }

    /**
     * 测试供应商连接
     */
    @PostMapping("/{providerId}/test-connection")
    @Operation(summary = "测试连接", description = "测试指定供应商的连接是否可用")
    public R<TestResult> testProviderConnection(@PathVariable String providerId) {
        return R.ok(runtime.testProviderConnection(providerId));
    }

    /**
     * 测试单个模型可用性
     */
    @PostMapping("/{providerId}/models/{modelId}/test")
    @Operation(summary = "测试模型", description = "测试指定模型的可用性")
    public R<TestResult> testModel(@PathVariable String providerId, @PathVariable String modelId) {
        return R.ok(runtime.testModel(providerId, modelId));
    }

    /**
     * 测试 Embedding 模型连通性
     */
    @PostMapping("/embedding/{modelId}/test")
    @Operation(summary = "测试 Embedding 模型", description = "测试指定 Embedding 模型的连通性（嵌入短文本验证 API key）")
    public R<Map<String, Object>> testEmbeddingModel(@PathVariable Long modelId) {
        return R.ok(runtime.testEmbeddingModel(modelId));
    }

    /**
     * 按类型筛选模型
     */
    @GetMapping("/by-type")
    @Operation(summary = "按类型筛选模型", description = "按模型类型（chat/embedding）筛选，可选模态过滤")
    public R<List<ModelConfigEntity>> listModelsByType(
            @RequestParam(defaultValue = "chat") String modelType,
            @RequestParam(required = false) String modality) {
        return R.ok(runtime.listModelsByType(modelType, modality));
    }

    /**
     * 获取默认向量模型
     */
    @GetMapping("/default-embedding")
    @Operation(summary = "获取默认向量模型", description = "获取当前配置的默认向量（embedding）模型")
    public R<ModelConfigEntity> getDefaultEmbeddingModel() {
        return R.ok(runtime.getDefaultEmbeddingModel());
    }

    /**
     * 设置默认向量模型
     */
    @PostMapping("/{id}/default-embedding")
    @Operation(summary = "设置默认向量模型", description = "将指定模型设为默认向量（embedding）模型")
    public R<ModelConfigEntity> setDefaultEmbeddingModel(@PathVariable Long id) {
        return R.ok(runtime.setDefaultEmbeddingModel(id));
    }
}