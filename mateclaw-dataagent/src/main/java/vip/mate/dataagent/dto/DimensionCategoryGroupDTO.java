package vip.mate.dataagent.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 维度按类目分组 DTO
 * <p>
 * 将维度按类目归类，前端可直接按分组渲染，无需自行分组。
 */
@Data
public class DimensionCategoryGroupDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 类目 ID */
    private String categoryId;

    /** 类目名称 */
    private String categoryName;

    /** 该类目下的维度数量 */
    private int dimensionCount;

    /** 该类目下的维度列表 */
    private List<AloudataDimensionSemanticDTO> dimensions;
}
