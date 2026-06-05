package vip.mate.client.model.request;

import lombok.Data;

import java.io.Serializable;

/**
 * Wiki 知识库配置
 */
@Data
public class WikiConfigReq implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 配置内容 */
    private String content;
}