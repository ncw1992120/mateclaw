package vip.mate.dataagent.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import vip.mate.dataagent.model.UserUidMappingEntity;

import java.util.List;

/**
 * 用户 Aloudata UID 映射 Mapper
 */
@Mapper
public interface UserUidMappingMapper extends BaseMapper<UserUidMappingEntity> {

    /**
     * 批量 upsert 映射记录（按业务唯一键 username + tenant_id 冲突更新）
     *
     * @param list 映射实体列表（批内已去重）
     */
    void upsertBatch(@Param("list") List<UserUidMappingEntity> list);
}
