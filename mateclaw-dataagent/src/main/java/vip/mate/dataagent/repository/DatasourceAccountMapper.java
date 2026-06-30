package vip.mate.dataagent.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Component;
import vip.mate.dataagent.model.DatasourceAccountEntity;

/**
 * 数据源用户查询账号 Mapper
 */
@Mapper
@Component("dataagentDatasourceAccountMapper")
public interface DatasourceAccountMapper extends BaseMapper<DatasourceAccountEntity> {
}
