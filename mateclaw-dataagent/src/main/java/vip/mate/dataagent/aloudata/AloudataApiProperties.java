package vip.mate.dataagent.aloudata;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vip.mate.system.service.SystemSettingService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aloudata API 端点配置（纯配置读取类）
 * <p>
 * 仅负责从 mate_system_setting 表读取端点配置的原始值，
 * 不包含缓存、默认兜底等任何业务逻辑。
 * <p>
 * 缓存决策、默认端点构建等业务逻辑由 {@link AloudataEndpointService} 承载。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AloudataApiProperties {

    /** 系统配置 key：API 端点映射 */
    private static final String ENDPOINTS_KEY = "aloudata.api.endpoints";

    /** 系统配置 key：API 版本号 */
    private static final String VERSION_KEY = "aloudata.api.version";

    private final SystemSettingService systemSettingService;
    private final ObjectMapper objectMapper;

    /**
     * 从数据库读取 API 版本号
     *
     * @return 版本号，数据库无配置时返回默认值 "v1"
     */
    public String readVersion() {
        return systemSettingService.getString(VERSION_KEY, "v1");
    }

    /**
     * 从数据库读取端点配置 JSON 并解析
     *
     * @return 端点配置 Map，数据库无配置或解析失败时返回 null
     */
    public Map<String, ApiEndpoint> readEndpoints() {
        String endpointsJson = systemSettingService.getString(ENDPOINTS_KEY, "");
        if (endpointsJson == null || endpointsJson.isBlank()) {
            return null;
        }

        try {
            return objectMapper.readValue(
                    endpointsJson, new TypeReference<LinkedHashMap<String, ApiEndpoint>>() {});
        } catch (Exception e) {
            log.warn("解析 Aloudata API 端点配置失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * API 端点定义
     */
    @Data
    public static class ApiEndpoint {

        /** 所属服务：anymetrics / semantic */
        private String service;

        /** 路径模板，支持 {var} 占位符 */
        private String path;

        /** HTTP 方法 */
        private String method;

        /** 端点描述 */
        private String description;

        /** 请求参数规范列表 */
        private List<ApiParam> requestParams;

        /** 响应参数规范列表 */
        private List<ApiParam> responseParams;

        public ApiEndpoint() {
        }

        public ApiEndpoint(String service, String path, String method, String description) {
            this.service = service;
            this.path = path;
            this.method = method;
            this.description = description;
        }

        public ApiEndpoint(String service, String path, String method, String description,
                           List<ApiParam> requestParams, List<ApiParam> responseParams) {
            this.service = service;
            this.path = path;
            this.method = method;
            this.description = description;
            this.requestParams = requestParams;
            this.responseParams = responseParams;
        }
    }
}
