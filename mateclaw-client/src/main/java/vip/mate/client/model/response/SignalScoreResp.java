package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 关联信号分数
 */
@Data
public class SignalScoreResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 信号名称 */
    private String signal;

    /** 权重 */
    private double weight;

    /** 得分 */
    private double score;
}
