package vip.mate.dataagent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 仪表盘列表状态计数（当前可见范围内，不受分页影响）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InsightDashboardCountsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 可见范围内全部仪表盘数量 */
    private long all;

    /** 草稿数量 */
    private long draft;

    /** 已发布数量 */
    private long published;
}
