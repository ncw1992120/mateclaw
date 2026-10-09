package vip.mate.dataagent.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import vip.mate.dataagent.auth.service.WorkspaceGuard;
import vip.mate.dataagent.constants.DataAgentConstants;
import vip.mate.dataagent.dto.InsightDashboardSaveAsTemplateRequest;
import vip.mate.dataagent.dto.InsightDashboardVO;
import vip.mate.dataagent.model.InsightDashboardEntity;
import vip.mate.dataagent.repository.InsightDashboardMapper;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Test
    void copyDashboardResetsVisibilityAndClearsTemplateMeta() throws Exception {
        InsightDashboardMapper mapper = mock(InsightDashboardMapper.class);
        WorkspaceGuard wg = mock(WorkspaceGuard.class);
        ObjectMapper objectMapper = new ObjectMapper();

        InsightDashboardEntity source = new InsightDashboardEntity();
        source.setId(1L);
        source.setWorkspaceId(7L);
        source.setOwnerId(999L);
        source.setVisibility(DataAgentConstants.INSIGHT_DASHBOARD_VISIBILITY_TEMPLATE);
        source.setTemplateMeta("{\"tags\":[\"策略\"]}");
        source.setName("策略解读");
        source.setSchemaJson("{\"version\":\"1.0\",\"components\":[]}");
        source.setStatus(DataAgentConstants.INSIGHT_DASHBOARD_STATUS_PUBLISHED);

        when(mapper.selectById(1L)).thenReturn(source);
        when(wg.currentUserId()).thenReturn(42L);
        when(wg.currentWorkspaceId()).thenReturn(7L);
        when(wg.currentUserNickname()).thenReturn("tester");
        doAnswer(inv -> {
            InsightDashboardEntity e = inv.getArgument(0);
            e.setId(2L);
            return null;
        }).when(mapper).insert(any(InsightDashboardEntity.class));

        InsightDashboardServiceImpl service =
                new InsightDashboardServiceImpl(mapper, wg, null, null, null, objectMapper, null);
        InsightDashboardVO vo = service.copyDashboard(1L);

        assertEquals("private", vo.getVisibility(), "副本必须是私有，不能再带模板可见性");
        // 模板展示元数据不应被继承；仅保留来源标记用于「替换示例数据」提示
        JsonNode meta = objectMapper.readTree(vo.getTemplateMeta());
        assertNull(meta.get("tags"), "副本不应继承模板标签");
        assertNull(meta.get("category"), "副本不应继承模板分类");
        assertEquals(1L, meta.get("sourceTemplateId").asLong());
        assertEquals("策略解读", meta.get("sourceTemplateName").asText());
        assertEquals(42L, vo.getOwnerId());
        assertEquals(7L, vo.getWorkspaceId());
        assertEquals(DataAgentConstants.INSIGHT_DASHBOARD_STATUS_DRAFT, vo.getStatus());
        assertEquals(2L, vo.getId());
        verify(mapper).insert(any(InsightDashboardEntity.class));
    }

    @Test
    void listDashboardsFiltersByVisibility() {
        InsightDashboardMapper mapper = mock(InsightDashboardMapper.class);
        WorkspaceGuard wg = mock(WorkspaceGuard.class);
        when(wg.currentWorkspaceId()).thenReturn(7L);

        InsightDashboardEntity priv = new InsightDashboardEntity();
        priv.setId(1L); priv.setWorkspaceId(7L); priv.setVisibility("private");
        InsightDashboardEntity tpl = new InsightDashboardEntity();
        tpl.setId(2L); tpl.setWorkspaceId(7L); tpl.setVisibility("template");
        InsightDashboardEntity off = new InsightDashboardEntity();
        off.setId(3L); off.setWorkspaceId(7L); off.setVisibility("official");
        InsightDashboardEntity nullVis = new InsightDashboardEntity();
        nullVis.setId(4L); nullVis.setWorkspaceId(7L); nullVis.setVisibility(null);

        when(mapper.selectList(any())).thenReturn(List.of(priv, tpl, off, nullVis));

        InsightDashboardServiceImpl service =
                new InsightDashboardServiceImpl(mapper, wg, null, null, null, new ObjectMapper(), null);

        assertEquals(2, service.listDashboards("template,official").size());
        assertEquals(4, service.listDashboards(null).size());
        // null 可见性按 private 处理，故 "private" 过滤命中 priv + nullVis
        assertEquals(2, service.listDashboards("private").size());
    }

    @Test
    void filterByVisibilityPureFunction() {
        InsightDashboardEntity a = new InsightDashboardEntity(); a.setVisibility("private");
        InsightDashboardEntity b = new InsightDashboardEntity(); b.setVisibility("template");
        InsightDashboardEntity c = new InsightDashboardEntity(); c.setVisibility(null);
        List<InsightDashboardEntity> all = List.of(a, b, c);

        assertEquals(2, InsightDashboardServiceImpl.filterByVisibility(all, "private").size());
        assertEquals(3, InsightDashboardServiceImpl.filterByVisibility(all, null).size());
        assertEquals(1, InsightDashboardServiceImpl.filterByVisibility(all, "template,official").size());
    }

    @Test
    void saveAsTemplateCreatesTemplateWithMeta() throws Exception {
        InsightDashboardMapper mapper = mock(InsightDashboardMapper.class);
        WorkspaceGuard wg = mock(WorkspaceGuard.class);
        ObjectMapper objectMapper = new ObjectMapper();

        InsightDashboardEntity source = new InsightDashboardEntity();
        source.setId(1L);
        source.setWorkspaceId(7L);
        source.setOwnerId(42L);
        source.setVisibility(DataAgentConstants.INSIGHT_DASHBOARD_VISIBILITY_PRIVATE);
        source.setName("策略解读");
        source.setDescription("原描述");
        source.setSchemaJson("{\"version\":\"1.0\",\"components\":[]}");
        source.setStatus(DataAgentConstants.INSIGHT_DASHBOARD_STATUS_PUBLISHED);

        when(mapper.selectById(1L)).thenReturn(source);
        when(wg.currentUserId()).thenReturn(42L);
        when(wg.currentWorkspaceId()).thenReturn(7L);
        when(wg.currentUserNickname()).thenReturn("tester");
        doAnswer(inv -> {
            InsightDashboardEntity e = inv.getArgument(0);
            e.setId(3L);
            return null;
        }).when(mapper).insert(any(InsightDashboardEntity.class));

        InsightDashboardSaveAsTemplateRequest req = new InsightDashboardSaveAsTemplateRequest();
        req.setName("策略解读 模板");
        req.setTags(List.of("策略"));
        req.setCategory("投资分析");

        InsightDashboardServiceImpl service =
                new InsightDashboardServiceImpl(mapper, wg, null, null, null, objectMapper, null);
        InsightDashboardVO vo = service.saveAsTemplate(1L, req);

        assertEquals(DataAgentConstants.INSIGHT_DASHBOARD_VISIBILITY_TEMPLATE, vo.getVisibility());
        assertEquals(42L, vo.getOwnerId());
        assertEquals(7L, vo.getWorkspaceId());
        assertEquals(DataAgentConstants.INSIGHT_DASHBOARD_STATUS_DRAFT, vo.getStatus());
        assertEquals("策略解读 模板", vo.getName());
        assertEquals(3L, vo.getId());

        JsonNode meta = objectMapper.readTree(vo.getTemplateMeta());
        assertEquals(1L, meta.get("sourceDashboardId").asLong());
        assertEquals("tester", meta.get("authorName").asText());
        assertEquals("投资分析", meta.get("category").asText());
        assertEquals(0, meta.get("usageCount").asInt());
        assertFalse(meta.get("isOfficial").asBoolean());
        verify(mapper).insert(any(InsightDashboardEntity.class));
    }

    @Test
    void copyDashboardOfNonTemplateKeepsTemplateMetaEmpty() {
        InsightDashboardMapper mapper = mock(InsightDashboardMapper.class);
        WorkspaceGuard wg = mock(WorkspaceGuard.class);

        InsightDashboardEntity source = new InsightDashboardEntity();
        source.setId(5L);
        source.setWorkspaceId(7L);
        source.setOwnerId(42L);
        source.setVisibility(DataAgentConstants.INSIGHT_DASHBOARD_VISIBILITY_PRIVATE);
        source.setName("普通看板");
        source.setSchemaJson("{\"version\":\"1.0\",\"components\":[]}");

        when(mapper.selectById(5L)).thenReturn(source);
        when(wg.currentUserId()).thenReturn(42L);
        when(wg.currentWorkspaceId()).thenReturn(7L);
        when(wg.currentUserNickname()).thenReturn("tester");
        doAnswer(inv -> {
            InsightDashboardEntity e = inv.getArgument(0);
            e.setId(6L);
            return null;
        }).when(mapper).insert(any(InsightDashboardEntity.class));

        InsightDashboardServiceImpl service =
                new InsightDashboardServiceImpl(mapper, wg, null, null, null, new ObjectMapper(), null);
        InsightDashboardVO vo = service.copyDashboard(5L);

        assertEquals("private", vo.getVisibility());
        assertNull(vo.getTemplateMeta(), "非模板复制不应产生来源标记");
    }
}
