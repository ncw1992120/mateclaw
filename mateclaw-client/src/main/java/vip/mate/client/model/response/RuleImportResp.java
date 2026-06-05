package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 安全规则导入结果
 */
@Data
public class RuleImportResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 新增数量 */
    private int inserted;

    /** 更新内置规则数量 */
    private int updatedBuiltin;

    /** 更新自定义规则数量 */
    private int updatedCustom;

    /** 跳过数量 */
    private int skipped;

    /** 错误信息列表 */
    private List<String> errors;
}
