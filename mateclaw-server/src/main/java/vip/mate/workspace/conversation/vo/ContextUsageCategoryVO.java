package vip.mate.workspace.conversation.vo;

import lombok.Data;

/**
 * 上下文使用分类视图对象
 * <p>
 * 描述某一分项（如系统提示词、工具定义、历史对话）占用的 token 数量。
 */
@Data
public class ContextUsageCategoryVO {

    /**
     * 分类标识：system_prompt / tool_definitions / conversation
     */
    private String name;

    /**
     * 前端展示标签
     */
    private String label;

    /**
     * 该分类占用的估算 token 数
     */
    private int tokens;

    /**
     * 前端进度条颜色（可选）
     */
    private String color;

    public static ContextUsageCategoryVO of(String name, String label, int tokens, String color) {
        ContextUsageCategoryVO vo = new ContextUsageCategoryVO();
        vo.setName(name);
        vo.setLabel(label);
        vo.setTokens(tokens);
        vo.setColor(color);
        return vo;
    }
}
