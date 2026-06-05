package vip.mate.client.model.request;

import lombok.Data;

import java.io.Serializable;

/**
 * 工作流草稿保存请求
 */
@Data
public class WorkflowDraftReq implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 草稿 JSON 内容 */
    private String draftJson;
}
