package vip.mate.client.model.request;

import lombok.Data;
import vip.mate.client.model.response.ModelInfoResp;

import java.io.Serializable;
import java.util.List;

/**
 * 创建自定义 Provider 请求
 */
@Data
public class CustomProviderReq implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 自定义 Provider ID */
    private String id;

    /** Provider 名称 */
    private String name;

    /** 默认基础 URL */
    private String defaultBaseUrl;

    /** API Key 前缀 */
    private String apiKeyPrefix;

    /** 协议 */
    private String protocol;

    /** 默认聊天模型 */
    private String chatModel;

    /** 是否需要 API Key */
    private Boolean requireApiKey;

    /** 模型列表 */
    private List<ModelInfoResp> models;
}
