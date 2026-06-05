package vip.mate.client.model.request;

import lombok.Data;

import java.io.Serializable;

/**
 * 晨报已读标记请求
 */
@Data
public class MorningCardSeenReq implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 报告 ID */
    private Long reportId;
}