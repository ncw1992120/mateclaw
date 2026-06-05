package vip.mate.client.model.request;

import lombok.Data;

import java.io.Serializable;

/**
 * 系统初始化请求
 */
@Data
public class SetupInitReq implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 系统语言 */
    private String language;
}
