package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * Wiki 关联解释
 */
@Data
public class RelationExplanationResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 页面A的slug */
    private String slugA;

    /** 页面B的slug */
    private String slugB;

    /** 总关联分数 */
    private double totalScore;

    /** 各信号分数明细 */
    private List<SignalScoreResp> breakdown;
}
