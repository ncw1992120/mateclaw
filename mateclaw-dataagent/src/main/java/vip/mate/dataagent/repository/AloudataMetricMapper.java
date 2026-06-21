package vip.mate.dataagent.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import vip.mate.dataagent.model.AloudataMetricEntity;

import java.util.List;

/**
 * Aloudata 指标元数据 Mapper
 */
@Mapper
public interface AloudataMetricMapper extends BaseMapper<AloudataMetricEntity> {

    /**
     * 批量 Upsert（INSERT ... ON DUPLICATE KEY UPDATE）
     *
     * @param list 实体列表
     */
    void upsertBatch(List<AloudataMetricEntity> list);
}
