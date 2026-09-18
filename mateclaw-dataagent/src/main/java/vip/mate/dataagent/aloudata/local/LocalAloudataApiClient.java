package vip.mate.dataagent.aloudata.local;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import vip.mate.dataagent.aloudata.AloudataApiClient;
import vip.mate.dataagent.aloudata.AloudataEndpointService;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 本地 mock 的 Aloudata 客户端：只在 {@code local-mock} profile 下生效，接管全部上游调用。
 * <p>
 * 动机：真实指标视图可能造不出数据或未授权（如语义层 {@code SM_02_0038}），而本链路的所有
 * 上游访问都收敛在 {@link AloudataApiClient#send} 一个出口，因此只需在此接管即可
 * 让「洞察-仪表盘-组件-卡片-添加数据集-指标视图」与其「字段名称默认值」在本地跑通，
 * 且**不改变任何生产行为**（未激活 profile 时不会创建该 Bean）。
 * <p>
 * **与正式环境唯一的差异是 host:port**：请求的构建仍走真实逻辑
 * （端点声明 → 默认值 → 必填/枚举校验 → HEADER/QUERY/BODY 分发 → 拼 URL + 方法），
 * 接管只发生在"把请求发出去"这一步。两种本地模式：
 * <ol>
 *   <li><b>HTTP mock（推荐，最高保真）</b>：配置 {@code ALOUDATA_MOCK_SERVER=http://127.0.0.1:18081}，
 *       请求会以真实 HTTP 发到本地 mock 服务（{@code dev-support/local-simulation/scripts/aloudata-mock-server.py}），
 *       路径、query/body、请求方式、状态码与真实环境完全一致；</li>
 *   <li><b>内置夹具（零依赖兜底）</b>：未配置该地址时直接返回内置夹具报文，不发起任何网络请求。</li>
 * </ol>
 * 无论哪种模式，参数校验、URL 拼装、请求方式都由真实代码决定，本地不会掩盖
 * 「参数写错 / 方法配错 / 必填缺失 / 请求体形状错误」这类真机必炸的问题。
 * <p>
 * 启用方式：{@code SPRING_PROFILES_ACTIVE=pgsql,local-mock}
 */
@Slf4j
@Primary
@Component
@Profile("local-mock")
public class LocalAloudataApiClient extends AloudataApiClient {

    private final LocalAloudataFixtures fixtures;

    /** 本地 mock 服务基地址（http://127.0.0.1:18081）；为空时退回内置夹具。 */
    private final String mockServerUrl;

    public LocalAloudataApiClient(AloudataEndpointService endpointService, LocalAloudataFixtures fixtures,
                                  @Value("${ALOUDATA_MOCK_SERVER:}") String mockServerUrl) {
        super(endpointService);
        this.fixtures = fixtures;
        this.mockServerUrl = mockServerUrl == null ? "" : mockServerUrl.trim().replaceAll("/$", "");
        if (this.mockServerUrl.isEmpty()) {
            log.warn("★ local-mock 已启用（内置夹具）：请求构建与真实一致，HTTP 发送替换为内置夹具报文");
        } else {
            log.warn("★ local-mock 已启用（HTTP mock）：真实 HTTP 发送到 {}，仅 host:port 与正式不同", this.mockServerUrl);
        }
    }

    /**
     * 接管的唯一环节：把已构建好的请求发出去。
     * <p>
     * 配置了 mock 服务地址时按**真实 HTTP** 发送（仅改写 scheme/host/port），否则返回内置夹具。
     */
    @Override
    protected ResponseEntity<Map> send(PreparedRequest request) {
        if (!mockServerUrl.isEmpty()) {
            String url = rewriteBase(request.url(), mockServerUrl);
            Object body = request.bodyParams().isEmpty() ? null : request.bodyParams();
            log.info("[local-mock] {} {} -> {}", request.method(), request.path(), url);
            return exchange(url, request.method(), new HttpEntity<>(body, request.headers()));
        }
        Map<String, Object> params = new LinkedHashMap<>(request.queryParams());
        params.putAll(request.bodyParams());
        if (log.isInfoEnabled()) {
            log.info("[local-mock] {} {} -> {} params={}", request.method(), request.path(),
                    request.url(), params.keySet());
        }
        return ResponseEntity.ok(fixtures.payload(request.endpointName(), params, authValue(request)));
    }

    /** 保留路径与查询串，只把 scheme://host:port 换成本地 mock 服务地址。 */
    private String rewriteBase(String url, String base) {
        URI uri = URI.create(url);
        return base + uri.getRawPath() + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
    }

    /** 「我」的身份取自真实认证头，与服务端视角一致（「只看我的」依赖它）。 */
    private String authValue(PreparedRequest request) {
        return request.headers() == null ? null : request.headers().getFirst("auth-value");
    }
}
