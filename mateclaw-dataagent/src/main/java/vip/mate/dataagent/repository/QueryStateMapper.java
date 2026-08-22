package vip.mate.dataagent.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import vip.mate.dataagent.model.QueryStateEntity;

/**
 * 会话级成功查询基座状态 Mapper
 */
@Mapper
public interface QueryStateMapper extends BaseMapper<QueryStateEntity> {
}
