package vip.mate.llm.model;

import lombok.Data;

import java.util.List;

@Data
public class CreateCustomProviderRequest {
    private String id;
    private String name;
    private String defaultBaseUrl;
    private String apiKeyPrefix;
    /** 创建时的初始 API Key（此前弹窗收集但被丢弃，创建后必须再编辑一次才能补上） */
    private String apiKey;
    private String protocol;
    private String chatModel;
    private Boolean requireApiKey;
    private List<ModelInfoDTO> models;
}
