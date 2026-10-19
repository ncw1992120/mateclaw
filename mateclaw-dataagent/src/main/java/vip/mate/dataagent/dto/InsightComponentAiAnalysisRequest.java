package vip.mate.dataagent.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/** 当前仪表盘预览状态下的 AI 分析请求。 */
@Data
public class InsightComponentAiAnalysisRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 组件配置的分析方向，可为空。 */
    @Size(max = 4000, message = "分析提示词不能超过 4000 个字符")
    private String promptTemplate;

    /** 本轮分析的唯一标识；旧客户端可不传，新客户端用其取消过期任务。 */
    @Pattern(regexp = "[a-zA-Z0-9-]{1,64}", message = "分析请求标识格式无效")
    private String requestId;

    /** 前端当前页面的已渲染数据快照，不触发后端重新查询。 */
    @NotNull(message = "分析数据快照不能为空")
    private Map<String, Object> snapshot;
}
