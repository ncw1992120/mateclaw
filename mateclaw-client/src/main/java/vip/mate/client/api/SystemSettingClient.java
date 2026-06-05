package vip.mate.client.api;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestTemplate;
import vip.mate.client.constant.ApiPathConstants;
import vip.mate.client.model.R;
import vip.mate.client.model.SystemSettings;
import vip.mate.client.model.request.LanguageReq;
import vip.mate.client.model.request.SidecarReq;

/**
 * 系统设置客户端
 */
public class SystemSettingClient extends AbstractApiClient {

    public SystemSettingClient(String baseUrl, RestTemplate restTemplate) {
        super(baseUrl, restTemplate);
    }

    /**
     * 获取系统设置
     */
    public R<SystemSettings> getSettings() {
        return get(ApiPathConstants.SETTINGS, new ParameterizedTypeReference<R<SystemSettings>>() {});
    }

    /**
     * 保存系统设置
     */
    public R<SystemSettings> saveSettings(SystemSettings dto) {
        return put(ApiPathConstants.SETTINGS, dto, new ParameterizedTypeReference<R<SystemSettings>>() {});
    }

    /**
     * 获取系统语言
     */
    public R<String> getLanguage() {
        return get(ApiPathConstants.SETTINGS_LANGUAGE, new ParameterizedTypeReference<R<String>>() {});
    }

    /**
     * 保存系统语言
     *
     * @param request 语言保存请求
     * @return 保存后的语言
     */
    public R<String> saveLanguage(LanguageReq request) {
        return put(ApiPathConstants.SETTINGS_LANGUAGE, request,
                new ParameterizedTypeReference<R<String>>() {});
    }

    /**
     * 保存 Sidecar 配置
     *
     * @param request Sidecar 配置请求
     * @return 更新后的系统设置
     */
    public R<SystemSettings> saveSidecar(SidecarReq request) {
        return put(ApiPathConstants.SETTINGS_SIDECAR, request,
                new ParameterizedTypeReference<R<SystemSettings>>() {});
    }
}
