package vip.mate.dataagent.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InsightDashboardSchemaDTOTest {

    @Test
    void dataSourceCarriesJdbcQueryBindingFields() {
        InsightDashboardSchemaDTO.DataSource dataSource = new InsightDashboardSchemaDTO.DataSource();
        dataSource.setDatasourceId("42");
        dataSource.setSourceType("JDBC");
        dataSource.setSql("select status, count(*) as total from orders group by status");

        assertEquals("JDBC", dataSource.getSourceType());
        assertEquals("select status, count(*) as total from orders group by status", dataSource.getSql());
    }

    @Test
    void schemaCarriesDatasetComposerFieldsWhenDashboardIsRewritten() {
        InsightDashboardSchemaDTO schema = new InsightDashboardSchemaDTO();
        schema.setDatasetInputs(new java.util.ArrayList<>());
        schema.setScriptFilterBindings(new java.util.ArrayList<>());
        schema.setScript("result = []");

        assertNotNull(schema.getDatasetInputs());
        assertNotNull(schema.getScriptFilterBindings());
        assertEquals("result = []", schema.getScript());
    }

    @Test
    void componentPipelineRoundTripsManagedScriptAndOptionalFilterConditions() throws Exception {
        String json = """
                {
                  "version": "1.1",
                  "pages": [{
                    "id": "page_0",
                    "name": "首页",
                    "components": [{
                      "id": "comp_chart_1",
                      "type": "chart",
                      "title": "趋势",
                      "position": {"x": 0, "y": 0, "w": 12, "h": 8},
                      "config": {
                        "datasetPipeline": {
                          "datasetInputs": [],
                          "scriptFilterBindings": [{
                            "filterComponentId": "time_filter",
                            "conditions": [{
                              "inputName": "orders",
                              "field": "metric_time",
                              "operator": "gte",
                              "parameterNames": ["startDate"],
                              "required": false
                            }]
                          }],
                          "systemScript": {
                            "mode": "managed",
                            "generatedCode": "orders = datasets.read(\\\"orders\\\")",
                            "managedCode": "orders = custom_read()",
                            "generatedFingerprint": "fp-1",
                            "userCode": "result = orders"
                          }
                        }
                      }
                    }]
                  }]
                }
                """;

        ObjectMapper mapper = new ObjectMapper();
        InsightDashboardSchemaDTO schema = mapper.readValue(json, InsightDashboardSchemaDTO.class);
        String written = mapper.writeValueAsString(schema);

        assertTrue(written.contains("\"mode\":\"managed\""));
        assertTrue(written.contains("\"parameterNames\":[\"startDate\"]"));
        assertTrue(written.contains("\"managedCode\":\"orders = custom_read()\""));
    }

    /**
     * KPI 指标分组防丢测试：复制组件 / AI 修改会把 schema_json 反序列化为 DTO 再序列化，
     * kpiMetrics 必须在 roundtrip 后逐字段保留（含嵌套 styles 与小数布局坐标）。
     */
    @Test
    void kpiMetricsSurviveSchemaJsonRoundtrip() throws Exception {
        String json = """
                {
                  "version": "1.1",
                  "pages": [{
                    "id": "page_0",
                    "name": "首页",
                    "order": 0,
                    "components": [{
                      "id": "comp_kpi_1",
                      "type": "kpi",
                      "title": "销售概览",
                      "position": {"x": 0, "y": 0, "w": 12, "h": 8},
                      "kpiMetrics": [{
                        "fieldKey": "gmv",
                        "displayName": "销售额",
                        "unit": "万元",
                        "helperText": "含税口径",
                        "visible": true,
                        "x": 12.5,
                        "y": 8,
                        "w": 284,
                        "h": 88,
                        "styles": {
                          "name": {"size": 14, "family": "pingfang", "color": "#646a73", "bold": "normal"},
                          "value": {"size": 28, "family": "system", "color": "#1f2329", "bold": "bold"},
                          "unit": {"size": 15, "family": "system", "color": "#646a73", "bold": "normal"},
                          "helper": {"size": 12, "family": "system", "color": "#8f959e", "bold": "normal"}
                        }
                      }]
                    }]
                  }]
                }
                """;

        ObjectMapper mapper = new ObjectMapper();
        InsightDashboardSchemaDTO schema = mapper.readValue(json, InsightDashboardSchemaDTO.class);

        InsightDashboardSchemaDTO.Component comp = schema.getPages().get(0).getComponents().get(0);
        assertNotNull(comp.getKpiMetrics(), "kpiMetrics 反序列化后不应为 null（未声明字段会被 roundtrip 丢弃）");
        assertEquals(1, comp.getKpiMetrics().size());

        InsightDashboardSchemaDTO.KpiMetric metric = comp.getKpiMetrics().get(0);
        assertEquals("gmv", metric.getFieldKey());
        assertEquals("销售额", metric.getDisplayName());
        assertEquals("万元", metric.getUnit());
        assertEquals("含税口径", metric.getHelperText());
        assertTrue(metric.getVisible());
        assertEquals(12.5, metric.getX());
        assertEquals(88.0, metric.getH());
        assertEquals("pingfang", metric.getStyles().getName().getFamily());
        assertEquals("bold", metric.getStyles().getValue().getBold());
        assertEquals(28.0, metric.getStyles().getValue().getSize());

        // 序列化回 JSON：字段仍完整保留（复制/AI 修改链路的关键）
        JsonNode roundtrip = mapper.readTree(mapper.writeValueAsString(schema));
        JsonNode back = roundtrip.get("pages").get(0).get("components").get(0).get("kpiMetrics").get(0);
        assertEquals("gmv", back.get("fieldKey").asText());
        assertEquals("销售额", back.get("displayName").asText());
        assertEquals(12.5, back.get("x").asDouble());
        assertEquals("bold", back.get("styles").get("value").get("bold").asText());
        assertEquals("#646a73", back.get("styles").get("name").get("color").asText());
    }

    @Test
    void preservesComponentDatasetPipelineAndUnknownConfigFieldsAcrossRoundTrip() throws Exception {
        String json = """
                {"version":"1.1","pages":[{"id":"page-1","components":[{
                  "id":"kpi-1","type":"kpi","title":"订单数",
                  "position":{"x":0,"y":0,"w":4,"h":3},
                  "config":{"datasetPipeline":{"datasetInputs":[{"datasetId":"orders","inputName":"orders"}]},"extensionField":{"keep":true}}
                }]}]}
                """;
        ObjectMapper mapper = new ObjectMapper();
        InsightDashboardSchemaDTO schema = mapper.readValue(json, InsightDashboardSchemaDTO.class);
        JsonNode config = mapper.readTree(mapper.writeValueAsString(schema))
                .at("/pages/0/components/0/config");

        assertEquals("orders", config.at("/datasetPipeline/datasetInputs/0/inputName").asText());
        assertTrue(config.at("/extensionField/keep").asBoolean());
    }

    @Test
    void themeAndMetricVisualSurviveFixtureRoundTrip() throws Exception {
        String json = java.nio.file.Files.readString(java.nio.file.Path.of("../docs/plans/fixtures/insight-dashboard-theme-schema.json"));
        ObjectMapper mapper = new ObjectMapper();
        InsightDashboardSchemaDTO schema = mapper.readValue(json, InsightDashboardSchemaDTO.class);
        JsonNode written = mapper.readTree(mapper.writeValueAsString(schema));

        assertEquals("teal", written.at("/theme/presetId").asText());
        assertEquals("chart-bar", written.at("/pages/0/components/0/kpiMetrics/0/visual/iconKey").asText());
        assertEquals("custom", written.at("/pages/0/components/0/kpiMetrics/0/styles/value/colorMode").asText());
        assertEquals("#102030", written.at("/theme/overrides/metricPalette/0").asText());
        assertEquals("large", written.at("/pages/0/components/0/config/density").asText());
    }

    @Test
    void titleIconStyleSurvivesSchemaJsonRoundTrip() throws Exception {
        String json = """
                {"version":"1.1","pages":[{"id":"page-1","components":[{
                  "id":"kpi-1","type":"kpi","title":"订单数",
                  "position":{"x":0,"y":0,"w":4,"h":3},
                  "titleIconStyle":{"iconKey":"trend-charts","strokeWidth":2.5,"colorMode":"custom","color":"#8C4A2F"}
                }]}]}
                """;

        ObjectMapper mapper = new ObjectMapper();
        InsightDashboardSchemaDTO schema = mapper.readValue(json, InsightDashboardSchemaDTO.class);
        JsonNode roundTrip = mapper.readTree(mapper.writeValueAsString(schema));
        JsonNode style = roundTrip.at("/pages/0/components/0/titleIconStyle");

        assertEquals("trend-charts", style.get("iconKey").asText());
        assertEquals(2.5, style.get("strokeWidth").asDouble());
        assertEquals("custom", style.get("colorMode").asText());
        assertEquals("#8C4A2F", style.get("color").asText());
    }
}
