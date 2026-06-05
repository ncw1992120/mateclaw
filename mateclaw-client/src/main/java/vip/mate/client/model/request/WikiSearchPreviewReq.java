package vip.mate.client.model.request;

import lombok.Data;

import java.io.Serializable;

/**
 * Wiki 搜索预览请求
 */
@Data
public class WikiSearchPreviewReq implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 查询关键词 */
    private String query = "";

    /** 搜索模式（默认 "hybrid"） */
    private String mode = "hybrid";

    /** 返回结果数量（默认 5） */
    private int topK = 5;
}