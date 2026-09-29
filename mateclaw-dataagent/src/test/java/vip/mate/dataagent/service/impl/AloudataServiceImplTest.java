package vip.mate.dataagent.service.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.ResponseEntity;
import vip.mate.dataagent.aloudata.AloudataApiClient;
import vip.mate.dataagent.aloudata.AloudataApiProperties;
import vip.mate.dataagent.aloudata.AloudataConfigHelper;
import vip.mate.dataagent.aloudata.AloudataEndpointService;
import vip.mate.dataagent.auth.context.UserContext;
import vip.mate.dataagent.auth.context.UserContextHolder;
import vip.mate.dataagent.dto.AloudataConfigDTO;
import vip.mate.dataagent.model.DatasourceEntity;
import vip.mate.dataagent.repository.DatasourceMapper;
import vip.mate.dataagent.service.DatasourceAccountService;
import vip.mate.system.service.SystemSettingService;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AloudataServiceImplTest {

    @AfterEach
    void clearUserContext() {
        UserContextHolder.clear();
    }

    @Test
    void usesLocalMockIdentityWhenTheUserHasNoBoundAuthValue() {
        assertEquals("mock-uid-001", AloudataServiceImpl.resolveAuthValue(null, true));
    }

    @Test
    void keepsProductionAuthRequirementWhenTheUserHasNoBoundAuthValue() {
        assertNull(AloudataServiceImpl.resolveAuthValue(null, false));
    }

    @Test
    @SuppressWarnings("unchecked")
    void queriesDimensionValuesUsingDocumentedPagingContractAndColumnarResponse() {
        DatasourceMapper datasourceMapper = mock(DatasourceMapper.class);
        AloudataApiClient apiClient = mock(AloudataApiClient.class);
        AloudataConfigHelper configHelper = mock(AloudataConfigHelper.class);
        DatasourceAccountService accountService = mock(DatasourceAccountService.class);
        Environment environment = mock(Environment.class);
        SystemSettingService settings = mock(SystemSettingService.class);
        when(settings.getString("aloudata.api.endpoints", "")).thenReturn("");

        DatasourceEntity datasource = new DatasourceEntity();
        when(datasourceMapper.selectById(77L)).thenReturn(datasource);
        AloudataConfigDTO config = new AloudataConfigDTO();
        when(configHelper.parseConfig(datasource)).thenReturn(config);
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(false);
        when(accountService.resolveAloudataAuthValue(77L, 99L)).thenReturn("test-auth");
        when(apiClient.callWithParams(eq("dimension_values"), eq(config), any(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("success", true,
                        "data", Map.of("tables", Map.of("values", List.of("门店A", "门店B"))))));
        UserContextHolder.set(new UserContext(99L, "tester", "Tester", "user", 1L));

        AloudataServiceImpl service = new AloudataServiceImpl(datasourceMapper, apiClient, configHelper,
                new AloudataEndpointService(new AloudataApiProperties(settings, new ObjectMapper())),
                accountService, environment);

        assertEquals(List.of("门店A", "门店B"), service.queryDimensionValues(77L, "shop_code_agent", "门店", 50));
        org.mockito.ArgumentCaptor<Map<String, Object>> params = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(apiClient).callWithParams(eq("dimension_values"), eq(config), params.capture());
        assertEquals("shop_code_agent", params.getValue().get("dimName"));
        assertEquals("门店", params.getValue().get("dimValueKeyword"));
        assertEquals(1, params.getValue().get("pageNumber"));
        assertEquals(50, params.getValue().get("pageSize"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void readsDimensionValuesFromAloudataDimensionKeyedTableResponse() {
        DatasourceMapper datasourceMapper = mock(DatasourceMapper.class);
        AloudataApiClient apiClient = mock(AloudataApiClient.class);
        AloudataConfigHelper configHelper = mock(AloudataConfigHelper.class);
        DatasourceAccountService accountService = mock(DatasourceAccountService.class);
        Environment environment = mock(Environment.class);
        SystemSettingService settings = mock(SystemSettingService.class);
        when(settings.getString("aloudata.api.endpoints", "")).thenReturn("");

        DatasourceEntity datasource = new DatasourceEntity();
        when(datasourceMapper.selectById(77L)).thenReturn(datasource);
        AloudataConfigDTO config = new AloudataConfigDTO();
        when(configHelper.parseConfig(datasource)).thenReturn(config);
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(false);
        when(accountService.resolveAloudataAuthValue(77L, 99L)).thenReturn("test-auth");
        when(apiClient.callWithParams(eq("dimension_values"), eq(config), any(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("success", true,
                        "data", Map.of(
                                "metas", List.of(Map.of("name", "shop_code_agent", "dataTypeName", "STRING")),
                                "table", Map.of("shop_code_agent", List.of(
                                        List.of("门店A", 17, Map.of("source", "dimension")),
                                        List.of("门店B", 8, Map.of("source", "dimension"))))))));
        UserContextHolder.set(new UserContext(99L, "tester", "Tester", "user", 1L));

        AloudataServiceImpl service = new AloudataServiceImpl(datasourceMapper, apiClient, configHelper,
                new AloudataEndpointService(new AloudataApiProperties(settings, new ObjectMapper())),
                accountService, environment);

        assertEquals(List.of("门店A", "门店B"), service.queryDimensionValues(77L, "shop_code_agent", null, 200));
    }
}
