package vip.mate.dataagent.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import vip.mate.dataagent.model.AloudataDimensionEntity;

import java.util.List;

/**
 * Aloudata 维度元数据 Mapper
 */
@Mapper
public interface AloudataDimensionMapper extends BaseMapper<AloudataDimensionEntity> {

    /**
     * 批量 Upsert（INSERT ... ON DUPLICATE KEY UPDATE）
     *
     * @param list 实体列表
     */
    void upsertBatch(List<AloudataDimensionEntity> list);
}
