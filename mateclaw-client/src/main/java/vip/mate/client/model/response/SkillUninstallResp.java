package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 技能卸载结果
 */
@Data
public class SkillUninstallResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 结果消息 */
    private String message;
}