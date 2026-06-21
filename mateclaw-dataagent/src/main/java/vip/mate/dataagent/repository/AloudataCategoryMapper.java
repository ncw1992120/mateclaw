package vip.mate.dataagent.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import vip.mate.dataagent.model.AloudataCategoryEntity;

import java.util.List;

/**
 * Aloudata 类目元数据 Mapper
 */
@Mapper
public interface AloudataCategoryMapper extends BaseMapper<AloudataCategoryEntity> {

    /**
     * 批量 Upsert（INSERT ... ON DUPLICATE KEY UPDATE）
     *
     * @param list 实体列表
     */
    void upsertBatch(List<AloudataCategoryEntity> list);
}
