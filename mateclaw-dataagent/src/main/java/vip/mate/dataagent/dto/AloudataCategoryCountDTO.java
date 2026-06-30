package vip.mate.dataagent.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * Aloudata 类目统计结果
 */
@Data
public class AloudataCategoryCountDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 类目 ID */
    private String categoryId;

    /** 类目名称 */
    private String categoryName;

    /** 父级类目 ID */
    private String parentId;

    /** 该类目下的指标/维度数量 */
    private Long count;
}
