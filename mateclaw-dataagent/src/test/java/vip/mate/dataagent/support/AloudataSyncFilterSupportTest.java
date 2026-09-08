package vip.mate.dataagent.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import vip.mate.dataagent.dto.AloudataConfigDTO;
import vip.mate.system.service.SystemSettingService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Aloudata 元数据同步黑名单过滤单元测试
 * <p>
 * 真实执行 QLExpress 引擎，验证表达式语义（in/startsWith/空值）、
 * 类目黑名单级联、全局与数据源级规则合并。
 */
class AloudataSyncFilterSupportTest {

    private SystemSettingService systemSettingService;
    private AloudataSyncFilterSupport support;

    @BeforeEach
    void setUp() {
        systemSettingService = Mockito.mock(SystemSettingService.class);
        when(systemSettingService.getString(anyString(), anyString())).thenReturn("");
        support = new AloudataSyncFilterSupport(systemSettingService, new ObjectMapper());
    }

    private AloudataSyncFilterSupport.SyncFilterRules buildRules(List<String> datasourceExpressions,
                                                                 List<AloudataSyncFilterSupport.CategoryRaw> categories) {
        AloudataConfigDTO config = new AloudataConfigDTO();
        config.setSyncFilterExpressions(datasourceExpressions);
        return support.buildRules(config, categories);
    }

    private AloudataSyncFilterSupport.CategoryRaw category(String id, String name, String parentId) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", id);
        data.put("name", name);
        data.put("parentId", parentId);
        return new AloudataSyncFilterSupport.CategoryRaw("CATEGORY_METRIC", data);
    }

    @Test
    @DisplayName("未配置表达式时规则禁用，过滤直通")
    void disabledWhenNoExpression() {
        AloudataSyncFilterSupport.SyncFilterRules rules = buildRules(List.of(), List.of());
        assertThat(rules.enabled()).isFalse();
        assertThat(support.isMetricBlacklisted(rules, Map.of("metricName", "任意指标"))).isFalse();
        assertThat(support.isDimensionBlacklisted(rules, Map.of("dimName", "任意维度"))).isFalse();
    }

    @Test
    @DisplayName("类目名称 in 黑名单命中，且级联过滤子类目")
    void categoryBlacklistWithCascade() {
        AloudataSyncFilterSupport.SyncFilterRules rules = buildRules(
                List.of("categoryName in ('测试类目', '敏感数据')"),
                List.of(
                        category("1", "测试类目", null),
                        category("2", "正常类目", null),
                        category("3", "子类目A", "1"),
                        category("4", "孙类目B", "3")));

        assertThat(rules.enabled()).isTrue();
        assertThat(rules.blockedCategoryIds()).containsExactlyInAnyOrder("1", "3", "4");
        assertThat(rules.blockedCategoryIds()).doesNotContain("2");
    }

    @Test
    @DisplayName("指标命中表达式（startsWith）不入库")
    void metricExpressionHit() {
        AloudataSyncFilterSupport.SyncFilterRules rules = buildRules(
                List.of("metricName.startsWith('test_')"),
                List.of());

        Map<String, Object> hit = new HashMap<>();
        hit.put("metricName", "test_临时指标");
        hit.put("metricCategoryId", "9");
        Map<String, Object> kept = new HashMap<>();
        kept.put("metricName", "正式指标");
        kept.put("metricCategoryId", "9");

        assertThat(support.isMetricBlacklisted(rules, hit)).isTrue();
        assertThat(support.isMetricBlacklisted(rules, kept)).isFalse();
    }

    @Test
    @DisplayName("指标所属类目被拉黑时级联过滤，优先于表达式")
    void metricCascadeByBlockedCategory() {
        AloudataSyncFilterSupport.SyncFilterRules rules = buildRules(
                List.of("categoryName in ('敏感类目')"),
                List.of(category("100", "敏感类目", null)));

        Map<String, Object> metric = new HashMap<>();
        metric.put("metricName", "销售金额");
        metric.put("metricCategoryId", "100");

        assertThat(support.isMetricBlacklisted(rules, metric)).isTrue();
    }

    @Test
    @DisplayName("维度上下文可用 datasetName 与 data 原始字段")
    void dimensionContextVariables() {
        AloudataSyncFilterSupport.SyncFilterRules rules = buildRules(
                List.of("datasetName == 'ods_order_detail' || data.originDataType == 'SECRET_TYPE'"),
                List.of());

        Map<String, Object> hit = new HashMap<>();
        hit.put("dimName", "订单维度");
        hit.put("datasetName", "ods_order_detail");
        Map<String, Object> hitByData = new HashMap<>();
        hitByData.put("dimName", "客户维度");
        hitByData.put("originDataType", "SECRET_TYPE");
        Map<String, Object> kept = new HashMap<>();
        kept.put("dimName", "时间维度");
        kept.put("datasetName", "ads_time");
        kept.put("originDataType", "STRING");

        assertThat(support.isDimensionBlacklisted(rules, hit)).isTrue();
        assertThat(support.isDimensionBlacklisted(rules, hitByData)).isTrue();
        assertThat(support.isDimensionBlacklisted(rules, kept)).isFalse();
    }

    @Test
    @DisplayName("坏表达式按未命中处理（fail-open），不影响其他规则")
    void badExpressionFailsOpen() {
        AloudataSyncFilterSupport.SyncFilterRules rules = buildRules(
                List.of("categoryName.contains(", "metricName.startsWith('test_')"),
                List.of(category("1", "正常类目", null)));

        assertThat(rules.blockedCategoryIds()).isEmpty();

        Map<String, Object> metric = new HashMap<>();
        metric.put("metricName", "test_临时指标");
        assertThat(support.isMetricBlacklisted(rules, metric)).isTrue();
    }

    @Test
    @DisplayName("全局系统配置与数据源级表达式合并去重")
    void mergeGlobalAndDatasourceExpressions() {
        when(systemSettingService.getString(
                eq("aloudata.sync.filter.expressions"), anyString()))
                .thenReturn("[\"categoryName in ('测试类目')\", \"metricName.startsWith('test_')\"]");
        AloudataSyncFilterSupport.SyncFilterRules rules = buildRules(
                List.of("metricName.startsWith('test_')", "dimName.startsWith('dim_test_')"),
                List.of());

        assertThat(rules.expressions()).containsExactly(
                "categoryName in ('测试类目')",
                "metricName.startsWith('test_')",
                "dimName.startsWith('dim_test_')");

        /* 系统配置命中的类目表达式应通过数据源级规则同样生效 */
        assertThat(rules.blockedCategoryIds()).isEmpty();
    }

    @Test
    @DisplayName("上下文空值归一：null 变量按空字符串参与匹配，不触发「对象为空」异常")
    void nullContextValuesNormalized() {
        /* 无名称类目：in 与 startsWith 均不命中（空串不匹配），全程无异常 */
        AloudataSyncFilterSupport.SyncFilterRules rules = buildRules(
                List.of("categoryName in ('未分类')", "name.startsWith('test_')"),
                List.of(category("1", null, null), category("2", "正常类目", null)));
        assertThat(rules.blockedCategoryIds()).isEmpty();

        /* 归一化语义可被显式利用：空字符串规则可命中无名称类目 */
        AloudataSyncFilterSupport.SyncFilterRules emptyNameRules = buildRules(
                List.of("name == ''"),
                List.of(category("1", null, null)));
        assertThat(emptyNameRules.blockedCategoryIds()).containsExactly("1");

        /* 指标上下文字段缺失同样归一化：startsWith 不抛异常，owner == '' 命中 */
        AloudataSyncFilterSupport.SyncFilterRules metricRules = buildRules(
                List.of("metricName.startsWith('test_')", "owner == ''"),
                List.of());
        Map<String, Object> metric = new HashMap<>();
        metric.put("metricName", null);
        metric.put("owner", null);
        assertThat(support.isMetricBlacklisted(metricRules, metric)).isTrue();
    }
}
