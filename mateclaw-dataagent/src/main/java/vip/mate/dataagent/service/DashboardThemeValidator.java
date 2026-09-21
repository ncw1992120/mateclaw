package vip.mate.dataagent.service;

import vip.mate.dataagent.dto.InsightDashboardSchemaDTO;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** 仪表盘主题和 KPI 视觉配置的结构校验，不执行查询，也不接受任意 CSS/SVG。 */
public final class DashboardThemeValidator {
    private static final Set<String> PRESETS = Set.of("blue", "indigo", "teal", "amber", "dark-data", "rose", "coral", "orange", "gold", "burgundy");
    private static final Set<String> MODES = Set.of("preset", "custom");
    private static final Set<String> COLOR_MODES = Set.of("theme", "custom");
    private static final Set<String> RADII = Set.of("small", "medium", "large");
    private static final Set<String> SHADOWS = Set.of("none", "subtle", "elevated");
    private static final Set<String> ICONS = Set.of("trend-charts", "data-analysis", "data-line", "histogram", "pie-chart", "odometer", "aim", "connection", "collection", "coin", "credit-card", "money", "shopping-cart", "user", "user-filled", "setting", "operation", "monitor", "promotion", "star", "timer", "calendar", "tickets", "chart-bar");
    private static final Pattern COLOR = Pattern.compile("#[0-9a-fA-F]{6}");

    private DashboardThemeValidator() { }

    public static void validate(InsightDashboardSchemaDTO schema) {
        if (schema == null || schema.getTheme() == null) {
            validateMetrics(schema);
            return;
        }
        InsightDashboardSchemaDTO.Theme theme = schema.getTheme();
        if (theme.getMode() != null && !MODES.contains(theme.getMode())) fail("theme.mode 不合法");
        if (theme.getPresetId() != null && !PRESETS.contains(theme.getPresetId())) fail("theme.presetId 不在允许的预设列表");
        if (theme.getDensity() != null && !Set.of("compact", "standard", "large").contains(theme.getDensity())) fail("theme.density 不合法");
        validateOverrides(theme.getOverrides());
        validateMetrics(schema);
    }

    private static void validateOverrides(InsightDashboardSchemaDTO.ThemeOverrides overrides) {
        if (overrides == null) return;
        List<String> colors = java.util.stream.Stream.of(overrides.getPageBackground(), overrides.getCardBackground(), overrides.getBorder(), overrides.getText(), overrides.getTextSecondary(), overrides.getTextMuted(), overrides.getPrimary(), overrides.getPositive(), overrides.getNegative(), overrides.getWarning(), overrides.getInfo()).toList();
        colors.stream().filter(value -> value != null && !COLOR.matcher(value).matches()).findFirst().ifPresent(value -> fail("主题颜色必须是 6 位 HEX"));
        validatePalette(overrides.getMetricPalette(), "metricPalette");
        validatePalette(overrides.getChartPalette(), "chartPalette");
        if (overrides.getRadius() != null && !RADII.contains(overrides.getRadius())) fail("theme.overrides.radius 不合法");
        if (overrides.getShadow() != null && !SHADOWS.contains(overrides.getShadow())) fail("theme.overrides.shadow 不合法");
    }

    private static void validatePalette(List<String> palette, String name) {
        if (palette == null) return;
        if (palette.size() < 5 || palette.size() > 20 || palette.stream().anyMatch(value -> value == null || !COLOR.matcher(value).matches())) fail("主题色板 " + name + " 不合法");
    }

    private static void validateMetrics(InsightDashboardSchemaDTO schema) {
        if (schema == null) return;
        for (InsightDashboardSchemaDTO.Component component : schema.getAllComponents()) {
            if (component.getKpiMetrics() == null) continue;
            for (InsightDashboardSchemaDTO.KpiMetric metric : component.getKpiMetrics()) {
                InsightDashboardSchemaDTO.KpiMetricVisual visual = metric.getVisual();
                if (visual != null) {
                    if (visual.getIconKey() != null && !ICONS.contains(visual.getIconKey())) fail("KPI 图标不在本地注册表: " + visual.getIconKey());
                    if (visual.getColorMode() != null && !COLOR_MODES.contains(visual.getColorMode())) fail("KPI 图标颜色模式不合法");
                    if (visual.getAccentColor() != null && !COLOR.matcher(visual.getAccentColor()).matches()) fail("KPI 强调色不合法");
                }
                validateStyle(metric.getStyles());
            }
        }
    }

    private static void validateStyle(InsightDashboardSchemaDTO.KpiMetricStyles styles) {
        if (styles == null) return;
        for (InsightDashboardSchemaDTO.KpiMetricFieldStyle style : List.of(styles.getName(), styles.getValue(), styles.getUnit(), styles.getHelper())) {
            if (style == null) continue;
            if (style.getSize() != null && (style.getSize() < 8 || style.getSize() > 96)) fail("KPI 字号超出范围");
            if (style.getColor() != null && !style.getColor().isBlank() && !COLOR.matcher(style.getColor()).matches()) fail("KPI 颜色不合法");
            if (style.getColorMode() != null && !COLOR_MODES.contains(style.getColorMode())) fail("KPI 字段颜色模式不合法");
        }
    }

    private static void fail(String message) { throw new IllegalArgumentException(message); }
}
