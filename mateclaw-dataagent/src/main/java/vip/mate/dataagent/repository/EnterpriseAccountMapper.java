package vip.mate.dataagent.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import vip.mate.dataagent.model.EnterpriseAccountEntity;

/**
 * 企业认证影子账号映射 Mapper
 *
 * @author MateClaw Team
 */
@Mapper
public interface EnterpriseAccountMapper extends BaseMapper<EnterpriseAccountEntity> {
}
