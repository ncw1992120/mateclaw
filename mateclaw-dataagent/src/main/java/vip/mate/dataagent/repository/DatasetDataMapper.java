package vip.mate.dataagent.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Component;
import vip.mate.dataagent.model.DatasetDataEntity;

@Mapper
@Component("dataagentDatasetDataMapper")
public interface DatasetDataMapper extends BaseMapper<DatasetDataEntity> {
}
