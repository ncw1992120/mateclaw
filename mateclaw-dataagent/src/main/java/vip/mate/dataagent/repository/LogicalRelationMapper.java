package vip.mate.dataagent.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Component;
import vip.mate.dataagent.model.LogicalRelationEntity;

/**
 * 逻辑外键关系 Mapper
 */
@Mapper
@Component("dataagentLogicalRelationMapper")
public interface LogicalRelationMapper extends BaseMapper<LogicalRelationEntity> {
}
