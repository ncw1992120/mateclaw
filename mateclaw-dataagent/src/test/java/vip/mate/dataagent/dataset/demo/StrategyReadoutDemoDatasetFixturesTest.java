package vip.mate.dataagent.dataset.demo;

import org.junit.jupiter.api.Test;
import vip.mate.dataagent.dataset.DatasetAccessContext;
import vip.mate.dataagent.dataset.DatasetReadRequest;
import vip.mate.dataagent.exception.BusinessException;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class StrategyReadoutDemoDatasetFixturesTest {
    private static final long DIMENSION_DATASET_ID = 2104203094760529922L;
    private static final long METRIC_DATASET_ID = 2104203095955906562L;
    private static final long CURRENT_METRIC_DATASET_ID = 2104142033512488962L;
    private static final long CURRENT_DIMENSION_DATASET_ID = 2104142034569453570L;
    private final StrategyReadoutDemoDatasetFixtures fixtures = new StrategyReadoutDemoDatasetFixtures();
    private final DatasetAccessContext context = new DatasetAccessContext(1L, 2L, "fixture-test",
            Set.of(DIMENSION_DATASET_ID, METRIC_DATASET_ID));

    @Test
    void providesDimensionAndWideMetricDescriptorsForOnlyTheDeclaredIds() {
        var dimension = fixtures.describe(context, DIMENSION_DATASET_ID, "table_wd");
        var wide = fixtures.describe(context, METRIC_DATASET_ID, "table_ab");

        assertEquals("table_wd", dimension.inputName());
        assertTrue(dimension.schema().stream().anyMatch(column -> column.name().equals("metric_name")));
        assertTrue(dimension.schema().stream().anyMatch(column -> column.name().equals("attribution_plan_id")));
        assertTrue(wide.schema().stream().anyMatch(column -> column.name().startsWith("digo_")));
        assertFalse(fixtures.supports(999999L));
    }

    @Test
    void readsProjectedRowsAndReturnsAnEmptyBatchWhenFiltersHaveNoMatches() {
        var rows = fixtures.read(context, new DatasetReadRequest(DIMENSION_DATASET_ID, "table_wd",
                List.of("metric_name", "attribution_plan_id"), List.of(), 10, 0, Map.of()));
        var empty = fixtures.read(context, new DatasetReadRequest(DIMENSION_DATASET_ID, "table_wd",
                List.of(), List.of(new vip.mate.dataagent.dataset.DatasetFilter(
                "attribution_plan_id", "dimension", "eq", "NO_SUCH_PLAN")), 10, 0, Map.of()));

        assertFalse(rows.rows().isEmpty());
        assertEquals(Set.of("metric_name", "attribution_plan_id"), rows.rows().getFirst().keySet());
        assertTrue(empty.rows().isEmpty());
        assertEquals(0, empty.rowCount());
    }

    @Test
    void rejectsReadsOutsideTheDatasetAccessContext() {
        DatasetAccessContext denied = new DatasetAccessContext(1L, 2L, "fixture-test", Set.of());

        assertThrows(BusinessException.class,
                () -> fixtures.describe(denied, DIMENSION_DATASET_ID, "table_wd"));
        assertThrows(BusinessException.class,
                () -> fixtures.read(denied, new DatasetReadRequest(DIMENSION_DATASET_ID, "table_wd",
                        List.of(), List.of(), 10, 0, Map.of())));
    }

    @Test
    void supportsTheDatasetIdsPersistedByTheCurrentStrategyReadoutDashboard() {
        DatasetAccessContext currentContext = new DatasetAccessContext(1L, 2L, "current-dashboard",
                Set.of(CURRENT_METRIC_DATASET_ID, CURRENT_DIMENSION_DATASET_ID));

        var metricDescriptor = fixtures.describe(currentContext, CURRENT_METRIC_DATASET_ID, "table_ab");
        var dimensionDescriptor = fixtures.describe(currentContext, CURRENT_DIMENSION_DATASET_ID, "table_wd");
        var metrics = fixtures.read(currentContext, new DatasetReadRequest(CURRENT_METRIC_DATASET_ID, "table_ab",
                List.of("digo_trd_fund_amt_inout_cy_jjgr", "digo_new_cust_asset_in"), List.of(), 10, 0, Map.of()));
        var dimensions = fixtures.read(currentContext, new DatasetReadRequest(CURRENT_DIMENSION_DATASET_ID, "table_wd",
                List.of("metric_id", "metric_name", "attribution_plan_name", "channel"), List.of(), 10, 0, Map.of()));

        assertTrue(fixtures.supports(CURRENT_METRIC_DATASET_ID));
        assertTrue(fixtures.supports(CURRENT_DIMENSION_DATASET_ID));
        assertTrue(metricDescriptor.schema().stream().anyMatch(column -> column.name().equals("digo_new_cust_asset_in")));
        assertTrue(dimensionDescriptor.schema().stream().anyMatch(column -> column.name().equals("attribution_plan_name")));
        assertFalse(metrics.rows().isEmpty());
        assertFalse(dimensions.rows().isEmpty());
        assertTrue(dimensions.rows().getFirst().containsKey("metric_id"));
    }
}
