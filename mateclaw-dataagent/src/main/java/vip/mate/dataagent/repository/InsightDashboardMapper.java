package vip.mate.dataagent.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Component;
import vip.mate.dataagent.model.InsightDashboardEntity;

/**
 * 洞察仪表盘 Mapper
 */
@Mapper
@Component("dataagentInsightDashboardMapper")
public interface InsightDashboardMapper extends BaseMapper<InsightDashboardEntity> {
}
