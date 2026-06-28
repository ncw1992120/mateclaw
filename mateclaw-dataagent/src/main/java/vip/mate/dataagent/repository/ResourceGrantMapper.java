package vip.mate.dataagent.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import vip.mate.dataagent.model.ResourceGrantEntity;

/**
 * 通用资源授权 Mapper
 */
@Mapper
public interface ResourceGrantMapper extends BaseMapper<ResourceGrantEntity> {
}
