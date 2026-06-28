package vip.mate.dataagent.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import vip.mate.dataagent.model.ApprovalRecordEntity;

/**
 * 审批流程记录 Mapper
 */
@Mapper
public interface ApprovalRecordMapper extends BaseMapper<ApprovalRecordEntity> {
}
