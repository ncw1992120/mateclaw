package vip.mate.client.model.request;

import lombok.Data;

import java.io.Serializable;

/**
 * 工作流草稿生成请求
 */
@Data
public class DraftGenerateReq implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 生成描述 */
    private String description;
}
