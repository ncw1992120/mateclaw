package vip.mate.dataagent.dto;

import lombok.Data;

/**
 * 帮助文档反馈视图对象
 */
@Data
public class HelpFeedbackVO {

    private String id;

    private String documentId;

    private Integer rating;

    private String suggestion;

    private String userId;

    private String createTime;

    private String updateTime;
}
