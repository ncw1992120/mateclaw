package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 系统初始化状态
 */
@Data
public class SetupStatusResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 应用是否已初始化 */
    private boolean initialized;
}
