package vip.mate.dataagent.dto;

import lombok.Data;

/**
 * 帮助文档反馈汇总视图对象
 */
@Data
public class HelpFeedbackSummaryVO {

    /** 文档 ID */
    private String documentId;

    /** 平均评分 */
    private Double averageRating;

    /** 总反馈数 */
    private Integer totalFeedbacks;

    /** 5星数量 */
    private Integer star5Count;

    /** 4星数量 */
    private Integer star4Count;

    /** 3星数量 */
    private Integer star3Count;

    /** 2星数量 */
    private Integer star2Count;

    /** 1星数量 */
    private Integer star1Count;
}
