package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 测试结果
 */
@Data
public class TestResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private boolean success;
    private long latencyMs;
    private String message;
    private String errorMessage;
}
