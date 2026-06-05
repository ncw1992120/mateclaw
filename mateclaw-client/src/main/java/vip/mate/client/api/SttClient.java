package vip.mate.client.api;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestTemplate;
import vip.mate.client.constant.ApiPathConstants;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * STT 语音转写客户端
 * <p>
 * 对应服务端 /api/v1/stt 接口，提供语音转写功能。
 * 服务端直接返回 ResponseEntity，不走 R&lt;T&gt; 包装。
 */
public class SttClient extends AbstractApiClient {

    public SttClient(String baseUrl, RestTemplate restTemplate) {
        super(baseUrl, restTemplate);
    }

    /**
     * 转录音频
     *
     * @param language 语言代码
     * @return 转录结果
     */
    public Map<String, Object> transcribe(String language) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (language != null) {
            params.put("language", language);
        }
        return post(buildUrl(ApiPathConstants.STT_TRANSCRIBE, params), null,
                new ParameterizedTypeReference<Map<String, Object>>() {});
    }
}