package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 技能导出到工作区结果
 */
@Data
public class SkillExportResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 是否成功 */
    private boolean success;

    /** 导出路径 */
    private String path;

    /** 失败消息 */
    private String message;
}
