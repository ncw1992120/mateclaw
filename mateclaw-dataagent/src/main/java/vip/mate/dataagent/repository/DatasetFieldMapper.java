package vip.mate.dataagent.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Component;
import vip.mate.dataagent.model.DatasetFieldEntity;

/**
 * 数据集字段 Mapper
 */
@Mapper
@Component("dataagentDatasetFieldMapper")
public interface DatasetFieldMapper extends BaseMapper<DatasetFieldEntity> {
}
