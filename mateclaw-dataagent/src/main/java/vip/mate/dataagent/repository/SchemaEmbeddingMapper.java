package vip.mate.dataagent.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Component;
import vip.mate.dataagent.model.SchemaEmbeddingEntity;

/**
 * Schema 嵌入向量 Mapper
 */
@Mapper
@Component("dataagentSchemaEmbeddingMapper")
public interface SchemaEmbeddingMapper extends BaseMapper<SchemaEmbeddingEntity> {
}
