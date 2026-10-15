package vip.mate.dataagent.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 洞察仪表盘列表分页响应。
 * <p>
 * {@code counts} 是当前可见范围（工作区 + 可见性 + 关键词）内的状态总数，
 * 与分页无关：状态 Tab 与范围 Tab 的计数不随翻页变化。
 */
@Data
public class InsightDashboardPageVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前页记录 */
    private List<InsightDashboardSummaryVO> records;

    /** 满足条件的总数（非当前页条数） */
    private long total;

    /** 当前页码（从 1 开始） */
    private int page;

    /** 每页条数 */
    private int size;

    /** 可见范围内的状态计数：all / draft / published */
    private InsightDashboardCountsVO counts;
}
