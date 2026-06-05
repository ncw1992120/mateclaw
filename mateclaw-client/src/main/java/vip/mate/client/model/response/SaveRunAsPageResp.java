package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 转换运行保存为页面结果
 */
@Data
public class SaveRunAsPageResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 页面ID */
    private Long pageId;

    /** 页面slug */
    private String slug;

    /** 页面标题 */
    private String title;
}
