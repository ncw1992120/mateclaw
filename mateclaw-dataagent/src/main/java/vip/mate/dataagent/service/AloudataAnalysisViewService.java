package vip.mate.dataagent.service;

import vip.mate.dataagent.dto.AloudataAnalysisViewDetail;
import vip.mate.dataagent.dto.AloudataAnalysisViewSummary;

import java.util.List;

public interface AloudataAnalysisViewService {
    List<AloudataAnalysisViewSummary> listTree(Long datasourceId);

    AloudataAnalysisViewDetail getByName(Long datasourceId, String viewName);
}
