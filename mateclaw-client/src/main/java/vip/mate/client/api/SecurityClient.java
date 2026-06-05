package vip.mate.client.api;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestTemplate;
import vip.mate.client.constant.ApiPathConstants;
import vip.mate.client.model.PageData;
import vip.mate.client.model.R;
import vip.mate.client.model.request.GuardConfigReq;
import vip.mate.client.model.response.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 安全客户端
 * <p>
 * 对应服务端 /api/v1/security 接口，提供安全防护配置、规则管理、审计日志等功能
 */
public class SecurityClient extends AbstractApiClient {

    public SecurityClient(String baseUrl, RestTemplate restTemplate) {
        super(baseUrl, restTemplate);
    }

    // ==================== 防护配置 ====================

    /**
     * 获取防护配置
     *
     * @return 防护配置
     */
    public R<GuardConfigReq> getGuardConfig() {
        return get(ApiPathConstants.SECURITY_GUARD_CONFIG,
                new ParameterizedTypeReference<R<GuardConfigReq>>() {});
    }

    /**
     * 更新防护配置
     *
     * @param config 配置内容
     * @return 更新后的配置
     */
    public R<GuardConfigReq> updateGuardConfig(GuardConfigReq config) {
        return put(ApiPathConstants.SECURITY_GUARD_CONFIG, config,
                new ParameterizedTypeReference<R<GuardConfigReq>>() {});
    }

    /**
     * 获取文件防护配置
     *
     * @return 文件防护配置
     */
    public R<FileGuardConfigResp> getFileGuardConfig() {
        return get(ApiPathConstants.SECURITY_FILE_GUARD_CONFIG,
                new ParameterizedTypeReference<R<FileGuardConfigResp>>() {});
    }

    /**
     * 更新文件防护配置
     *
     * @param config 配置内容
     * @return 更新后的配置
     */
    public R<FileGuardConfigResp> updateFileGuardConfig(FileGuardConfigResp config) {
        return put(ApiPathConstants.SECURITY_FILE_GUARD_CONFIG, config,
                new ParameterizedTypeReference<R<FileGuardConfigResp>>() {});
    }

    // ==================== 规则管理 ====================

    /**
     * 获取规则列表
     *
     * @param page     页码
     * @param size     每页数量
     * @param builtin  是否内置
     * @param enabled  是否启用
     * @param category 分类
     * @param severity 严重程度
     * @return 规则分页列表
     */
    public R<PageData<GuardRuleResp>> listRules(Integer page, Integer size, Boolean builtin,
                                            Boolean enabled, String category, String severity) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (page != null) {
            params.put("page", page);
        }
        if (size != null) {
            params.put("size", size);
        }
        if (builtin != null) {
            params.put("builtin", builtin);
        }
        if (enabled != null) {
            params.put("enabled", enabled);
        }
        if (category != null) {
            params.put("category", category);
        }
        if (severity != null) {
            params.put("severity", severity);
        }
        return get(ApiPathConstants.SECURITY_GUARD_RULES, params,
                new ParameterizedTypeReference<R<PageData<GuardRuleResp>>>() {});
    }

    /**
     * 获取内置规则列表
     *
     * @param page 页码
     * @param size 每页数量
     * @return 内置规则分页列表
     */
    public R<PageData<GuardRuleResp>> listBuiltinRules(Integer page, Integer size) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (page != null) {
            params.put("page", page);
        }
        if (size != null) {
            params.put("size", size);
        }
        return get(ApiPathConstants.SECURITY_GUARD_RULES_BUILTIN, params,
                new ParameterizedTypeReference<R<PageData<GuardRuleResp>>>() {});
    }

    /**
     * 创建规则
     *
     * @param rule 规则信息
     * @return 创建的规则
     */
    public R<GuardRuleResp> createRule(GuardRuleResp rule) {
        return post(ApiPathConstants.SECURITY_GUARD_RULES, rule,
                new ParameterizedTypeReference<R<GuardRuleResp>>() {});
    }

    /**
     * 更新规则
     *
     * @param ruleId 规则 ID
     * @param rule   规则更新信息
     * @return 更新后的规则
     */
    public R<GuardRuleResp> updateRule(String ruleId, GuardRuleResp rule) {
        return put(resolvePath(ApiPathConstants.SECURITY_GUARD_RULE_BY_ID, ruleId), rule,
                new ParameterizedTypeReference<R<GuardRuleResp>>() {});
    }

    /**
     * 切换规则状态
     *
     * @param ruleId  规则 ID
     * @param enabled 是否启用
     * @return 操作结果
     */
    public R<String> toggleRule(String ruleId, Boolean enabled) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("enabled", enabled);
        return put(buildUrl(resolvePath(ApiPathConstants.SECURITY_GUARD_RULE_TOGGLE, ruleId), params), null,
                new ParameterizedTypeReference<R<String>>() {});
    }

    /**
     * 删除规则
     *
     * @param ruleId 规则 ID
     * @return 操作结果
     */
    public R<String> deleteRule(String ruleId) {
        return delete(resolvePath(ApiPathConstants.SECURITY_GUARD_RULE_BY_ID, ruleId),
                new ParameterizedTypeReference<R<String>>() {});
    }

    /**
     * 按主键删除规则
     *
     * @param id 规则主键
     * @return 操作结果
     */
    public R<String> deleteRuleByPk(Long id) {
        return delete(resolvePath(ApiPathConstants.SECURITY_GUARD_RULE_BY_PK, id),
                new ParameterizedTypeReference<R<String>>() {});
    }

    /**
     * 导出规则
     *
     * @return 导出内容
     */
    public R<RuleExportDataResp> exportRules() {
        return get(ApiPathConstants.SECURITY_GUARD_RULES_EXPORT,
                new ParameterizedTypeReference<R<RuleExportDataResp>>() {});
    }

    /**
     * 导入规则
     *
     * @param body 导入内容
     * @return 导入结果
     */
    public R<RuleImportResp> importRules(RuleExportDataResp body) {
        return post(ApiPathConstants.SECURITY_GUARD_RULES_IMPORT, body,
                new ParameterizedTypeReference<R<RuleImportResp>>() {});
    }

    // ==================== 审计日志 ====================

    /**
     * 获取审计日志列表
     *
     * @param page           页码
     * @param size           每页数量
     * @param toolName       工具名称
     * @param decision       决策
     * @param conversationId 会话 ID
     * @return 审计日志分页列表
     */
    public R<PageData<AuditLogResp>> listAuditLogs(Integer page, Integer size, String toolName,
                                                String decision, String conversationId) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (page != null) {
            params.put("page", page);
        }
        if (size != null) {
            params.put("size", size);
        }
        if (toolName != null) {
            params.put("toolName", toolName);
        }
        if (decision != null) {
            params.put("decision", decision);
        }
        if (conversationId != null) {
            params.put("conversationId", conversationId);
        }
        return get(ApiPathConstants.SECURITY_AUDIT_LOGS, params,
                new ParameterizedTypeReference<R<PageData<AuditLogResp>>>() {});
    }

    /**
     * 获取审计统计
     *
     * @return 审计统计信息
     */
    public R<AuditStatsResp> getAuditStats() {
        return get(ApiPathConstants.SECURITY_AUDIT_STATS,
                new ParameterizedTypeReference<R<AuditStatsResp>>() {});
    }

    /**
     * 获取审批列表
     *
     * @param conversationId 会话 ID
     * @param limit          限制数量
     * @return 审批列表
     */
    public R<Object> listApprovals(String conversationId, Integer limit) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (conversationId != null) {
            params.put("conversationId", conversationId);
        }
        if (limit != null) {
            params.put("limit", limit);
        }
        return get(ApiPathConstants.SECURITY_APPROVALS, params,
                new ParameterizedTypeReference<R<Object>>() {});
    }
}
