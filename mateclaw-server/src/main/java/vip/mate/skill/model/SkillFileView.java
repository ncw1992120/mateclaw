package vip.mate.skill.model;

import java.time.LocalDateTime;

/**
 * 技能 bundle 文件视图
 * <p>
 * 供文件列表/内容查看/内容更新接口返回。刻意不含 id / skillId：
 * 雪花 ID 超出前端 JS Number 安全整数范围，且前端长整型转换规则未覆盖
 * skillId，视图层直接省略以规避精度丢失。
 */
public record SkillFileView(String filePath, String content, Integer contentSize,
                            String sha256, LocalDateTime updateTime) {

    /**
     * 由文件实体构造视图
     *
     * @param entity         文件实体
     * @param includeContent 是否携带正文（列表场景传 false 以减小响应体）
     * @return 文件视图，实体为 null 时返回 null
     */
    public static SkillFileView from(SkillFileEntity entity, boolean includeContent) {
        if (entity == null) {
            return null;
        }
        return new SkillFileView(
                entity.getFilePath(),
                includeContent ? entity.getContent() : null,
                entity.getContentSize(),
                entity.getSha256(),
                entity.getUpdateTime());
    }
}
