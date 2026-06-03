package vip.mate.dataagent.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Component;
import vip.mate.dataagent.model.DatasetEntity;

/**
 * 数据集 Mapper
 */
@Mapper
@Component("dataagentDatasetMapper")
public interface DatasetMapper extends BaseMapper<DatasetEntity> {
}
