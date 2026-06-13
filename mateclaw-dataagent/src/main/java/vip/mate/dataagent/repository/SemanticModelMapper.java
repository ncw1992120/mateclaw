package vip.mate.dataagent.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Component;
import vip.mate.dataagent.model.SemanticModelEntity;

/**
 * 字段级语义模型 Mapper
 */
@Mapper
@Component("dataagentSemanticModelMapper")
public interface SemanticModelMapper extends BaseMapper<SemanticModelEntity> {
}
