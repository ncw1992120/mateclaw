package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 会话流状态
 */
@Data
public class ConversationStreamStatusResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 流状态（idle / running） */
    private String streamStatus;
}