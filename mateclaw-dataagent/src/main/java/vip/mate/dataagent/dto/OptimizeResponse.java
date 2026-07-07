package vip.mate.dataagent.dto;

import lombok.Data;

/**
 * 输入优化响应
 */
@Data
public class OptimizeResponse {
    /** 优化后的文本内容 */
    private String optimized;
}
