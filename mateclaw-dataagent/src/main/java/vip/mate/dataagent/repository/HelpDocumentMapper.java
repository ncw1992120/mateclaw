package vip.mate.dataagent.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Component;
import vip.mate.dataagent.model.HelpDocumentEntity;

/**
 * 帮助文档 Mapper
 */
@Mapper
@Component("dataagentHelpDocumentMapper")
public interface HelpDocumentMapper extends BaseMapper<HelpDocumentEntity> {
}
