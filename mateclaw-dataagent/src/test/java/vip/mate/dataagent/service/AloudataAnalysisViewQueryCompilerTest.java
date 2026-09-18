package vip.mate.dataagent.service;

import org.junit.jupiter.api.Test;
import vip.mate.dataagent.dataset.DatasetFilter;
import vip.mate.dataagent.dataset.DatasetReadException;
import vip.mate.dataagent.dataset.DatasetReadRequest;
import vip.mate.dataagent.dto.AloudataAnalysisViewDetail;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 视图筛选编译：真实 semantic {@code metrics/query} 只接受表达式字符串数组，
 * 结构化 {@code {field,operator,value}} 会返回 {@code SM99002}。这里锁定该契约，
 * 防止有人改回结构化形态（本地 mock 已同步返回 SM99002，见 LocalAloudataFixturesTest）。
 */
class AloudataAnalysisViewQueryCompilerTest {

    private final AloudataAnalysisViewQueryCompiler compiler = new AloudataAnalysisViewQueryCompiler();

    private AloudataAnalysisViewDetail view() {
        return new AloudataAnalysisViewDetail("v1", "sales", "销售", null,
                List.of(Map.of("name", "revenue")), List.of(Map.of("name", "region")),
                null, List.of(), List.of(), List.of());
    }

    @Test
    void compilesFiltersIntoAloudataExpressions() {
        Map<String, Object> body = compiler.compile(view(), new DatasetReadRequest(7L, "sales", List.of(),
                List.of(new DatasetFilter("region", "dimension", "eq", "华东"),
                        new DatasetFilter("revenue", "measure", "gte", 10)),
                10, 0, Map.of()));

        assertEquals(List.of("[region] = \"华东\"", "[revenue] >= 10"), body.get("filters"));
        assertEquals(List.of("revenue"), body.get("metrics"));
        assertEquals(List.of("region"), body.get("dimensions"));
        assertEquals("DATA", body.get("queryResultType"));
    }

    @Test
    void inAndBetweenUseAloudataSyntax() {
        Map<String, Object> body = compiler.compile(view(), new DatasetReadRequest(7L, "sales", List.of(),
                List.of(new DatasetFilter("region", "dimension", "in", List.of("华东", "华南")),
                        new DatasetFilter("revenue", "measure", "between", List.of(10, 40))),
                10, 0, Map.of()));

        assertEquals(List.of("[region] IN (\"华东\",\"华南\")", "([revenue] >= 10 AND [revenue] <= 40)"),
                body.get("filters"));
    }

    @Test
    void unsupportedFieldAndOperatorAreRejected() {
        DatasetReadRequest request = new DatasetReadRequest(7L, "sales", List.of(),
                List.of(new DatasetFilter("secret", "dimension", "eq", "x")), 10, 0, Map.of());
        assertThrows(DatasetReadException.class, () -> compiler.compile(view(), request));

        DatasetReadRequest nullCheck = new DatasetReadRequest(7L, "sales", List.of(),
                List.of(new DatasetFilter("region", "dimension", "is_null", null)), 10, 0, Map.of());
        assertThrows(DatasetReadException.class, () -> compiler.compile(view(), nullCheck));
    }
}
