package vip.mate.dataagent.service;

import vip.mate.dataagent.dto.AloudataAnalysisViewDetail;
import vip.mate.dataagent.dto.AloudataAnalysisViewItem;
import vip.mate.dataagent.dto.AloudataAnalysisViewSummary;

import java.util.List;

public interface AloudataAnalysisViewService {
    List<AloudataAnalysisViewSummary> listTree(Long datasourceId);

    /**
     * 平铺查询指标视图列表（支持关键字搜索与「只看我的」）。
     *
     * @param datasourceId 数据源 ID
     * @param keyword      视图名称关键字，空则查全量
     * @param onlyMine     仅返回归属当前认证账号（owner）的视图
     * @return 平铺列表，元素带 owner/mine 归属信息
     */
    List<AloudataAnalysisViewItem> listViews(Long datasourceId, String keyword, boolean onlyMine);

    AloudataAnalysisViewDetail getByName(Long datasourceId, String viewName);
}
