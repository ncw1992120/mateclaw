package vip.mate.dataagent.aloudata.local;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import vip.mate.dataagent.aloudata.AloudataApiClient;
import vip.mate.dataagent.aloudata.AloudataEndpointService;
import vip.mate.dataagent.dto.AloudataConfigDTO;

import java.util.Map;

/**
 * 本地 mock 的 Aloudata 客户端：只在 {@code local-mock} profile 下生效，接管全部上游调用。
 * <p>
 * 动机：真实指标视图可能造不出数据或未授权（如语义层 {@code SM_02_0038}），而本链路的所有
 * 上游访问都收敛在 {@link AloudataApiClient#callWithParams} 一个出口，因此只需在此短路即可
 * 让「洞察-仪表盘-组件-卡片-添加数据集-指标视图」与其「字段名称默认值」在本地跑通，
 * 且**不改变任何生产行为**（未激活 profile 时不会创建该 Bean）。
 * <p>
 * 短路后，{@code AloudataAnalysisViewServiceImpl} 的树解析与字段聚合、{@code
 * AloudataAnalysisViewAdapter} 的行/列式容错等真实逻辑仍会完整执行，mock 只替换数据来源。
 * <p>
 * 启用方式：{@code SPRING_PROFILES_ACTIVE=pgsql,local-mock}
 */
@Slf4j
@Primary
@Component
@Profile("local-mock")
public class LocalAloudataApiClient extends AloudataApiClient {

    private final LocalAloudataFixtures fixtures;

    public LocalAloudataApiClient(AloudataEndpointService endpointService, LocalAloudataFixtures fixtures) {
        super(endpointService);
        this.fixtures = fixtures;
        log.warn("★ local-mock 已启用：Aloudata 上游调用全部返回内置 mock 报文，不会发起任何真实 HTTP 请求");
    }

    @Override
    public ResponseEntity<Map> callWithParams(String endpointName, AloudataConfigDTO config, Map<String, Object> params) {
        if (log.isInfoEnabled()) {
            log.info("[local-mock] {} params={}", endpointName, params == null ? Map.of() : params.keySet());
        }
        return ResponseEntity.ok(fixtures.payload(endpointName, params, authValue(config)));
    }

    /**
     * 少数调用方走不带参数规范的 {@code call(...)}；同样短路，避免漏网打到真实上游。
     * （三参数的 {@code call} 会委托到本重载，故只需覆写这一个。）
     */
    @Override
    public ResponseEntity<Map> call(String endpointName, AloudataConfigDTO config,
                                    Map<String, String> pathVariables, Object requestBody) {
        if (log.isInfoEnabled()) {
            log.info("[local-mock] {} (call) pathVariables={}", endpointName, pathVariables);
        }
        return ResponseEntity.ok(fixtures.payload(endpointName, Map.of(), authValue(config)));
    }

    /** 数据源认证值即「我」的 Aloudata 身份，供 mock 生成 owner（「只看我的」依赖它）。 */
    private String authValue(AloudataConfigDTO config) {
        return config == null ? null : config.getAuthValue();
    }
}
