package vip.mate.dataagent.dto;

import lombok.Data;

/**
 * 帮助文档反馈请求对象
 */
@Data
public class HelpFeedbackRequest {

    /** 评分（1-5） */
    private Integer rating;

    /** 改进建议 */
    private String suggestion;

    /** 用户标识 */
    private String userId;
}
