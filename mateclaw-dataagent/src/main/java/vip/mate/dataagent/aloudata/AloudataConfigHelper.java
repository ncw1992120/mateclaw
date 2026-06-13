package vip.mate.dataagent.aloudata;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vip.mate.dataagent.dto.AloudataConfigDTO;
import vip.mate.dataagent.model.DatasourceEntity;

import java.util.Map;

/**
 * Aloudata 配置解析工具
 * <p>
 * 统一从 {@link DatasourceEntity} 解析为 {@link AloudataConfigDTO}，
 * 消除 AloudataServiceImpl 和 DatasourceManageServiceImpl 中的重复代码。
 */
@Slf4j
@Component
public class AloudataConfigHelper {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 从数据源实体解析 Aloudata 配置
     *
     * @param entity 数据源实体
     * @return Aloudata 配置
     */
    public AloudataConfigDTO parseConfig(DatasourceEntity entity) {
        AloudataConfigDTO config = new AloudataConfigDTO();
        config.setAnymetricsHost(entity.getHost());
        config.setSemanticHost(entity.getHost());

        if (entity.getConnectionParams() != null && !entity.getConnectionParams().isEmpty()) {
            try {
                Map<String, Object> params = objectMapper.readValue(entity.getConnectionParams(),
                        new TypeReference<Map<String, Object>>() {});
                if (params.get("anymetricsPort") != null) {
                    config.setAnymetricsPort(((Number) params.get("anymetricsPort")).intValue());
                }
                if (params.get("semanticPort") != null) {
                    config.setSemanticPort(((Number) params.get("semanticPort")).intValue());
                }
                if (params.get("authType") != null) {
                    config.setAuthType((String) params.get("authType"));
                }
                // 解析数据源级别的 API 端点覆盖
                if (params.get("apiOverrides") != null) {
                    @SuppressWarnings("unchecked")
                    Map<String, String> overrides = objectMapper.convertValue(
                            params.get("apiOverrides"), new TypeReference<Map<String, String>>() {});
                    config.setApiOverrides(overrides);
                }
            } catch (Exception e) {
                log.warn("解析 connectionParams 失败，使用默认配置: {}", e.getMessage());
            }
        }

        // username 字段存储租户ID，password 字段存储认证值
        config.setTenantId(entity.getUsername());
        config.setAuthValue(entity.getPassword());
        return config;
    }
}
