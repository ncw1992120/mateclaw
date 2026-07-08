package vip.mate.dataagent.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import vip.mate.dataagent.aloudata.AloudataApiClient;
import vip.mate.dataagent.aloudata.AloudataConfigHelper;
import vip.mate.dataagent.aloudata.AloudataEndpointService;
import vip.mate.dataagent.auth.context.UserContextHolder;
import vip.mate.dataagent.dto.*;
import vip.mate.dataagent.model.DatasourceEntity;
import vip.mate.dataagent.repository.DatasourceMapper;
import vip.mate.dataagent.service.AloudataService;
import vip.mate.dataagent.service.DatasourceAccountService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aloudata 指标平台服务实现
 * <p>
 * 通过 {@link AloudataApiClient} 发起 HTTP 请求，API 端点路径从数据库配置读取，
 * 支持数据源级别覆盖。API 版本升级时只需修改数据库配置，无需改代码。
 * <p>
 * 认证方式：直接在请求 Header 中注入 tenant-id / auth-type / auth-value，
 * 无需预先获取 Token。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AloudataServiceImpl implements AloudataService {

    private final DatasourceMapper datasourceMapper;
    private final AloudataApiClient apiClient;
    private final AloudataConfigHelper configHelper;
    private final AloudataEndpointService endpointService;
    private final DatasourceAccountService datasourceAccountService;

    /** 测试连接使用的 API 端点名 */
    private static final String TEST_CONNECTION_ENDPOINT = "category_list";

    /** 指标列表端点名 */
    private static final String METRICS_LIST_ENDPOINT = "metrics_list";

    /** 指标查询端点名 */
    private static final String METRICS_QUERY_ENDPOINT = "metrics_query";

    /** 指标详情端点名 */
    private static final String METRIC_DETAIL_ENDPOINT = "metric_detail";

    /** 指标可用维度端点名 */
    private static final String METRIC_AVAILABLE_DIMENSIONS_ENDPOINT = "metric_available_dimensions";

    /** 维度列表端点名 */
    private static final String DIMENSIONS_LIST_ENDPOINT = "dimensions_list";

    /** 维度详情端点名 */
    private static final String DIMENSION_DETAIL_ENDPOINT = "dimension_detail";

    /** 指标列表响应字段 → DTO 属性别名映射（API 字段名与 DTO 属性名不一致时使用） */
    private static final Map<String, String> METRIC_FIELD_ALIASES = Map.of(
            "id", "metricId",
            "metricCategoryId", "metricCategoryId"
    );

    /** 维度列表响应字段 → DTO 属性别名映射 */
    private static final Map<String, String> DIMENSION_FIELD_ALIASES = Map.of(
            "id", "dimensionId"
    );

    /**
     * 解析数据源配置，并使用当前用户的 Aloudata 认证值替换管理员认证值（仅查询场景使用）
     * <p>
     * tenant-id 和 auth-type 仍来自数据源共享配置，仅 auth-value 替换为用户绑定的认证值。
     * 用户必须绑定自己的 Aloudata 认证值才能执行查询，未绑定时抛出异常，不允许回退到管理员账号。
     *
     * @param datasourceId 数据源 ID
     * @return 替换用户认证值后的配置
     */
    private AloudataConfigDTO parseConfigWithUserAuth(Long datasourceId) {
        DatasourceEntity entity = datasourceMapper.selectById(datasourceId);
        if (entity == null) {
            throw new RuntimeException("数据源不存在: " + datasourceId);
        }
        AloudataConfigDTO config = configHelper.parseConfig(entity);
        Long currentUserId = UserContextHolder.getUserId();
        if (currentUserId == null) {
            throw new RuntimeException("当前用户未登录，无法执行 Aloudata 查询");
        }
        String userAuthValue = datasourceAccountService.resolveAloudataAuthValue(datasourceId, currentUserId);
        if (userAuthValue == null) {
            throw new RuntimeException("当前用户未绑定 Aloudata 认证值，请先在数据源页面配置查询账号");
        }
        config.setAuthValue(userAuthValue);
        return config;
    }

    /**
     * 测试 Aloudata 连接
     * <p>
     * 通过调用 category_list 接口验证连接和认证是否正常，
     * 相比 metrics_list 返回数据量更小，响应更快
     */
    @Override
    public boolean testConnection(AloudataConfigDTO config) {
        try {
            Map<String, Object> params = endpointService.buildHeaderParamsFromConfig(TEST_CONNECTION_ENDPOINT, config);

            ResponseEntity<Map> response = apiClient.callWithParams(TEST_CONNECTION_ENDPOINT, config, params);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Boolean success = (Boolean) response.getBody().get("success");
                return Boolean.TRUE.equals(success);
            }
            return false;
        } catch (Exception e) {
            log.error("测试 Aloudata 连接失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 查询指标列表
     */
    @Override
    public List<AloudataMetricVO> listMetrics(Long datasourceId) {
        AloudataConfigDTO config = parseConfigWithUserAuth(datasourceId);

        try {
            Map<String, Object> params = endpointService.buildHeaderParamsFromConfig(METRICS_LIST_ENDPOINT, config);

            ResponseEntity<Map> response = apiClient.callWithParams(METRICS_LIST_ENDPOINT, config, params);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                Boolean success = (Boolean) responseBody.get("success");
                if (Boolean.TRUE.equals(success)) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> metricsList = (List<Map<String, Object>>) responseBody.get("data");
                    if (metricsList != null) {
                        List<AloudataMetricVO> result = new ArrayList<>();
                        for (Map<String, Object> metric : metricsList) {
                            AloudataMetricVO vo = new AloudataMetricVO();
                            endpointService.mapResponseToDto(METRICS_LIST_ENDPOINT, metric, vo, METRIC_FIELD_ALIASES);
                            result.add(vo);
                        }
                        return result;
                    }
                }
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("查询 Aloudata 指标列表失败: {}", e.getMessage());
            throw new RuntimeException("查询指标列表失败: " + e.getMessage(), e);
        }
    }

    /**
     * 查询维度列表
     */
    @Override
    public List<AloudataDimensionVO> listDimensions(Long datasourceId) {
        AloudataConfigDTO config = parseConfigWithUserAuth(datasourceId);

        try {
            Map<String, Object> params = endpointService.buildHeaderParamsFromConfig(DIMENSIONS_LIST_ENDPOINT, config);

            ResponseEntity<Map> response = apiClient.callWithParams(DIMENSIONS_LIST_ENDPOINT, config, params);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                Boolean success = (Boolean) responseBody.get("success");
                if (Boolean.TRUE.equals(success)) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> dimensionsList = (List<Map<String, Object>>) responseBody.get("data");
                    if (dimensionsList != null) {
                        List<AloudataDimensionVO> result = new ArrayList<>();
                        for (Map<String, Object> dimension : dimensionsList) {
                            AloudataDimensionVO vo = new AloudataDimensionVO();
                            endpointService.mapResponseToDto(DIMENSIONS_LIST_ENDPOINT, dimension, vo, DIMENSION_FIELD_ALIASES);
                            result.add(vo);
                        }
                        return result;
                    }
                }
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("查询 Aloudata 维度列表失败: {}", e.getMessage());
            throw new RuntimeException("查询维度列表失败: " + e.getMessage(), e);
        }
    }

    /**
     * 执行指标数据查询
     */
    @Override
    public AloudataMetricQueryResponse queryMetrics(Long datasourceId, AloudataMetricQueryRequest request) {
        AloudataConfigDTO config = parseConfigWithUserAuth(datasourceId);

        try {
            Map<String, Object> params = endpointService.buildParamsFromConfigAndInput(
                    METRICS_QUERY_ENDPOINT, config, request);

            ResponseEntity<Map> response = apiClient.callWithParams(METRICS_QUERY_ENDPOINT, config, params);

            AloudataMetricQueryResponse result = new AloudataMetricQueryResponse();
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                result.setSuccess((Boolean) responseBody.get("success"));
                result.setCode((String) responseBody.get("code"));
                result.setErrorMsg((String) responseBody.get("errorMsg"));
                result.setTraceId((String) responseBody.get("traceId"));

                if (responseBody.get("data") != null) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = (Map<String, Object>) responseBody.get("data");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> table = (Map<String, Object>) data.get("table");
                    AloudataMetricQueryResponse.MetricData metricData = new AloudataMetricQueryResponse.MetricData();

                    // 解析列式 columns：Map<String, List<{value, flag, count}>>
                    @SuppressWarnings("unchecked")
                    Map<String, List<Map<String, Object>>> rawColumns =
                            (Map<String, List<Map<String, Object>>>) table.get("columns");
                    if (rawColumns != null) {
                        Map<String, List<AloudataMetricQueryResponse.ColumnValue>> typedColumns = new LinkedHashMap<>();
                        for (Map.Entry<String, List<Map<String, Object>>> entry : rawColumns.entrySet()) {
                            List<AloudataMetricQueryResponse.ColumnValue> values = new ArrayList<>();
                            if (entry.getValue() != null) {
                                for (Map<String, Object> item : entry.getValue()) {
                                    AloudataMetricQueryResponse.ColumnValue cv = new AloudataMetricQueryResponse.ColumnValue();
                                    cv.setValue(item.get("value"));
                                    cv.setFlag(item.get("flag"));
                                    cv.setCount(item.get("count") instanceof Number
                                            ? ((Number) item.get("count")).intValue() : null);
                                    values.add(cv);
                                }
                            }
                            typedColumns.put(entry.getKey(), values);
                        }
                        metricData.setColumns(typedColumns);
                    }

                    // 解析行式 rows（可能为 null）
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> rows = (List<Map<String, Object>>) table.get("rows");
                    metricData.setRows(rows);

                    Object totalObj = data.get("total");
                    if (totalObj instanceof Number) {
                        metricData.setTotal(((Number) totalObj).longValue());
                    }
                    result.setData(metricData);
                }
            }
            return result;
        } catch (Exception e) {
            log.error("执行 Aloudata 指标查询失败: {}", e.getMessage());
            throw new RuntimeException("执行指标查询失败: " + e.getMessage(), e);
        }
    }

    /**
     * 查询指标语义信息列表（含同义词、业务口径、可用维度）
     * <p>
     * 通过 metrics_list 接口获取指标列表，再通过 metric_detail 接口获取每个指标的详情（含同义词），
     * 最后通过 metric_available_dimensions 接口获取每个指标的可用维度。
     * 如果 metric_detail 接口不支持同义词字段，则降级为仅使用 metrics_list 的基础信息。
     */
    @Override
    public List<AloudataMetricSemanticDTO> listMetricSemantics(Long datasourceId) {
        DatasourceEntity entity = datasourceMapper.selectById(datasourceId);
        if (entity == null) {
            throw new RuntimeException("数据源不存在: " + datasourceId);
        }
        AloudataConfigDTO config = configHelper.parseConfig(entity);

        try {
            // 1. 获取指标列表（分页获取全量）
            List<Map<String, Object>> allMetrics = new ArrayList<>();
            int pageNumber = 1;
            int pageSize = 100;
            boolean hasNext = true;

            while (hasNext) {
                Map<String, Object> params = endpointService.buildParamsFromConfigAndInput(
                        METRICS_LIST_ENDPOINT, config, metricsListInput(pageNumber, pageSize));

                ResponseEntity<Map> response = apiClient.callWithParams(METRICS_LIST_ENDPOINT, config, params);
                if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                    break;
                }
                Map<String, Object> responseBody = response.getBody();
                Boolean success = (Boolean) responseBody.get("success");
                if (!Boolean.TRUE.equals(success)) {
                    break;
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) responseBody.get("data");
                if (data == null) {
                    break;
                }
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> pageData = (List<Map<String, Object>>) data.get("data");
                if (pageData != null) {
                    allMetrics.addAll(pageData);
                }
                // 判断是否还有下一页
                Object hasNextObj = data.get("hasNext");
                hasNext = Boolean.TRUE.equals(hasNextObj);
                pageNumber++;
            }

            // 2. 转换为语义 DTO
            List<AloudataMetricSemanticDTO> result = new ArrayList<>();
            for (Map<String, Object> metric : allMetrics) {
                AloudataMetricSemanticDTO dto = new AloudataMetricSemanticDTO();
                endpointService.mapResponseToDto(METRICS_LIST_ENDPOINT, metric, dto, METRIC_FIELD_ALIASES);

                // 3. 尝试获取指标详情（含同义词）
                try {
                    Map<String, Object> detailParams = endpointService.buildParamsFromConfigAndInput(
                            METRIC_DETAIL_ENDPOINT, config, metricDetailInput(dto.getMetricName()));

                    ResponseEntity<Map> detailResponse = apiClient.callWithParams(METRIC_DETAIL_ENDPOINT, config, detailParams);
                    if (detailResponse.getStatusCode().is2xxSuccessful() && detailResponse.getBody() != null) {
                        Map<String, Object> detailBody = detailResponse.getBody();
                        if (Boolean.TRUE.equals(detailBody.get("success")) && detailBody.get("data") != null) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> detailData = (Map<String, Object>) detailBody.get("data");
                            // 提取同义词
                            Object synonymsObj = detailData.get("synonyms");
                            if (synonymsObj instanceof List) {
                                @SuppressWarnings("unchecked")
                                List<String> synonyms = (List<String>) synonymsObj;
                                dto.setSynonyms(synonyms);
                            }
                            // 提取类目名称
                            String categoryName = (String) detailData.get("metricCategoryName");
                            if (categoryName != null) {
                                dto.setMetricCategoryName(categoryName);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.debug("获取指标 [{}] 详情失败，跳过同义词: {}", dto.getMetricName(), e.getMessage());
                }

                // 4. 获取可用维度
                try {
                    Map<String, Object> dimParams = endpointService.buildParamsFromConfigAndInput(
                            METRIC_AVAILABLE_DIMENSIONS_ENDPOINT, config, metricNamesInput(dto.getMetricName()));

                    ResponseEntity<Map> dimResponse = apiClient.callWithParams(METRIC_AVAILABLE_DIMENSIONS_ENDPOINT, config, dimParams);
                    if (dimResponse.getStatusCode().is2xxSuccessful() && dimResponse.getBody() != null) {
                        Map<String, Object> dimBody = dimResponse.getBody();
                        if (Boolean.TRUE.equals(dimBody.get("success")) && dimBody.get("data") != null) {
                            @SuppressWarnings("unchecked")
                            List<Map<String, Object>> dims = (List<Map<String, Object>>) dimBody.get("data");
                            List<String> dimNames = new ArrayList<>();
                            if (dims != null) {
                                for (Map<String, Object> dim : dims) {
                                    String dimName = (String) dim.get("dimName");
                                    if (dimName != null) {
                                        dimNames.add(dimName);
                                    }
                                }
                            }
                            dto.setAvailableDimensions(dimNames);
                        }
                    }
                } catch (Exception e) {
                    log.debug("获取指标 [{}] 可用维度失败: {}", dto.getMetricName(), e.getMessage());
                }

                result.add(dto);
            }

            log.info("从 Aloudata 同步指标语义信息完成，共 {} 个指标", result.size());
            return result;
        } catch (Exception e) {
            log.error("查询 Aloudata 指标语义信息失败: {}", e.getMessage());
            throw new RuntimeException("查询指标语义信息失败: " + e.getMessage(), e);
        }
    }

    /**
     * 查询维度语义信息列表（含同义词、描述、数据类型）
     * <p>
     * 通过 dimensions_list 接口获取维度列表，再通过 dimension_detail 接口获取每个维度的详情（含同义词）。
     */
    @Override
    public List<AloudataDimensionSemanticDTO> listDimensionSemantics(Long datasourceId) {
        DatasourceEntity entity = datasourceMapper.selectById(datasourceId);
        if (entity == null) {
            throw new RuntimeException("数据源不存在: " + datasourceId);
        }
        AloudataConfigDTO config = configHelper.parseConfig(entity);

        try {
            // 1. 获取维度列表（分页获取全量）
            List<Map<String, Object>> allDimensions = new ArrayList<>();
            int pageNumber = 1;
            int pageSize = 100;
            boolean hasNext = true;

            while (hasNext) {
                Map<String, Object> params = endpointService.buildParamsFromConfigAndInput(
                        DIMENSIONS_LIST_ENDPOINT, config, pageInput(pageNumber, pageSize));

                ResponseEntity<Map> response = apiClient.callWithParams(DIMENSIONS_LIST_ENDPOINT, config, params);
                if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                    break;
                }
                Map<String, Object> responseBody = response.getBody();
                Boolean success = (Boolean) responseBody.get("success");
                if (!Boolean.TRUE.equals(success)) {
                    break;
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) responseBody.get("data");
                if (data == null) {
                    break;
                }
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> pageData = (List<Map<String, Object>>) data.get("data");
                if (pageData != null) {
                    allDimensions.addAll(pageData);
                }
                Object hasNextObj = data.get("hasNext");
                hasNext = Boolean.TRUE.equals(hasNextObj);
                pageNumber++;
            }

            // 2. 转换为语义 DTO
            List<AloudataDimensionSemanticDTO> result = new ArrayList<>();
            for (Map<String, Object> dim : allDimensions) {
                AloudataDimensionSemanticDTO dto = new AloudataDimensionSemanticDTO();
                endpointService.mapResponseToDto(DIMENSIONS_LIST_ENDPOINT, dim, dto, DIMENSION_FIELD_ALIASES);

                // 3. 尝试获取维度详情（含同义词）
                try {
                    Map<String, Object> detailParams = endpointService.buildParamsFromConfigAndInput(
                            DIMENSION_DETAIL_ENDPOINT, config, dimensionDetailInput(dto.getDimName()));

                    ResponseEntity<Map> detailResponse = apiClient.callWithParams(DIMENSION_DETAIL_ENDPOINT, config, detailParams);
                    if (detailResponse.getStatusCode().is2xxSuccessful() && detailResponse.getBody() != null) {
                        Map<String, Object> detailBody = detailResponse.getBody();
                        if (Boolean.TRUE.equals(detailBody.get("success")) && detailBody.get("data") != null) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> detailData = (Map<String, Object>) detailBody.get("data");
                            // 提取同义词
                            Object synonymsObj = detailData.get("synonyms");
                            if (synonymsObj instanceof List) {
                                @SuppressWarnings("unchecked")
                                List<String> synonyms = (List<String>) synonymsObj;
                                dto.setSynonyms(synonyms);
                            }
                            // 提取配置信息
                            @SuppressWarnings("unchecked")
                            Map<String, Object> configMap = (Map<String, Object>) detailData.get("config");
                            if (configMap != null) {
                                dto.setConfigType((String) configMap.get("type"));
                                dto.setConfigValue((String) configMap.get("value"));
                            }
                            dto.setDatasetName((String) detailData.get("datasetName"));
                        }
                    }
                } catch (Exception e) {
                    log.debug("获取维度 [{}] 详情失败，跳过同义词: {}", dto.getDimName(), e.getMessage());
                }

                result.add(dto);
            }

            log.info("从 Aloudata 同步维度语义信息完成，共 {} 个维度", result.size());
            return result;
        } catch (Exception e) {
            log.error("查询 Aloudata 维度语义信息失败: {}", e.getMessage());
            throw new RuntimeException("查询维度语义信息失败: " + e.getMessage(), e);
        }
    }

    private Object metricsListInput(Integer pageNumber, Integer pageSize) {
        return new Object() {
            public List<String> statusFilters() {
                return List.of("PUBLISHED");
            }

            public Integer pageNumber() {
                return pageNumber;
            }

            public Integer pageSize() {
                return pageSize;
            }
        };
    }

    private Object pageInput(Integer pageNumber, Integer pageSize) {
        return new Object() {
            public Integer pageNumber() {
                return pageNumber;
            }

            public Integer pageSize() {
                return pageSize;
            }
        };
    }

    private Object metricDetailInput(String metricName) {
        return new Object() {
            public String metricName() {
                return metricName;
            }
        };
    }

    private Object metricNamesInput(String metricName) {
        return new Object() {
            public List<String> metricNames() {
                return List.of(metricName);
            }
        };
    }

    private Object dimensionDetailInput(String dimName) {
        return new Object() {
            public String dimName() {
                return dimName;
            }
        };
    }
}
