package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 工作流编译错误项响应
 */
@Data
public class CompileErrorResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 错误码 */
    private String code;

    /** 错误路径 */
    private String path;

    /** 错误信息 */
    private String message;
}
