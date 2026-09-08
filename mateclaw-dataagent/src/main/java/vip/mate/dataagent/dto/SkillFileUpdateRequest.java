package vip.mate.dataagent.dto;

import lombok.Data;

/**
 * 技能 bundle 文件内容更新请求
 */
@Data
public class SkillFileUpdateRequest {

    /** 新的 UTF-8 文本内容 */
    private String content;
}
