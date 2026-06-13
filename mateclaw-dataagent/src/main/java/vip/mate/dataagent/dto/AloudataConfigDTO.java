package vip.mate.dataagent.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * Aloudata 数据源配置信息
 */
@Data
public class AloudataConfigDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** anymetrics 服务地址（产品层，端口默认 8083） */
    private String anymetricsHost;

    /** anymetrics 服务端口 */
    private Integer anymetricsPort;

    /** semantic 服务地址（语义层，端口默认 8085） */
    private String semanticHost;

    /** semantic 服务端口 */
    private Integer semanticPort;

    /** 租户ID */
    private String tenantId;

    /** 认证方式：UID（基于用户ID认证）/ ACCESS_KEY（Access Key 认证） */
    private String authType;

    /** 认证值：根据认证方式，可为用户ID或Secret Key */
    private String authValue;

    /**
     * 数据源级别的 API 端点路径覆盖
     * <p>
     * key 为端点名称（如 metrics_list、metrics_query），value 为覆盖的路径。
     * 优先级高于全局配置（aloudata.api.endpoints），适用于同一平台不同版本/不同部署的场景。
     * <p>
     * 示例：{"metrics_list": "/anymetrics/api/v2/metrics/list"}
     */
    private Map<String, String> apiOverrides;
}
