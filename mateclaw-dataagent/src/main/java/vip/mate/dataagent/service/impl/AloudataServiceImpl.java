package vip.mate.dataagent.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import vip.mate.dataagent.aloudata.AloudataApiClient;
import vip.mate.dataagent.aloudata.AloudataConfigHelper;
import vip.mate.dataagent.dto.*;
import vip.mate.dataagent.model.DatasourceEntity;
import vip.mate.dataagent.repository.DatasourceMapper;
import vip.mate.dataagent.service.AloudataService;

import java.util.*;

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

    /**
     * 测试 Aloudata 连接
     * <p>
     * 通过调用 metrics_list 接口（pageSize=1）验证连接和认证是否正常
     */
    @Override
    public boolean testConnection(AloudataConfigDTO config) {
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("tenant-id", config.getTenantId());
            params.put("auth-type", config.getAuthType() != null ? config.getAuthType() : "UID");
            params.put("auth-value", config.getAuthValue());
            params.put("pageSize", 1);

            ResponseEntity<Map> response = apiClient.callWithParams("metrics_list", config, params);

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
        DatasourceEntity entity = datasourceMapper.selectById(datasourceId);
        if (entity == null) {
            throw new RuntimeException("数据源不存在: " + datasourceId);
        }
        AloudataConfigDTO config = configHelper.parseConfig(entity);

        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("tenant-id", config.getTenantId());
            params.put("auth-type", config.getAuthType() != null ? config.getAuthType() : "UID");
            params.put("auth-value", config.getAuthValue());

            ResponseEntity<Map> response = apiClient.callWithParams("metrics_list", config, params);

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
                            vo.setMetricId(String.valueOf(metric.get("id")));
                            vo.setMetricName((String) metric.get("metricName"));
                            vo.setMetricDisplayName((String) metric.get("metricDisplayName"));
                            vo.setType((String) metric.get("type"));
                            vo.setBusinessCaliber((String) metric.get("businessCaliber"));
                            vo.setOwner((String) metric.get("owner"));
                            vo.setBusinessOwner((String) metric.get("businessOwner"));
                            vo.setMetricCategoryId(String.valueOf(metric.get("metricCategoryId")));
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
     * 执行指标数据查询
     */
    @Override
    public AloudataMetricQueryResponse queryMetrics(Long datasourceId, AloudataMetricQueryRequest request) {
        DatasourceEntity entity = datasourceMapper.selectById(datasourceId);
        if (entity == null) {
            throw new RuntimeException("数据源不存在: " + datasourceId);
        }
        AloudataConfigDTO config = configHelper.parseConfig(entity);

        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("tenant-id", config.getTenantId());
            params.put("auth-type", config.getAuthType() != null ? config.getAuthType() : "UID");
            params.put("auth-value", config.getAuthValue());
            params.put("metrics", request.getMetrics());
            params.put("dimensions", request.getDimensions());
            if (request.getFilters() != null) {
                params.put("filters", request.getFilters());
            }
            if (request.getOrderBy() != null) {
                params.put("orderBy", request.getOrderBy());
            }
            if (request.getLimit() != null) {
                params.put("limit", request.getLimit());
            }
            if (request.getOffset() != null) {
                params.put("offset", request.getOffset());
            }

            ResponseEntity<Map> response = apiClient.callWithParams("metrics_query", config, params);

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
                    AloudataMetricQueryResponse.MetricData metricData = new AloudataMetricQueryResponse.MetricData();
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> columns = (List<Map<String, Object>>) data.get("columns");
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> rows = (List<Map<String, Object>>) data.get("rows");
                    metricData.setColumns(columns);
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
                Map<String, Object> params = new LinkedHashMap<>();
                params.put("tenant-id", config.getTenantId());
                params.put("auth-type", config.getAuthType() != null ? config.getAuthType() : "UID");
                params.put("auth-value", config.getAuthValue());
                params.put("statusFilters", List.of("PUBLISHED"));
                params.put("pageNumber", pageNumber);
                params.put("pageSize", pageSize);

                ResponseEntity<Map> response = apiClient.callWithParams("metrics_list", config, params);
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
                dto.setMetricId(String.valueOf(metric.get("id")));
                dto.setMetricName((String) metric.get("metricName"));
                dto.setMetricDisplayName((String) metric.get("metricDisplayName"));
                dto.setType((String) metric.get("type"));
                dto.setBusinessCaliber((String) metric.get("businessCaliber"));
                dto.setOwner((String) metric.get("owner"));
                dto.setBusinessOwner((String) metric.get("businessOwner"));
                dto.setMetricCategoryId(String.valueOf(metric.get("metricCategoryId")));
                dto.setStatus((String) metric.get("status"));
                dto.setUnit((String) metric.get("unit"));

                // 3. 尝试获取指标详情（含同义词）
                try {
                    Map<String, Object> detailParams = new LinkedHashMap<>();
                    detailParams.put("tenant-id", config.getTenantId());
                    detailParams.put("auth-type", config.getAuthType() != null ? config.getAuthType() : "UID");
                    detailParams.put("auth-value", config.getAuthValue());
                    detailParams.put("metricName", dto.getMetricName());

                    ResponseEntity<Map> detailResponse = apiClient.callWithParams("metric_detail", config, detailParams);
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
                    Map<String, Object> dimParams = new LinkedHashMap<>();
                    dimParams.put("tenant-id", config.getTenantId());
                    dimParams.put("auth-type", config.getAuthType() != null ? config.getAuthType() : "UID");
                    dimParams.put("auth-value", config.getAuthValue());
                    dimParams.put("metricNames", List.of(dto.getMetricName()));

                    ResponseEntity<Map> dimResponse = apiClient.callWithParams("metric_available_dimensions", config, dimParams);
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
                Map<String, Object> params = new LinkedHashMap<>();
                params.put("tenant-id", config.getTenantId());
                params.put("auth-type", config.getAuthType() != null ? config.getAuthType() : "UID");
                params.put("auth-value", config.getAuthValue());
                params.put("pageNumber", pageNumber);
                params.put("pageSize", pageSize);

                ResponseEntity<Map> response = apiClient.callWithParams("dimensions_list", config, params);
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
                dto.setDimensionId(String.valueOf(dim.get("id")));
                dto.setDimName((String) dim.get("dimName"));
                dto.setDimDisplayName((String) dim.get("dimDisplayName"));
                dto.setOriginDataType((String) dim.get("originDataType"));
                dto.setDimDescription((String) dim.get("dimDescription"));

                // 3. 尝试获取维度详情（含同义词）
                try {
                    Map<String, Object> detailParams = new LinkedHashMap<>();
                    detailParams.put("tenant-id", config.getTenantId());
                    detailParams.put("auth-type", config.getAuthType() != null ? config.getAuthType() : "UID");
                    detailParams.put("auth-value", config.getAuthValue());
                    detailParams.put("dimName", dto.getDimName());

                    ResponseEntity<Map> detailResponse = apiClient.callWithParams("dimension_detail", config, detailParams);
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
}
