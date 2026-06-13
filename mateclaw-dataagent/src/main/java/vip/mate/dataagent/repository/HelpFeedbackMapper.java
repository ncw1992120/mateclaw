package vip.mate.dataagent.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Component;
import vip.mate.dataagent.model.HelpFeedbackEntity;

/**
 * 帮助文档反馈 Mapper
 */
@Mapper
@Component("dataagentHelpFeedbackMapper")
public interface HelpFeedbackMapper extends BaseMapper<HelpFeedbackEntity> {
}
