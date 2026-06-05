package vip.mate.client.model;

import lombok.Data;

import java.io.Serializable;

/**
 * 投递配置
 */
@Data
public class DeliveryConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    private String targetId;
    private String threadId;
    private String accountId;
    private String userId;
    private Boolean suppressAgentReply;
}
