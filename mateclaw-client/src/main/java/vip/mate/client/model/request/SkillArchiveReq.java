package vip.mate.client.model.request;

import lombok.Data;

import java.io.Serializable;

/**
 * 技能归档请求
 */
@Data
public class SkillArchiveReq implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 归档原因 */
    private String reason;
}
