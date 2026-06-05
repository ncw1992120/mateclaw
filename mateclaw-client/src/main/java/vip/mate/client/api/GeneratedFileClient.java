package vip.mate.client.api;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestTemplate;
import vip.mate.client.constant.ApiPathConstants;

import java.util.Map;

/**
 * 生成文件客户端
 * <p>
 * 对应服务端 /api/v1/files/generated 接口，提供生成文件下载功能。
 * 服务端直接返回 ResponseEntity（成功时为文件字节流，失败时为 Map），不走 R&lt;T&gt; 包装。
 */
public class GeneratedFileClient extends AbstractApiClient {

    public GeneratedFileClient(String baseUrl, RestTemplate restTemplate) {
        super(baseUrl, restTemplate);
    }

    /**
     * 下载生成文件
     *
     * @param id 文件 ID
     * @return 文件内容或错误信息
     */
    public Map<String, Object> download(Long id) {
        return get(resolvePath(ApiPathConstants.GENERATED_FILE_BY_ID, id),
                new ParameterizedTypeReference<Map<String, Object>>() {});
    }
}