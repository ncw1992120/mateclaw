package vip.mate.dataagent.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Component;
import vip.mate.dataagent.model.InsightReportEntity;

/**
 * 洞察报告 Mapper
 */
@Mapper
@Component("dataagentInsightReportMapper")
public interface InsightReportMapper extends BaseMapper<InsightReportEntity> {
}
