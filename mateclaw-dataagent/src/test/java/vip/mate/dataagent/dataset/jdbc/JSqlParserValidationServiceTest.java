package vip.mate.dataagent.dataset.jdbc;

import org.junit.jupiter.api.Test;
import vip.mate.dataagent.dataset.DatasetFilter;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JSqlParserValidationServiceTest {
    private final SqlValidationService service = new JSqlParserValidationService();

    @Test
    void preservesBaseWhereAndBindsAdditionalFilterAndPaging() {
        CompiledJdbcQuery query = service.compile(
                "WITH source AS (SELECT id, status FROM orders WHERE deleted = 0) SELECT id, status FROM source ORDER BY id",
                List.of("id", "status"), List.of(new DatasetFilter("status", "dimension", "eq", "PAID")), 50, 10);

        assertTrue(query.sql().contains("deleted = 0"));
        assertTrue(query.sql().contains("status = ?"));
        assertTrue(query.sql().contains("LIMIT ? OFFSET ?"));
        assertEquals(List.of("PAID", 50, 10), query.parameters());
        assertFalse(query.digest().isBlank());
    }

    @Test
    void rejectsDmlMultipleStatementsAndUnknownColumns() {
        assertThrows(IllegalArgumentException.class, () -> service.compile("UPDATE orders SET status='x'", List.of(), List.of(), 10, 0));
        assertThrows(IllegalArgumentException.class, () -> service.compile("SELECT * FROM orders; DELETE FROM orders", List.of(), List.of(), 10, 0));
        assertThrows(IllegalArgumentException.class, () -> service.compile("SELECT id FROM orders", List.of("id"),
                List.of(new DatasetFilter("secret", "dimension", "eq", "x")), 10, 0));
    }

    @Test
    void rejectsUnsafeProjectionAndInvalidPageSize() {
        assertThrows(IllegalArgumentException.class, () -> service.compile("SELECT id FROM orders", List.of("id, secret"), List.of(), 10, 0));
        assertThrows(IllegalArgumentException.class, () -> service.compile("SELECT id FROM orders", List.of(), List.of(), 10_001, 0));
    }

    @Test
    void expandsCollectionOperatorsIntoIndividualBindings() {
        CompiledJdbcQuery query = service.compile("SELECT id FROM orders", List.of("id"),
                List.of(new DatasetFilter("id", "measure", "in", List.of(1, 2))), 10, 0);
        assertTrue(query.sql().contains("id IN (?, ?)"));
        assertEquals(List.of(1, 2, 10, 0), query.parameters());
    }

    @Test
    void acceptsSemicolonsInsideLiteralsAndCommentsButRejectsMultipleStatements() {
        CompiledJdbcQuery query = service.compile(
                "SELECT id, 'a;b' AS marker FROM orders -- trailing comment\nWHERE status = 'PAID'",
                List.of("id", "marker"), List.of(), 10, 0);
        assertTrue(query.sql().contains("a;b"));
        assertTrue(query.sql().contains("LIMIT ? OFFSET ?"));
        assertThrows(IllegalArgumentException.class, () -> service.compile(
                "SELECT id FROM orders; SELECT id FROM orders", List.of("id"), List.of(), 10, 0));
    }

    /* ==================== 命名参数绑定（:name 占位符） ====================
       核心不变量：参数顺序必须是「SQL 内的 ? → filters 的 ? → limit/offset」。
       顺序错位时 PreparedStatement 会静默设错值 —— 不报错但结果不对，最难排查。 */

    @Test
    void bindsNamedParametersIntoPlaceholdersBeforeParsing() {
        CompiledJdbcQuery query = service.compile(
                "SELECT plan_id FROM sales WHERE metric_time >= :start_date AND region = :region",
                List.of(), List.of(), 10, 0,
                Map.of("start_date", "2026-09-01", "region", "华东"));

        // 占位符必须已换成 ?；残留 :name 会在数据库层报语法错（JSqlParser 也解析不过去）
        assertFalse(query.sql().contains(":start_date"));
        assertFalse(query.sql().contains(":region"));
        assertEquals(List.of("2026-09-01", "华东", 10, 0), query.parameters());
    }

    @Test
    void namedParametersPrecedeFilterParameters() {
        CompiledJdbcQuery query = service.compile(
                "SELECT id, status FROM orders WHERE created_at >= :since",
                List.of("id", "status"), List.of(new DatasetFilter("status", "dimension", "eq", "PAID")), 10, 0,
                Map.of("since", "2026-09-01"));

        // SQL 内的参数在子查询里、filters 在外层 WHERE，顺序反了就会把值设到错的位置
        assertEquals(List.of("2026-09-01", "PAID", 10, 0), query.parameters());
    }

    @Test
    void repeatedPlaceholderBindsValueEachOccurrence() {
        CompiledJdbcQuery query = service.compile(
                "SELECT id FROM orders WHERE a >= :d AND b <= :d",
                List.of("id"), List.of(), 5, 0,
                Map.of("d", "2026-09-01"));

        assertEquals(List.of("2026-09-01", "2026-09-01", 5, 0), query.parameters());
    }

    @Test
    void leavesColonInsideStringLiteralAlone() {
        CompiledJdbcQuery query = service.compile(
                "SELECT id, ':notice' AS tag FROM orders",
                List.of("id", "tag"), List.of(), 10, 0, Map.of());

        assertTrue(query.sql().contains(":notice"), "字符串字面量里的冒号不是参数");
        assertEquals(List.of(10, 0), query.parameters());
    }

    @Test
    void leavesCastOperatorAlone() {
        CompiledJdbcQuery query = service.compile(
                "SELECT amount::numeric AS amount FROM orders",
                List.of("amount"), List.of(), 10, 0, Map.of());

        assertTrue(query.sql().contains("::"), ":: 是类型转换，不是命名参数");
    }

    @Test
    void failsFastWhenPlaceholderHasNoValue() {
        // 静默传 null 会让查询返回空结果，用户分不清「没数据」还是「参数没传进来」
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.compile(
                "SELECT id FROM orders WHERE d >= :d AND r = :r",
                List.of("id"), List.of(), 10, 0, Map.of("d", "2026-09-01")));
        assertTrue(error.getMessage().contains("missing SQL parameter: r"));
    }

    @Test
    void legacySignatureWithoutParametersKeepsWorking() {
        CompiledJdbcQuery query = service.compile("SELECT id FROM orders", List.of("id"), List.of(), 5, 0);
        assertEquals(List.of(5, 0), query.parameters());
    }

    @Test
    void compilesContainsIntoLikeWithWildcards() {
        CompiledJdbcQuery query = service.compile("SELECT id, name FROM orders", List.of("id", "name"),
                List.of(new DatasetFilter("name", "dimension", "contains", "基金")), 10, 0);

        assertTrue(query.sql().contains("name LIKE ?"));
        // 值两侧自动补通配符：用户填纯文本，不必自己写 %
        assertEquals(List.of("%基金%", 10, 0), query.parameters());
    }

    @Test
    void rejectsUnsupportedFilterOperator() {
        // 「最近 N 天」这类非数据库语义的操作符应当被挡在构造器，而不是拼进 SQL
        assertThrows(IllegalArgumentException.class, () -> new DatasetFilter("d", "dimension", "recent_days", "7"));
    }
}
