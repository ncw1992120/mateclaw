package vip.mate.client.model.request;

import lombok.Data;

import java.io.Serializable;

/**
 * Wiki 页面更新请求
 */
@Data
public class WikiPageUpdateReq implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 页面内容 */
    private String content;

    /** 页面摘要 */
    private String summary;
}