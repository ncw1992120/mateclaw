package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 页面归档操作结果
 */
@Data
public class PageArchiveResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 页面slug */
    private String slug;

    /** 是否已归档 */
    private boolean archived;

    /** 是否有变更 */
    private boolean changed;
}
