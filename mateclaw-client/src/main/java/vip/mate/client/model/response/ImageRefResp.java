package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 页面图片引用
 */
@Data
public class ImageRefResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 原始markdown图片语法 */
    private String fullMatch;

    /** alt文本 */
    private String alt;

    /** 图片URL */
    private String url;
}
