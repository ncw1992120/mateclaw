package vip.mate.dataagent.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Component;
import vip.mate.dataagent.model.DatasourceColumnEntity;

/**
 * 数据源字段元数据 Mapper
 */
@Mapper
@Component("dataagentDatasourceColumnMapper")
public interface DatasourceColumnMapper extends BaseMapper<DatasourceColumnEntity> {
}