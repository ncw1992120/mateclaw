package vip.mate.dataagent.service.code;

import org.junit.jupiter.api.Test;
import vip.mate.dataagent.service.code.ScriptResultContractService.ValidatedEnvelope;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScriptResultContractServiceTest {
    private final ScriptResultContractService service = new ScriptResultContractService();

    private Map<String, Object> numberColumn(String name) {
        return Map.of("name", name, "title", name, "dataType", "number", "nullable", false);
    }

    private Map<String, Object> tableEnvelope(List<Map<String, Object>> columns, List<Map<String, Object>> rows) {
        return Map.of("schemaVersion", "1.0", "kind", "table",
                "data", Map.of("columns", columns, "rows", rows),
                "meta", Map.of("rowCount", rows.size(), "truncated", false, "sourceInputs", List.of()));
    }

    @Test
    void rejectsUnknownVersionAndKind() {
        var raw = Map.of("schemaVersion", "2.0", "kind", "chart", "data", Map.of(), "meta", Map.of());
        var error = assertThrows(ScriptResultContractException.class, () -> service.validate(raw));
        assertThat(error.getPath()).isEqualTo("result.schemaVersion");

        var badKind = Map.of("schemaVersion", "1.0", "kind", "chart", "data", Map.of(), "meta", Map.of());
        assertThat(assertThrows(ScriptResultContractException.class, () -> service.validate(badKind)).getPath())
                .isEqualTo("result.kind");
    }

    @Test
    void rejectsTableRowsThatDoNotMatchColumns() {
        var raw = tableEnvelope(List.of(numberColumn("amount")), List.of(Map.of("other", 1)));
        var error = assertThrows(ScriptResultContractException.class, () -> service.validate(raw));
        assertThat(error.getPath()).isEqualTo("result.data.rows[0].amount");
    }

    @Test
    void acceptsEmptyTable() {
        var result = service.validate(tableEnvelope(List.of(), List.of()));
        assertThat(result.kind()).isEqualTo("table");
        assertThat(result.meta().rowCount()).isZero();
    }

    @Test
    void returnsPathForTypeMismatch() {
        var raw = tableEnvelope(List.of(numberColumn("amount")), List.of(Map.of("amount", "unknown")));
        var error = assertThrows(ScriptResultContractException.class, () -> service.validate(raw));
        assertThat(error.getPath()).isEqualTo("result.data.rows[0].amount");
        assertThat(error.getExpected()).isEqualTo("number");
    }

    @Test
    void rebuildsEnvelopeFromParquetReferenceMetadata() {
        var result = service.rebuildTable(
                Map.of("schemaVersion", "1.0", "kind", "table", "columns", List.of(numberColumn("amount")), "rowCount", 1),
                List.<Map<String, Object>>of(Map.of("amount", 12.5)));
        assertThat(result.data().rows()).containsExactly(Map.of("amount", 12.5));
        assertThat(result.meta().rowCount()).isEqualTo(1);
    }

    @Test
    void previewTruncatesRowsButKeepsRealRowCount() {
        var rows = List.<Map<String, Object>>of(Map.of("id", 1), Map.of("id", 2), Map.of("id", 3));
        var envelope = service.validate(tableEnvelope(List.of(numberColumn("id")), rows));
        var previewed = service.preview(envelope, 2);
        assertThat(previewed.data().rows()).hasSize(2);
        assertThat(previewed.meta().rowCount()).isEqualTo(3);
        assertThat(previewed.meta().truncated()).isTrue();
    }

    @Test
    void validatesScalarAndMessageEnvelopes() {
        var scalar = service.validate(Map.of("schemaVersion", "1.0", "kind", "scalar",
                "data", Map.of("value", 42, "dataType", "number"), "meta", Map.of("rowCount", 0)));
        assertThat(scalar.kind()).isEqualTo("scalar");

        var message = service.validate(Map.of("schemaVersion", "1.0", "kind", "message",
                "data", Map.of("level", "info", "message", "没有满足条件的数据"), "meta", Map.of("rowCount", 0)));
        assertThat(message.kind()).isEqualTo("message");
    }

    @Test
    void rejectsDuplicateColumnsAndBadScalarType() {
        var duplicated = tableEnvelope(
                List.of(numberColumn("id"), numberColumn("id")), List.of(Map.of("id", 1)));
        assertThat(assertThrows(ScriptResultContractException.class, () -> service.validate(duplicated)).getPath())
                .isEqualTo("result.data.columns");

        var badScalar = Map.of("schemaVersion", "1.0", "kind", "scalar",
                "data", Map.of("value", "text", "dataType", "number"), "meta", Map.of("rowCount", 0));
        assertThat(assertThrows(ScriptResultContractException.class, () -> service.validate(badScalar)).getPath())
                .isEqualTo("result.data.value");
    }
}
