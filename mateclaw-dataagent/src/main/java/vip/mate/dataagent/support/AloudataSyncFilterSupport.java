package vip.mate.dataagent.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vip.mate.dataagent.constants.DataAgentConstants;
import vip.mate.dataagent.dto.AloudataConfigDTO;
import vip.mate.dataagent.util.QlExpressSupport;
import vip.mate.system.service.SystemSettingService;

import java.util.*;

/**
 * Aloudata 元数据同步黑名单过滤支持（QLExpress 动态规则）
 * <p>
 * 在元数据入库持久化前做黑名单动态判断：过滤表达式来源于两级配置并合并生效——
 * <ul>
 *   <li>全局级：系统配置 {@code aloudata.sync.filter.expressions}（QLExpress 布尔表达式 JSON 数组）；</li>
 *   <li>数据源级：connection_params 的 {@code syncFilterExpressions}（QLExpress 布尔表达式数组）。</li>
 * </ul>
 * 过滤语义为「黑名单」：元数据（类目/指标/维度）命中任一表达式即不落库持久化；
 * 类目命中后其在**同一类目树内**的全部级联子类目一并过滤（父子关系仅按 parentId 推导）。
 * 表达式执行异常按「未命中」处理（fail-open），避免坏规则误杀全量同步数据。
 * QLExpress 引擎调用统一委托 {@link QlExpressSupport}。
 * <p>
 * 表达式的求值上下文（同一份表达式会在三处分别求值）：
 * <ul>
 *   <li>类目上下文：{@code categoryName/name} 为类目自身名称，另有 {@code categoryType}
 *       （{@code CATEGORY_METRIC}/{@code CATEGORY_DIMENSION}）、{@code parentId}、{@code type} 等；</li>
 *   <li>指标上下文：{@code metricName} 为指标名，而 {@code categoryName/metricCategoryName}
 *       为「该指标所属类目的名字」；</li>
 *   <li>维度上下文：{@code dimName} 为维度名，而 {@code categoryName/dimCategoryName}
 *       为「该维度所属类目的名字」。</li>
 * </ul>
 * <b>注意</b>：指标类目树与维度类目树常使用同一套业务域命名，因此按名称写的规则会同时命中两棵树
 * （黑名单类目与级联按 categoryType 隔离，但表达式本身在两棵树上都会求值）。
 * 若只想过滤其中一侧，请显式限定作用域，例如
 * {@code categoryType == 'CATEGORY_DIMENSION' && categoryName in ('xxx', 'yyy')}。
 * <p>
 * 典型表达式示例：
 * <ul>
 *   <li>{@code categoryName in ("测试类目", "敏感数据")} —— 按类目名称黑名单（两棵树同名的都会命中）；</li>
 *   <li>{@code metricName.startsWith("test_")} —— 按指标名称前缀；</li>
 *   <li>{@code type == "SYSTEM"} —— 过滤系统内置类目。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AloudataSyncFilterSupport {

    // ==================== QLExpress 上下文变量名 ====================

    /** 通用变量：原始数据 Map（表达式可按 data.xxx 访问任意原始字段） */
    private static final String VAR_DATA = "data";

    /** 通用变量：主名称别名（类目=categoryName，指标=metricName，维度=dimName） */
    private static final String VAR_NAME = "name";

    /** 通用变量：类目 ID 别名（指标/维度上下文中指所属类目 ID） */
    private static final String VAR_CATEGORY_ID = "categoryId";

    /** 通用变量：类目名称别名（指标/维度上下文中指所属类目名称） */
    private static final String VAR_CATEGORY_NAME = "categoryName";

    /** 通用变量：展示状态 */
    private static final String VAR_DISPLAY_STATUS = "displayStatus";

    /** 类目上下文变量：类目类型 / 父级类目 ID / 上级类目 ID / 类型 */
    private static final String VAR_CATEGORY_TYPE = "categoryType";
    private static final String VAR_PARENT_ID = "parentId";
    private static final String VAR_FRONT_ID = "frontId";
    private static final String VAR_TYPE = "type";

    /** 指标上下文变量前缀对应的原始字段（metricName/metricCode/... 直接与 Aloudata 字段同名） */
    private static final String VAR_METRIC_NAME = "metricName";
    private static final String VAR_METRIC_CODE = "metricCode";
    private static final String VAR_METRIC_DISPLAY_NAME = "metricDisplayName";
    private static final String VAR_METRIC_CATEGORY_ID = "metricCategoryId";
    private static final String VAR_METRIC_CATEGORY_NAME = "metricCategoryName";
    private static final String VAR_STATUS = "status";
    private static final String VAR_PUBLISH_STATUS = "publishStatus";
    private static final String VAR_BUSINESS_CALIBER = "businessCaliber";
    private static final String VAR_OWNER = "owner";
    private static final String VAR_BUSINESS_OWNER = "businessOwner";
    private static final String VAR_UNIT = "unit";
    private static final String VAR_CN_UNIT = "cnUnit";
    private static final String VAR_TIME_GRANULARITY = "timeGranularity";

    /** 维度上下文变量（dimName/dimCode/... 直接与 Aloudata 字段同名） */
    private static final String VAR_DIM_NAME = "dimName";
    private static final String VAR_DIM_CODE = "dimCode";
    private static final String VAR_DIM_DISPLAY_NAME = "dimDisplayName";
    private static final String VAR_DIM_CATEGORY_ID = "dimCategoryId";
    private static final String VAR_DIM_CATEGORY_NAME = "dimCategoryName";
    private static final String VAR_DATASET_NAME = "datasetName";
    private static final String VAR_ORIGIN_DATA_TYPE = "originDataType";
    private static final String VAR_DIM_DESCRIPTION = "dimDescription";
    private static final String VAR_IS_TIME_DIMENSION = "isTimeDimension";

    private final SystemSettingService systemSettingService;
    private final ObjectMapper objectMapper;

    /**
     * 同步过滤规则（一次 fullSync 构建一次，供类目/指标/维度过滤共享）
     * <p>
     * 黑名单类目 ID 与「类目 ID → 名称」映射均按 {@code categoryType} 隔离存放：
     * 指标类目树与维度类目树各自独立建树、独立级联，避免两棵树的同名/id 相互污染。
     *
     * @param expressions              合并后的 QLExpress 黑名单表达式
     * @param blockedCategoryIdsByType categoryType → 黑名单类目 ID 集合（表达式直接命中 + 同类型级联子类目）
     * @param categoryNameByIdByType   categoryType → （类目 ID → 名称）映射（供指标/维度上下文填充所属类目名称）
     */
    public record SyncFilterRules(
            List<String> expressions,
            Map<String, Set<String>> blockedCategoryIdsByType,
            Map<String, Map<String, String>> categoryNameByIdByType
    ) {

        /** 未配置该类型时的空黑名单集合（避免调用方判空） */
        private static final Set<String> EMPTY_BLOCKED_IDS = Set.of();

        /** 未配置该类型时的空类目名映射（避免调用方判空） */
        private static final Map<String, String> EMPTY_CATEGORY_NAMES = Map.of();

        /**
         * 是否启用过滤（未配置任何表达式时为 false，调用方零开销直通）
         */
        public boolean enabled() {
            return !expressions.isEmpty();
        }

        /**
         * 取指定类目类型的黑名单类目 ID 集合
         *
         * @param categoryType 类目类型：CATEGORY_METRIC/CATEGORY_DIMENSION
         * @return 该类型的黑名单类目 ID 集合；未配置时为空集合
         */
        public Set<String> blockedCategoryIds(String categoryType) {
            return blockedCategoryIdsByType.getOrDefault(categoryType, EMPTY_BLOCKED_IDS);
        }

        /**
         * 取指定类目类型的「类目 ID → 名称」映射
         *
         * @param categoryType 类目类型：CATEGORY_METRIC/CATEGORY_DIMENSION
         * @return 该类型的类目名映射；未配置时为空映射
         */
        public Map<String, String> categoryNameById(String categoryType) {
            return categoryNameByIdByType.getOrDefault(categoryType, EMPTY_CATEGORY_NAMES);
        }
    }

    /**
     * 类目原始数据（含所属类目类型，供过滤上下文与黑名单级联计算使用）
     *
     * @param categoryType 类目类型：CATEGORY_METRIC/CATEGORY_DIMENSION
     * @param data         类目原始数据（category_list 响应条目）
     */
    public record CategoryRaw(String categoryType, Map<String, Object> data) {
    }

    /**
     * 构建同步过滤规则：合并全局配置与数据源级配置的表达式，
     * 并基于本次拉取的类目计算黑名单类目 ID 集合（含同类型级联子类目）。
     *
     * @param config        Aloudata 数据源配置（数据源级表达式来源）
     * @param rawCategories 本次拉取的类目原始数据（指标类目 + 维度类目）
     * @return 过滤规则；未配置表达式时 enabled() 为 false
     */
    public SyncFilterRules buildRules(AloudataConfigDTO config, List<CategoryRaw> rawCategories) {
        List<String> expressions = resolveExpressions(config);
        if (expressions.isEmpty()) {
            return new SyncFilterRules(expressions, Map.of(), Map.of());
        }

        /* 类目名映射按 categoryType 隔离，指标上下文只解析指标类目名、维度上下文只解析维度类目名 */
        Map<String, Map<String, String>> categoryNameByIdByType = new HashMap<>();
        for (CategoryRaw raw : rawCategories) {
            String id = asString(raw.data().get("id"));
            if (id == null) {
                continue;
            }
            String name = asString(raw.data().get("name"));
            if (name != null) {
                categoryNameByIdByType.computeIfAbsent(raw.categoryType(), key -> new HashMap<>()).put(id, name);
            }
        }

        Map<String, Set<String>> blockedCategoryIdsByType = resolveBlockedCategoryIds(expressions, rawCategories);
        int blockedCategoryCount = blockedCategoryIdsByType.values().stream().mapToInt(Set::size).sum();
        log.info("[Aloudata同步过滤] 规则表达式 {} 条，黑名单类目 {} 个（按类目类型隔离，含同类型级联子类目）",
                expressions.size(), blockedCategoryCount);
        return new SyncFilterRules(expressions, blockedCategoryIdsByType, categoryNameByIdByType);
    }

    /**
     * 指标是否命中黑名单：所属类目在黑名单中（级联），或表达式命中。命中则不入库持久化。
     *
     * @param filterRules 过滤规则
     * @param metricData  指标原始数据（metric_list 响应条目）
     * @return true 表示命中黑名单
     */
    public boolean isMetricBlacklisted(SyncFilterRules filterRules, Map<String, Object> metricData) {
        if (!filterRules.enabled()) {
            return false;
        }
        String categoryId = asString(metricData.get(VAR_METRIC_CATEGORY_ID));
        if (categoryId != null
                && filterRules.blockedCategoryIds(DataAgentConstants.ALOUDATA_CATEGORY_TYPE_METRIC).contains(categoryId)) {
            log.info("[Aloudata同步过滤] 指标 [{}] 所属类目 [{}] 在黑名单中，跳过入库",
                    metricData.get(VAR_METRIC_NAME), categoryId);
            return true;
        }
        Map<String, Object> context = buildMetricContext(filterRules, metricData);
        return isBlacklisted(filterRules.expressions(), context, "指标", asString(metricData.get(VAR_METRIC_NAME)));
    }

    /**
     * 维度是否命中黑名单：所属类目在黑名单中（级联），或表达式命中。命中则不入库持久化。
     * <p>
     * 兼容指标-维度关联数据（仅含 dimName/dimDisplayName）：缺少 dimCategoryId 时自动跳过类目判断。
     *
     * @param filterRules 过滤规则
     * @param dimData     维度原始数据（dimension_list/详情/关联响应条目）
     * @return true 表示命中黑名单
     */
    public boolean isDimensionBlacklisted(SyncFilterRules filterRules, Map<String, Object> dimData) {
        if (!filterRules.enabled()) {
            return false;
        }
        String categoryId = asString(dimData.get(VAR_DIM_CATEGORY_ID));
        if (categoryId != null
                && filterRules.blockedCategoryIds(DataAgentConstants.ALOUDATA_CATEGORY_TYPE_DIMENSION).contains(categoryId)) {
            log.info("[Aloudata同步过滤] 维度 [{}] 所属类目 [{}] 在黑名单中，跳过入库",
                    dimData.get(VAR_DIM_NAME), categoryId);
            return true;
        }
        Map<String, Object> context = buildDimensionContext(filterRules, dimData);
        return isBlacklisted(filterRules.expressions(), context, "维度", asString(dimData.get(VAR_DIM_NAME)));
    }

    /**
     * 解析过滤表达式：全局系统配置（aloudata.sync.filter.expressions，JSON 数组）
     * 与数据源级配置（connection_params.syncFilterExpressions）合并，去重保序。
     *
     * @param config Aloudata 数据源配置
     * @return 合并后的表达式列表（可能为空）
     */
    private List<String> resolveExpressions(AloudataConfigDTO config) {
        List<String> expressions = new ArrayList<>();
        String globalJson = systemSettingService.getString(
                DataAgentConstants.ALOUDATA_SYNC_FILTER_EXPRESSIONS_KEY, "");
        if (globalJson != null && !globalJson.isBlank()) {
            expressions.addAll(parseExpressions(globalJson, "系统配置 aloudata.sync.filter.expressions"));
        }
        if (config != null && config.getSyncFilterExpressions() != null) {
            config.getSyncFilterExpressions().stream()
                    .filter(expression -> expression != null && !expression.isBlank())
                    .map(String::trim)
                    .forEach(expressions::add);
        }
        /* 去重保序 */
        return new ArrayList<>(new LinkedHashSet<>(expressions));
    }

    /**
     * 解析 JSON 数组形式的表达式配置，剔除空白项
     *
     * @param json   配置 JSON
     * @param source 配置来源描述（用于告警日志）
     * @return 表达式列表；解析失败时返回空列表并告警
     */
    private List<String> parseExpressions(String json, String source) {
        try {
            List<String> parsed = objectMapper.readValue(json, new TypeReference<>() {
            });
            if (parsed == null) {
                return List.of();
            }
            return parsed.stream()
                    .filter(expression -> expression != null && !expression.isBlank())
                    .map(String::trim)
                    .toList();
        } catch (Exception e) {
            log.warn("[Aloudata同步过滤] 解析{}失败，忽略该来源: {}", source, e.getMessage());
            return List.of();
        }
    }

    /**
     * 计算黑名单类目 ID 集合：按 categoryType 分别建树，直接命中表达式的类目
     * 及其同类型全部级联子类目计入黑名单（父子关系仅按 parentId 推导，BFS 展开）。
     * <p>
     * 指标类目树与维度类目树相互独立：只有父类目在同类型中真实存在才建立父子边，
     * 避免一棵树上的命中通过 id 或上级引用把另一棵树整棵拉黑。
     *
     * @param expressions   黑名单表达式
     * @param rawCategories 类目原始数据
     * @return categoryType → 黑名单类目 ID 集合
     */
    private Map<String, Set<String>> resolveBlockedCategoryIds(List<String> expressions,
                                                               List<CategoryRaw> rawCategories) {
        Map<String, Map<String, String>> parentByIdByType = new HashMap<>();
        Map<String, Set<String>> idsByType = new HashMap<>();
        Map<String, Set<String>> blockedByType = new HashMap<>();
        Map<String, Integer> directHitCountByType = new HashMap<>();

        for (CategoryRaw raw : rawCategories) {
            String id = asString(raw.data().get("id"));
            if (id == null) {
                continue;
            }
            /* 父子关系仅按 parentId 推导（与前端类目树一致）；缺失/空白视为根节点 */
            String parentId = asString(raw.data().get(VAR_PARENT_ID));
            parentByIdByType.computeIfAbsent(raw.categoryType(), key -> new HashMap<>())
                    .put(id, parentId != null && !parentId.isBlank() ? parentId : null);
            idsByType.computeIfAbsent(raw.categoryType(), key -> new HashSet<>()).add(id);

            Map<String, Object> context = buildCategoryContext(raw.categoryType(), raw.data());
            if (isBlacklisted(expressions, context, "类目[" + raw.categoryType() + "]",
                    asString(raw.data().get("name")))) {
                blockedByType.computeIfAbsent(raw.categoryType(), key -> new HashSet<>()).add(id);
                directHitCountByType.merge(raw.categoryType(), 1, Integer::sum);
            }
        }

        /* 级联展开子类目：父类目被拉黑则同类型子类目一并拉黑 */
        for (Map.Entry<String, Map<String, String>> entry : parentByIdByType.entrySet()) {
            String categoryType = entry.getKey();
            Set<String> knownIds = idsByType.getOrDefault(categoryType, Set.of());
            Map<String, List<String>> childrenByParent = new HashMap<>();
            for (Map.Entry<String, String> node : entry.getValue().entrySet()) {
                String parentId = node.getValue();
                if (parentId != null && knownIds.contains(parentId)) {
                    childrenByParent.computeIfAbsent(parentId, key -> new ArrayList<>()).add(node.getKey());
                }
            }

            Set<String> blocked = blockedByType.computeIfAbsent(categoryType, key -> new HashSet<>());
            Deque<String> queue = new ArrayDeque<>(blocked);
            while (!queue.isEmpty()) {
                List<String> children = childrenByParent.get(queue.poll());
                if (children == null) {
                    continue;
                }
                for (String child : children) {
                    if (blocked.add(child)) {
                        queue.offer(child);
                    }
                }
            }

            int directHitCount = directHitCountByType.getOrDefault(categoryType, 0);
            log.info("[Aloudata同步过滤] 类目黑名单[{}]：直接命中 {} 个，级联子类目 {} 个，合计 {} 个",
                    categoryType, directHitCount, blocked.size() - directHitCount, blocked.size());
        }

        return blockedByType;
    }

    /**
     * 逐条执行黑名单表达式，命中任一（返回 Boolean.TRUE）即视为黑名单。
     * <p>
     * 表达式执行异常按「未命中」处理（fail-open）并告警，避免坏规则阻断全量同步。
     *
     * @param expressions 黑名单表达式
     * @param context     QLExpress 求值上下文
     * @param metaType    元数据类型描述（日志用：类目/指标/维度）
     * @param name        元数据名称（日志用）
     * @return true 表示命中黑名单
     */
    private boolean isBlacklisted(List<String> expressions, Map<String, Object> context,
                                  String metaType, String name) {
        for (String expression : expressions) {
            if (QlExpressSupport.evaluateBoolean(expression, context, false)) {
                log.info("[Aloudata同步过滤] {} [{}] 命中黑名单规则: {}", metaType, name, expression);
                return true;
            }
        }
        return false;
    }

    /**
     * 构建类目过滤上下文
     */
    private Map<String, Object> buildCategoryContext(String categoryType, Map<String, Object> categoryData) {
        Map<String, Object> context = new HashMap<>();
        String categoryName = asString(categoryData.get("name"));
        context.put(VAR_CATEGORY_TYPE, categoryType);
        context.put(VAR_CATEGORY_ID, asString(categoryData.get("id")));
        context.put(VAR_CATEGORY_NAME, categoryName);
        context.put(VAR_NAME, categoryName);
        context.put(VAR_PARENT_ID, asString(categoryData.get(VAR_PARENT_ID)));
        context.put(VAR_FRONT_ID, asString(categoryData.get(VAR_FRONT_ID)));
        context.put(VAR_TYPE, categoryData.get(VAR_TYPE));
        context.put(VAR_DATA, categoryData);
        return normalizeNullValues(context);
    }

    /**
     * 构建指标过滤上下文（字段与 Aloudata metric_list 原始字段同名，另补所属类目名称与通用别名）
     */
    private Map<String, Object> buildMetricContext(SyncFilterRules filterRules, Map<String, Object> metricData) {
        Map<String, Object> context = new HashMap<>();
        String categoryId = asString(metricData.get(VAR_METRIC_CATEGORY_ID));
        /* 注意：指标上下文中的 categoryName/metricCategoryName 是「该指标所属类目的名字」（取自指标类目树），
           并非指标自身名称；同名类目在指标/维度两棵树中都存在时，规则会同时作用于两棵树 */
        String categoryName = categoryId != null
                ? filterRules.categoryNameById(DataAgentConstants.ALOUDATA_CATEGORY_TYPE_METRIC).get(categoryId) : null;
        context.put(VAR_METRIC_NAME, metricData.get(VAR_METRIC_NAME));
        context.put(VAR_METRIC_CODE, metricData.get(VAR_METRIC_CODE));
        context.put(VAR_METRIC_DISPLAY_NAME, metricData.get(VAR_METRIC_DISPLAY_NAME));
        context.put(VAR_METRIC_CATEGORY_ID, categoryId);
        context.put(VAR_METRIC_CATEGORY_NAME, categoryName);
        context.put(VAR_CATEGORY_ID, categoryId);
        context.put(VAR_CATEGORY_NAME, categoryName);
        context.put(VAR_NAME, metricData.get(VAR_METRIC_NAME));
        context.put(VAR_TYPE, metricData.get(VAR_TYPE));
        context.put(VAR_STATUS, metricData.get(VAR_STATUS));
        context.put(VAR_PUBLISH_STATUS, metricData.get(VAR_PUBLISH_STATUS));
        context.put(VAR_DISPLAY_STATUS, metricData.get(VAR_DISPLAY_STATUS));
        context.put(VAR_BUSINESS_CALIBER, metricData.get(VAR_BUSINESS_CALIBER));
        context.put(VAR_OWNER, metricData.get(VAR_OWNER));
        context.put(VAR_BUSINESS_OWNER, metricData.get(VAR_BUSINESS_OWNER));
        context.put(VAR_UNIT, metricData.get(VAR_UNIT));
        context.put(VAR_CN_UNIT, metricData.get(VAR_CN_UNIT));
        context.put(VAR_TIME_GRANULARITY, metricData.get(VAR_TIME_GRANULARITY));
        context.put(VAR_DATA, metricData);
        return normalizeNullValues(context);
    }

    /**
     * 构建维度过滤上下文（字段与 Aloudata 维度原始字段同名，另补所属类目名称与通用别名）
     */
    private Map<String, Object> buildDimensionContext(SyncFilterRules filterRules, Map<String, Object> dimData) {
        Map<String, Object> context = new HashMap<>();
        String categoryId = asString(dimData.get(VAR_DIM_CATEGORY_ID));
        /* 注意：维度上下文中的 categoryName/dimCategoryName 是「该维度所属类目的名字」（取自维度类目树） */
        String categoryName = categoryId != null
                ? filterRules.categoryNameById(DataAgentConstants.ALOUDATA_CATEGORY_TYPE_DIMENSION).get(categoryId) : null;
        String dimName = asString(dimData.get(VAR_DIM_NAME));
        context.put(VAR_DIM_NAME, dimData.get(VAR_DIM_NAME));
        context.put(VAR_DIM_CODE, dimData.get(VAR_DIM_CODE));
        context.put(VAR_DIM_DISPLAY_NAME, dimData.get(VAR_DIM_DISPLAY_NAME));
        context.put(VAR_DIM_CATEGORY_ID, categoryId);
        context.put(VAR_DIM_CATEGORY_NAME, categoryName);
        context.put(VAR_CATEGORY_ID, categoryId);
        context.put(VAR_CATEGORY_NAME, categoryName);
        context.put(VAR_NAME, dimName);
        context.put(VAR_DATASET_NAME, dimData.get(VAR_DATASET_NAME));
        context.put(VAR_ORIGIN_DATA_TYPE, dimData.get(VAR_ORIGIN_DATA_TYPE));
        context.put(VAR_DIM_DESCRIPTION, dimData.get(VAR_DIM_DESCRIPTION));
        context.put(VAR_DISPLAY_STATUS, dimData.get(VAR_DISPLAY_STATUS));
        context.put(VAR_IS_TIME_DIMENSION, dimName != null
                && dimName.startsWith(DataAgentConstants.ALOUDATA_TIME_DIMENSION_NAME_PREFIX));
        context.put(VAR_DATA, dimData);
        return normalizeNullValues(context);
    }

    /**
     * 上下文空值归一：值为 null 的变量统一替换为空字符串。
     * <p>
     * QLExpress 原生运算符/方法对 null 操作数会抛「对象为空，不能执行方法」异常
     * （进而触发 fail-open 兜底产生告警噪音）。空值归一后，无名称的类目/指标/维度
     * 统一按空字符串参与匹配——不命中黑名单（语义等价）且表达式求值无异常。
     * {@code data} 原始数据 Map 恒非空，不受影响。
     *
     * @param context 过滤上下文
     * @return 归一化后的上下文
     */
    private Map<String, Object> normalizeNullValues(Map<String, Object> context) {
        context.replaceAll((key, value) -> value == null ? "" : value);
        return context;
    }

    /**
     * 对象转字符串（null 安全）
     */
    private String asString(Object value) {
        return value != null ? value.toString() : null;
    }
}
