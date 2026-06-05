package vip.mate.client.api;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestTemplate;
import vip.mate.client.constant.ApiPathConstants;

import java.util.List;
import java.util.Map;

/**
 * TTS 语音合成客户端
 * <p>
 * 对应服务端 /api/v1/tts 接口，提供语音合成功能。
 * 服务端直接返回 ResponseEntity，不走 R&lt;T&gt; 包装。
 */
public class TtsClient extends AbstractApiClient {

    public TtsClient(String baseUrl, RestTemplate restTemplate) {
        super(baseUrl, restTemplate);
    }

    /**
     * 合成语音
     *
     * @param body 合成参数
     * @return 合成结果（包含 audio 等 key）
     */
    public Map<String, Object> synthesize(Map<String, Object> body) {
        return post(ApiPathConstants.TTS_SYNTHESIZE, body,
                new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    /**
     * 获取语音列表
     *
     * @return 语音信息列表
     */
    public List<Map<String, Object>> listVoices() {
        return get(ApiPathConstants.TTS_VOICES,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {});
    }
}