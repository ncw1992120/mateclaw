package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 启用/禁用结果
 */
@Data
public class EnableResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private boolean defaultSwitched;
    private String newDefaultProviderId;
    private String newDefaultModel;
}
