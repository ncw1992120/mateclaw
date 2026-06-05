package vip.mate.client.model.request;

import lombok.Data;

import java.io.Serializable;

/**
 * 事实反馈请求
 */
@Data
public class FactFeedbackReq implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 反馈类型（HELPFUL / UNHELPFUL） */
    private String kind;
}