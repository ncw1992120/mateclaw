package vip.mate.client.model.request;

import lombok.Data;

import java.io.Serializable;

/**
 * 添加 Provider 模型请求
 */
@Data
public class AddProviderModelReq implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 模型 ID */
    private String id;

    /** 模型名称 */
    private String name;
}
