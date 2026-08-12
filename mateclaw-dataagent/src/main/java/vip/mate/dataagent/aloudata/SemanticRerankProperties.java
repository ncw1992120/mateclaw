package vip.mate.dataagent.aloudata;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vip.mate.system.service.SystemSettingService;

/**
 * 语义检索 Rerank 配置（纯配置读取类）
 * <p>
 * 仅负责从 mate_system_setting 表读取 rerank 相关的配置项，
 * 不包含任何业务逻辑。业务编排由 {@code SemanticRerankService} 承载。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SemanticRerankProperties {

    /** 系统配置 key：是否开启语义检索 rerank 分支 */
    private static final String RERANK_ENABLED_KEY = "dataagent.search.rerank.enabled";

    private final SystemSettingService systemSettingService;

    /**
     * 读取 rerank 检索分支开关
     *
     * @return true 表示开启，数据库无配置时返回默认值 false
     */
    public boolean isRerankEnabled() {
        return systemSettingService.getBool(RERANK_ENABLED_KEY, false);
    }
}
