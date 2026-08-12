package vip.mate.llm.rerank;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Rerank 单条结果
 * <p>
 * index 对应 {@link RerankRequest#getDocuments()} 中的原始下标，
 * relevanceScore 为模型返回的相关性分数（0~1，越高越相关）。
 *
 * @author MateClaw Team
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RerankResult {

    /** 原始文档下标（对应请求 documents 顺序） */
    private int index;

    /** 相关性分数（0~1） */
    private double relevanceScore;

    /** 原始文档内容（仅当请求方需要回显时使用，可空） */
    private String document;
}
