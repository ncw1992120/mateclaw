package vip.mate.dataagent.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Component;
import vip.mate.dataagent.model.HelpCategoryEntity;

/**
 * 帮助文档分类 Mapper
 */
@Mapper
@Component("dataagentHelpCategoryMapper")
public interface HelpCategoryMapper extends BaseMapper<HelpCategoryEntity> {
}
