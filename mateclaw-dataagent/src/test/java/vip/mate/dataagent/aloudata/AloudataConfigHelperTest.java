package vip.mate.dataagent.aloudata;

import org.junit.jupiter.api.Test;
import vip.mate.dataagent.model.DatasourceEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AloudataConfigHelperTest {
    @Test
    void mapsDatasourceTenantAndAuthValueToTheDefaultAloudataContext() {
        DatasourceEntity datasource = new DatasourceEntity();
        datasource.setUsername("tenant-test");
        datasource.setPassword("uid-test");
        datasource.setProductHost("https://product.example");
        datasource.setSemanticHost("http://semantic.example");
        datasource.setConnectionParams("{\"anymetricsPort\":443,\"semanticPort\":80,\"authType\":\"UID\"}");

        var config = new AloudataConfigHelper().parseConfig(datasource);

        assertEquals("tenant-test", config.getTenantId());
        assertEquals("uid-test", config.getAuthValue());
        assertEquals("UID", config.getAuthType());
        assertEquals(443, config.getAnymetricsPort());
        assertEquals(80, config.getSemanticPort());
        assertEquals("https://product.example", config.getAnymetricsHost());
        assertEquals("http://semantic.example", config.getSemanticHost());
    }
}
