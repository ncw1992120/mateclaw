package vip.mate.dataagent.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import vip.mate.dataagent.model.AloudataMetricDimensionEntity;

import java.util.List;

/**
 * Aloudata 指标-维度关联关系 Mapper
 */
@Mapper
public interface AloudataMetricDimensionMapper extends BaseMapper<AloudataMetricDimensionEntity> {

    /**
     * 批量 Upsert（INSERT ... ON DUPLICATE KEY UPDATE）
     *
     * @param list 实体列表
     */
    void upsertBatch(List<AloudataMetricDimensionEntity> list);
}
