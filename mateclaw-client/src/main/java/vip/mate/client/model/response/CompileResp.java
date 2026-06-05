package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 工作流编译结果
 */
@Data
public class CompileResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 错误总数 */
    private int errorCount;

    /** 错误列表 */
    private List<CompileErrorResp> errors;
}
