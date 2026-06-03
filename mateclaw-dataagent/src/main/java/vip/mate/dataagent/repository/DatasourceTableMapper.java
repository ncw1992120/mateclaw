package vip.mate.dataagent.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Component;
import vip.mate.dataagent.model.DatasourceTableEntity;

/**
 * 数据源表元数据 Mapper
 */
@Mapper
@Component("dataagentDatasourceTableMapper")
public interface DatasourceTableMapper extends BaseMapper<DatasourceTableEntity> {
}