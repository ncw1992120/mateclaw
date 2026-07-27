package vip.mate.dataagent.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Component;
import vip.mate.dataagent.model.InsightReportSubscriptionEntity;

/**
 * 洞察报告订阅 Mapper
 */
@Mapper
@Component("dataagentInsightReportSubscriptionMapper")
public interface InsightReportSubscriptionMapper extends BaseMapper<InsightReportSubscriptionEntity> {
}
