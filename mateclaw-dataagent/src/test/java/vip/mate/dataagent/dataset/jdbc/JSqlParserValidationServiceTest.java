package vip.mate.dataagent.dataset.jdbc;

import org.junit.jupiter.api.Test;
import vip.mate.dataagent.dataset.DatasetFilter;

import java.util.List;

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
}
