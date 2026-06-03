package vip.mate.dataagent.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Component;
import vip.mate.dataagent.model.DatasourceEntity;

/**
 * 数据源 Mapper
 */
@Mapper
@Component("dataagentDatasourceMapper")
public interface DatasourceMapper extends BaseMapper<DatasourceEntity> {
}