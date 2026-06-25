package vip.mate.dataagent.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Component;
import vip.mate.dataagent.model.BusinessTermEntity;

/**
 * 业务术语 Mapper
 */
@Mapper
@Component("dataagentBusinessTermMapper")
public interface BusinessTermMapper extends BaseMapper<BusinessTermEntity> {
}
