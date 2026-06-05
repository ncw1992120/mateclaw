package vip.mate.client.model.request;

import lombok.Data;

import java.io.Serializable;

/**
 * 目标评判标准添加请求
 */
@Data
public class GoalCriterionReq implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 评判标准内容 */
    private String criterion;
}