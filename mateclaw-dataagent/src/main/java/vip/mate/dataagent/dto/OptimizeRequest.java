package vip.mate.dataagent.dto;

import lombok.Data;

/**
 * 输入优化请求
 */
@Data
public class OptimizeRequest {
    /** 待优化的文本内容 */
    private String input;
}
