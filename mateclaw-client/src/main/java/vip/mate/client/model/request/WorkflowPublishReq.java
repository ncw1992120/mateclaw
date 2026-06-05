package vip.mate.client.model.request;

import lombok.Data;

import java.io.Serializable;

/**
 * 工作流发布请求
 */
@Data
public class WorkflowPublishReq implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 发布备注 */
    private String note;
}
