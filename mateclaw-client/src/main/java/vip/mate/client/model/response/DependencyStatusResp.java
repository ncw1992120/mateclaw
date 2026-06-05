package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 技能依赖状态
 */
@Data
public class DependencyStatusResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 依赖键 */
    private String key;

    /** 依赖类型 */
    private String type;

    /** 依赖描述 */
    private String description;

    /** 是否可选 */
    private boolean optional;

    /** 状态 */
    private String status;

    /** 是否满足 */
    private boolean satisfied;

    /** 安装命令列表 */
    private List<String> installCommands;
}
