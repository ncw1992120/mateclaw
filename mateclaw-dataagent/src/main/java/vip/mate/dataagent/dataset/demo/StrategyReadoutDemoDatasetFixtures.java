package vip.mate.dataagent.dataset.demo;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import vip.mate.dataagent.dataset.*;
import vip.mate.dataagent.exception.BusinessException;

import java.util.*;
import java.util.stream.Collectors;

/**
 * “策略解读”本地演示所需的只读数据集夹具。
 * 仅在 local-mock profile 中注册，且只接管两个已知 ID；其他缺失数据集继续走原 404。
 */
@Component
@Profile("local-mock")
public class StrategyReadoutDemoDatasetFixtures {
    public static final long DIMENSION_DATASET_ID = 2104203094760529922L;
    public static final long METRIC_DATASET_ID = 2104203095955906562L;
    /** Persisted by the current “策略解读 new” dashboard before the local fixture IDs were standardized. */
    public static final long LEGACY_DASHBOARD_METRIC_DATASET_ID = 2104142033512488962L;
    public static final long LEGACY_DASHBOARD_DIMENSION_DATASET_ID = 2104142034569453570L;

    private static final List<DatasetColumn> DIMENSION_COLUMNS = List.of(
            dimension("metric_time", "指标日期", "date"),
            dimension("attribution_plan_id", "计划ID", "string"),
            dimension("attribution_strategy_id", "策略ID", "string"),
            dimension("platform_id", "子策略ID", "string"),
            dimension("channel", "触达渠道", "string"),
            dimension("metric_id", "转化指标ID", "string"),
            dimension("metric_name", "转化指标名称", "string"),
            dimension("attribution_plan_name", "计划名称", "string"),
            dimension("attribution_plan_um_account", "计划负责人", "string"),
            dimension("attribution_strategy_name", "策略名称", "string"),
            dimension("attribution_over_by", "策略负责人", "string"),
            dimension("plan_name", "计划名称", "string"),
            dimension("strategy_name", "策略名称", "string"),
            dimension("platform_name", "子策略名称", "string"),
            dimension("create_by", "子策略创建人", "string"),
            dimension("plan_owner", "计划负责人", "string"),
            dimension("strategy_owner", "策略负责人", "string"),
            dimension("platform_owner", "子策略创建人", "string"),
            measure("digo_strategy_cnt", "策略数", "bigint"),
            measure("digo_strategy_cnt_distr_1", "下发策略数", "bigint"),
            measure("digo_distr_count_1", "下发次数", "bigint"),
            measure("digo_distr_user_cnt_a", "下发人数", "bigint"),
            measure("digo_touch_cnt_1", "触达次数", "bigint"),
            measure("digo_touch_user_cnt_1", "触达人数", "bigint")
    );
    private static final List<DatasetColumn> METRIC_COLUMNS = List.of(
            DIMENSION_COLUMNS.get(0), DIMENSION_COLUMNS.get(1), DIMENSION_COLUMNS.get(2),
            DIMENSION_COLUMNS.get(3), DIMENSION_COLUMNS.get(4),
            measure("digo_trd_fund_amt_inout_cy_jjgr", "转化规模", "decimal"),
            measure("digo_trd_fund_amt_inout_cy_jjgr_trans_user_cnt", "转化人数", "bigint"),
            measure("digo_fund_trd_amt_a566_fh_kgdb_jj0", "交易量", "decimal"),
            measure("digo_fund_trd_amt_a566_fh_kgdb_jj0_trans_user_cnt", "交易人数", "bigint"),
            measure("digo_trd_fund_amt_inout_cy_jjgr_pb", "破冰客户净买入", "decimal"),
            measure("digo_pbcnt_kgdb_a566_fh_jjgr", "破冰客户数", "bigint"),
            measure("digo_pub_fh_kgdb_trdamt_ppcadd_jjgr", "加仓交易量", "decimal"),
            measure("digo_pub_fh_kgdb_trdamt_ppcadd_jjgr_trans_user_cnt", "加仓交易人数", "bigint"),
            measure("digo_cust_asset_in", "入金客户数", "bigint"),
            measure("digo_new_cust_asset_in", "新客入金数", "bigint"),
            measure("digo_cnt_cust_code_new_brok", "新增客户数", "bigint"),
            measure("digo_cust_valid_new_yxh", "新增有效户数", "bigint"),
            measure("digo_cust_asset_in10000", "万元入金户数", "bigint")
    );

    private static final List<Map<String, Object>> DIMENSION_ROWS = List.of(
            dimensionRow("2026-09-01", "PLAN-001", "STR-001", "SUB-001", "APP",
                    "trd_fund_amt_inout_cy_jjgr", "经纪个人客户场内公募非货当年净买入", "高净值客户触达策略", "场内公募非货子策略"),
            dimensionRow("2026-09-01", "PLAN-002", "STR-002", "SUB-002", "SMS",
                    "trd_fund_amt_inout_cy_jjgr", "经纪个人客户场内公募非货当年净买入", "新客增长计划", "新客入金子策略"),
            dimensionRow("2026-09-02", "PLAN-001", "STR-001", "SUB-001", "APP",
                    "trd_fund_amt_inout_cy_jjgr", "经纪个人客户场内公募非货当年净买入", "高净值客户触达策略", "场内公募非货子策略"),
            dimensionRow("2026-09-02", "PLAN-002", "STR-002", "SUB-002", "SMS",
                    "trd_fund_amt_inout_cy_jjgr", "经纪个人客户场内公募非货当年净买入", "新客增长计划", "新客入金子策略")
    );
    private static final List<Map<String, Object>> METRIC_ROWS = List.of(
            metricRow("2026-09-01", "PLAN-001", "STR-001", "SUB-001", "APP", 1_250_000, 128, 860_000, 74),
            metricRow("2026-09-01", "PLAN-002", "STR-002", "SUB-002", "SMS", 930_000, 88, 640_000, 57),
            metricRow("2026-09-02", "PLAN-001", "STR-001", "SUB-001", "APP", 1_320_000, 136, 910_000, 82),
            metricRow("2026-09-02", "PLAN-002", "STR-002", "SUB-002", "SMS", 1_010_000, 94, 700_000, 63)
    );

    public boolean supports(long datasetId) {
        return isDimensionDataset(datasetId) || isMetricDataset(datasetId);
    }

    public DatasetInputDescriptor describe(DatasetAccessContext context, long datasetId, String inputName) {
        requireReadable(context, datasetId);
        boolean dimension = isDimensionDataset(datasetId);
        return new DatasetInputDescriptor(datasetId, inputName == null || inputName.isBlank()
                ? dimension ? "table_wd" : "table_ab" : inputName,
                DatasetSourceType.JDBC_SQL, dimension ? DIMENSION_COLUMNS : METRIC_COLUMNS,
                (long) (dimension ? DIMENSION_ROWS.size() : METRIC_ROWS.size()),
                Map.of("demo", true, "label", "本地演示数据"), null);
    }

    public DatasetBatch read(DatasetAccessContext context, DatasetReadRequest request) {
        requireReadable(context, request.datasetId());
        List<DatasetColumn> schema = isDimensionDataset(request.datasetId())
                ? DIMENSION_COLUMNS : METRIC_COLUMNS;
        Set<String> knownFields = schema.stream().map(DatasetColumn::name).collect(Collectors.toSet());
        for (DatasetFilter filter : request.filters()) {
            if (!knownFields.contains(filter.field())) {
                throw new DatasetReadException(DatasetReadErrorCode.SCHEMA_MISMATCH,
                        "本地演示数据集不存在字段: " + filter.field());
            }
        }
        List<Map<String, Object>> sourceRows = isDimensionDataset(request.datasetId())
                ? DIMENSION_ROWS : METRIC_ROWS;
        List<Map<String, Object>> matched = sourceRows.stream()
                .filter(row -> request.filters().stream().allMatch(filter -> matches(row.get(filter.field()), filter)))
                .map(row -> (Map<String, Object>) new LinkedHashMap<>(row))
                .collect(Collectors.toCollection(ArrayList::new));
        for (int i = request.orders().size() - 1; i >= 0; i--) {
            DatasetSort sort = request.orders().get(i);
            if (!knownFields.contains(sort.field())) {
                throw new DatasetReadException(DatasetReadErrorCode.SCHEMA_MISMATCH,
                        "本地演示数据集不存在排序字段: " + sort.field());
            }
            Comparator<Map<String, Object>> comparator = Comparator.comparing(
                    row -> Objects.toString(row.get(sort.field()), ""), String.CASE_INSENSITIVE_ORDER);
            if ("desc".equals(sort.direction())) comparator = comparator.reversed();
            matched.sort(comparator);
        }
        int offset = Math.min(request.offset() == null ? 0 : request.offset(), matched.size());
        int limit = request.limit() == null ? 100 : request.limit();
        int end = Math.min(offset + limit, matched.size());
        List<String> projection = request.columns().isEmpty()
                ? schema.stream().map(DatasetColumn::name).toList() : request.columns();
        for (String column : projection) {
            if (!knownFields.contains(column)) {
                throw new DatasetReadException(DatasetReadErrorCode.SCHEMA_MISMATCH,
                        "本地演示数据集不存在投影字段: " + column);
            }
        }
        List<Map<String, Object>> rows = matched.subList(offset, end).stream()
                .map(row -> (Map<String, Object>) projection.stream().filter(row::containsKey)
                        .collect(Collectors.toMap(field -> field, row::get, (left, right) -> right, LinkedHashMap::new)))
                .toList();
        Long total = request.requestTotalCount() ? (long) matched.size() : null;
        return new DatasetBatch(rows, null, rows.size(), end >= matched.size(), null, total);
    }

    private void requireReadable(DatasetAccessContext context, long datasetId) {
        if (context == null || !context.canRead(datasetId)) {
            throw new BusinessException(403, "无权读取本地演示数据集");
        }
        if (!supports(datasetId)) {
            throw new BusinessException(404, "数据集不存在: " + datasetId);
        }
    }

    private static boolean isDimensionDataset(long datasetId) {
        return datasetId == DIMENSION_DATASET_ID || datasetId == LEGACY_DASHBOARD_DIMENSION_DATASET_ID;
    }

    private static boolean isMetricDataset(long datasetId) {
        return datasetId == METRIC_DATASET_ID || datasetId == LEGACY_DASHBOARD_METRIC_DATASET_ID;
    }

    private static boolean matches(Object actual, DatasetFilter filter) {
        Object expected = filter.value();
        String left = Objects.toString(actual, null);
        String right = Objects.toString(expected, null);
        return switch (filter.operator()) {
            case "eq" -> Objects.equals(left, right);
            case "neq" -> !Objects.equals(left, right);
            case "contains" -> left != null && right != null && left.contains(right);
            case "in", "not_in" -> {
                Collection<?> values = expected instanceof Collection<?> collection ? collection : List.of(expected);
                boolean contains = values.stream().anyMatch(value -> Objects.equals(left, Objects.toString(value, null)));
                yield "in".equals(filter.operator()) == contains;
            }
            case "is_null" -> actual == null;
            case "is_not_null" -> actual != null;
            case "between" -> {
                List<?> bounds = expected instanceof List<?> list ? list : List.of();
                if (bounds.size() != 2 || left == null) yield false;
                String lower = Objects.toString(bounds.get(0), "");
                String upper = Objects.toString(bounds.get(1), "");
                yield left.compareTo(lower) >= 0 && left.compareTo(upper) <= 0;
            }
            default -> throw new DatasetReadException(DatasetReadErrorCode.UNSUPPORTED_FILTER,
                    "本地演示数据暂不支持筛选操作符: " + filter.operator());
        };
    }

    private static DatasetColumn dimension(String name, String title, String type) {
        return new DatasetColumn(name, title, type, true, "dimension");
    }

    private static DatasetColumn measure(String name, String title, String type) {
        return new DatasetColumn(name, title, type, true, "measure");
    }

    private static Map<String, Object> dimensionRow(String date, String plan, String strategy, String platform,
                                                     String channel, String metricId, String metricName,
                                                     String strategyName, String platformName) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("metric_time", date);
        row.put("attribution_plan_id", plan);
        row.put("attribution_strategy_id", strategy);
        row.put("platform_id", platform);
        row.put("channel", channel);
        row.put("metric_id", metricId);
        row.put("metric_name", metricName);
        row.put("attribution_plan_name", "PLAN-001".equals(plan) ? "策略解读示例计划" : "新客增长策略");
        row.put("attribution_plan_um_account", "plan_owner");
        row.put("attribution_strategy_name", strategyName);
        row.put("attribution_over_by", "demo_strategy_owner");
        row.put("plan_name", "PLAN-001".equals(plan) ? "高净值客户触达计划" : "新客增长计划");
        row.put("strategy_name", strategyName);
        row.put("platform_name", platformName);
        row.put("create_by", "demo_data_owner");
        row.put("plan_owner", "demo_plan_owner");
        row.put("strategy_owner", "demo_strategy_owner");
        row.put("platform_owner", "demo_data_owner");
        row.put("digo_strategy_cnt", "STR-001".equals(strategy) ? 2 : 3);
        row.put("digo_strategy_cnt_distr_1", "STR-001".equals(strategy) ? 2 : 3);
        row.put("digo_distr_count_1", "STR-001".equals(strategy) ? 180 : 210);
        row.put("digo_distr_user_cnt_a", "STR-001".equals(strategy) ? 150 : 178);
        row.put("digo_touch_cnt_1", "STR-001".equals(strategy) ? 120 : 156);
        row.put("digo_touch_user_cnt_1", "STR-001".equals(strategy) ? 96 : 127);
        return Collections.unmodifiableMap(row);
    }

    private static Map<String, Object> metricRow(String date, String plan, String strategy, String platform,
                                                 String channel, long conversionValue, long conversionUsers,
                                                 long tradingValue, long tradingUsers) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("metric_time", date);
        row.put("attribution_plan_id", plan);
        row.put("attribution_strategy_id", strategy);
        row.put("platform_id", platform);
        row.put("channel", channel);
        row.put("digo_trd_fund_amt_inout_cy_jjgr", conversionValue);
        row.put("digo_trd_fund_amt_inout_cy_jjgr_trans_user_cnt", conversionUsers);
        row.put("digo_fund_trd_amt_a566_fh_kgdb_jj0", tradingValue);
        row.put("digo_fund_trd_amt_a566_fh_kgdb_jj0_trans_user_cnt", tradingUsers);
        row.put("digo_trd_fund_amt_inout_cy_jjgr_pb", conversionValue / 3);
        row.put("digo_pbcnt_kgdb_a566_fh_jjgr", conversionUsers / 2);
        row.put("digo_pub_fh_kgdb_trdamt_ppcadd_jjgr", conversionValue / 6);
        row.put("digo_pub_fh_kgdb_trdamt_ppcadd_jjgr_trans_user_cnt", conversionUsers / 3);
        row.put("digo_cust_asset_in", conversionUsers + 58);
        row.put("digo_new_cust_asset_in", conversionUsers / 2);
        row.put("digo_cnt_cust_code_new_brok", conversionUsers + 26);
        row.put("digo_cust_valid_new_yxh", conversionUsers / 2 + 19);
        row.put("digo_cust_asset_in10000", conversionUsers / 3 + 9);
        return Collections.unmodifiableMap(row);
    }
}
