package vip.mate.dataagent.service;

import org.junit.jupiter.api.Test;
import vip.mate.dataagent.dto.InsightDashboardSchemaDTO;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InsightDashboardThemeServiceTest {
    @Test
    void acceptsAllRegisteredPresetsAndLegacySchema() {
        for (String preset : List.of("blue", "indigo", "teal", "amber", "dark-data", "rose", "coral", "orange", "gold", "burgundy")) {
            InsightDashboardSchemaDTO schema = new InsightDashboardSchemaDTO();
            InsightDashboardSchemaDTO.Theme theme = new InsightDashboardSchemaDTO.Theme();
            theme.setMode("preset");
            theme.setPresetId(preset);
            schema.setTheme(theme);
            assertDoesNotThrow(() -> DashboardThemeValidator.validate(schema));
        }
        assertDoesNotThrow(() -> DashboardThemeValidator.validate(new InsightDashboardSchemaDTO()));
    }

    @Test
    void rejectsInvalidColorsAndUntrustedIconKeys() {
        InsightDashboardSchemaDTO schema = new InsightDashboardSchemaDTO();
        InsightDashboardSchemaDTO.Theme theme = new InsightDashboardSchemaDTO.Theme();
        theme.setMode("custom");
        theme.setPresetId("blue");
        InsightDashboardSchemaDTO.ThemeOverrides overrides = new InsightDashboardSchemaDTO.ThemeOverrides();
        overrides.setText("url(javascript:alert(1))");
        theme.setOverrides(overrides);
        schema.setTheme(theme);

        InsightDashboardSchemaDTO.Component component = new InsightDashboardSchemaDTO.Component();
        InsightDashboardSchemaDTO.KpiMetric metric = new InsightDashboardSchemaDTO.KpiMetric();
        InsightDashboardSchemaDTO.KpiMetricVisual visual = new InsightDashboardSchemaDTO.KpiMetricVisual();
        visual.setIconKey("<svg onload=alert(1)>");
        metric.setVisual(visual);
        component.setKpiMetrics(List.of(metric));
        schema.setComponents(List.of(component));

        assertThrows(IllegalArgumentException.class, () -> DashboardThemeValidator.validate(schema));
    }
}
