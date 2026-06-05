package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 数据源连接测试结果
 */
@Data
public class DatasourceTestResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 连接是否成功 */
    private boolean success;

    /** 消息 */
    private String message;
}
