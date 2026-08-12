package vip.mate.llm.rerank;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Rerank 请求体
 * <p>
 * 对一个查询与一组候选文档做交叉编码相关性重排。
 * <p>
 * topN 为可选参数：null 或小于 1 时由模型实现决定返回全部（或供应商默认值）。
 *
 * @author MateClaw Team
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RerankRequest {

    /** 检索查询 */
    private String query;

    /** 候选文档列表（按原始顺序） */
    private List<String> documents;

    /** 返回 TopN 条重排结果，null/&lt;1 表示全部返回 */
    private Integer topN;
}
