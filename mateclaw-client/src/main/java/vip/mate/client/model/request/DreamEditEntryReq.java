package vip.mate.client.model.request;

import lombok.Data;

import java.io.Serializable;

/**
 * 晨报条目编辑请求
 */
@Data
public class DreamEditEntryReq implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 条目内容 */
    private String content;

    /** 目标文件名（仅当 reportId=0 时使用，默认 "MEMORY.md"） */
    private String filename;
}