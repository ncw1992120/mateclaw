package vip.mate.dataagent.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InsightDashboardServiceImplTest {

    @Test
    void optimisticUpdateRequiresTheReadVersionWhenProvided() {
        assertTrue(InsightDashboardServiceImpl.matchesExpectedUpdateTime(null, "2026-09-22T10:00"));
        assertTrue(InsightDashboardServiceImpl.matchesExpectedUpdateTime("2026-09-22T10:00", "2026-09-22T10:00"));
        assertFalse(InsightDashboardServiceImpl.matchesExpectedUpdateTime("2026-09-22T10:00", "2026-09-22T10:01"));
        assertFalse(InsightDashboardServiceImpl.matchesExpectedUpdateTime("2026-09-22T10:00", null));
    }

    @Test
    void copyRemapPreservesNestedCombinationCardConfiguration() throws Exception {
        String source = """
                {
                  "version": "1.1",
                  "pages": [{
                    "id": "page-source",
                    "components": [{
                      "id": "combination-source",
                      "type": "combination",
                      "title": "策略解读",
                      "titleBarStyle": {"visible": true, "background": "#fff"},
                      "visualStyle": {"background": "#f7f8fa", "radius": 12},
                      "containerConfig": {
                        "layoutMode": "free",
                        "activeTab": "tab-source",
                        "tabs": [{
                          "id": "tab-source",
                          "title": "指标视角",
                          "children": [{
                            "id": "child-source",
                            "type": "combination",
                            "title": "子组合卡片",
                            "config": {"customSetting": {"enabled": true}},
                            "containerConfig": {
                              "layoutMode": "grid",
                              "tabs": [{"id": "nested-tab-source", "title": "子页签", "children": [{
                                "id": "nested-child-source",
                                "type": "table",
                                "title": "策略贡献",
                                "dataSource": {"datasourceId": "1", "dimensions": ["metric_name"]},
                                "layout": {"x": 8, "y": 8, "col": 6, "h": 180}
                              }]}]
                            },
                            "layout": {"x": 0, "y": 0, "col": 12, "h": 320}
                          }]
                        }]
                      }
                    }]
                  }]
                }
                """;

        ObjectMapper mapper = new ObjectMapper();
        InsightDashboardServiceImpl service = new InsightDashboardServiceImpl(
                null, null, null, null, null, mapper, null);
        Method remap = InsightDashboardServiceImpl.class.getDeclaredMethod("remapSchemaIds", String.class);
        remap.setAccessible(true);

        JsonNode copied = mapper.readTree((String) remap.invoke(service, source));
        JsonNode copiedContainer = copied.at("/pages/0/components/0");
        JsonNode copiedChild = copied.at("/pages/0/components/0/containerConfig/tabs/0/children/0");
        JsonNode copiedNestedChild = copied.at("/pages/0/components/0/containerConfig/tabs/0/children/0/containerConfig/tabs/0/children/0");

        assertNotNull(copied.at("/pages/0/components/0/titleBarStyle/visible"));
        assertEquals("#f7f8fa", copiedContainer.at("/visualStyle/background").asText());
        assertEquals(true, copiedChild.at("/config/customSetting/enabled").asBoolean());
        assertEquals("metric_name", copiedNestedChild.at("/dataSource/dimensions/0").asText());
        assertNotEquals("combination-source", copiedContainer.get("id").asText());
        assertNotEquals("child-source", copiedChild.get("id").asText());
        assertNotEquals("nested-child-source", copiedNestedChild.get("id").asText());
        String activeTab = copiedContainer.at("/containerConfig/activeTab").asText();
        assertNotEquals("tab-source", activeTab);
        assertEquals(activeTab, copiedContainer.at("/containerConfig/tabs/0/id").asText());
    }
}
