package vip.mate.dataagent.service;

import org.junit.jupiter.api.Test;
import vip.mate.dataagent.dto.FinalResultQueryConfigDTO;
import vip.mate.dataagent.dto.FinalResultQueryContextDTO;
import vip.mate.dataagent.service.code.ScriptResultContractService;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FinalResultQueryServiceTest {

    @Test
    void filtersSortsAndPaginatesPythonTableInMemory() {
        var envelope = table(
                row("region", "华东", "amount", 10),
                row("region", "华南", "amount", 30),
                row("region", "华东", "amount", 20));
        var config = new FinalResultQueryConfigDTO(
                "schema-1",
                List.of(new FinalResultQueryConfigDTO.DisplayField("region", "区域", "dimension", "string"),
                        new FinalResultQueryConfigDTO.DisplayField("amount", "金额", "metric", "number")),
                List.of(new FinalResultQueryConfigDTO.FilterField("amount", "金额", "number", "amount", List.of("gte"))),
                List.of(new FinalResultQueryConfigDTO.ParameterBinding("amount", "amount", "gte")),
                new FinalResultQueryConfigDTO.SortPolicy(true, List.of("amount")),
                new FinalResultQueryConfigDTO.PaginationPolicy(true, 1, 100, true));

        var page = new FinalResultQueryServiceImpl().query(envelope, config,
                new FinalResultQueryContextDTO(Map.of("amount", 15),
                        new vip.mate.dataagent.dto.QueryContextDTO.SortSpec("amount", "desc"),
                        new vip.mate.dataagent.dto.QueryContextDTO.PaginationSpec(1, 1)));

        assertThat(page.totalRows()).isEqualTo(2);
        assertThat(page.envelope().data().rows()).containsExactly(row("region", "华南", "amount", 30));
        assertThat(page.envelope().meta().rowCount()).isEqualTo(2);
        assertThat(page.envelope().meta().truncated()).isTrue();
    }

    @Test
    void rejectsFilterFieldOutsideWhitelist() {
        var config = new FinalResultQueryConfigDTO("schema-1", List.of(), List.of(), List.of(),
                new FinalResultQueryConfigDTO.SortPolicy(false, List.of()),
                new FinalResultQueryConfigDTO.PaginationPolicy(false, 100, 100, false));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new FinalResultQueryServiceImpl().query(
                        table(row("amount", 10)), config,
                        new FinalResultQueryContextDTO(Map.of("amount", 10), null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("parameter binding");
    }

    @Test
    void leavesScalarUntouchedWhenNoFinalQueryIsRequested() {
        var envelope = new ScriptResultContractService.ValidatedEnvelope(
                "1.0", "scalar", Map.of("value", 42, "dataType", "number"),
                new ScriptResultContractService.ValidatedEnvelope.Meta(0, false, List.of()));
        var config = new FinalResultQueryConfigDTO("schema-1", List.of(), List.of(), List.of(),
                new FinalResultQueryConfigDTO.SortPolicy(false, List.of()),
                new FinalResultQueryConfigDTO.PaginationPolicy(false, 100, 100, false));

        var page = new FinalResultQueryServiceImpl().query(envelope, config,
                new FinalResultQueryContextDTO(Map.of(), null, null));

        assertThat(page.envelope()).isSameAs(envelope);
        assertThat(page.totalRows()).isZero();
    }

    @Test
    void projectsConfiguredDisplayFieldsAfterQuerying() {
        var config = new FinalResultQueryConfigDTO("schema-1",
                List.of(new FinalResultQueryConfigDTO.DisplayField("amount", "金额", "measure", "number")),
                List.of(), List.of(),
                new FinalResultQueryConfigDTO.SortPolicy(false, List.of()),
                new FinalResultQueryConfigDTO.PaginationPolicy(false, 100, 100, false));

        var page = new FinalResultQueryServiceImpl().query(table(row("region", "华东", "amount", 10)), config,
                new FinalResultQueryContextDTO(Map.of(), null, null));

        assertThat(page.envelope().data().columns()).extracting(ScriptResultContractService.ValidatedEnvelope.Column::name)
                .containsExactly("amount");
        assertThat(page.envelope().data().rows()).containsExactly(Map.of("amount", 10));
    }

    private static ScriptResultContractService.ValidatedEnvelope table(Map<String, Object>... rows) {
        return new ScriptResultContractService.ValidatedEnvelope("1.0", "table",
                new ScriptResultContractService.ValidatedEnvelope.TableData(
                        List.of(new ScriptResultContractService.ValidatedEnvelope.Column("region", "区域", "string", true),
                                new ScriptResultContractService.ValidatedEnvelope.Column("amount", "金额", "number", true)),
                        List.of(rows)),
                new ScriptResultContractService.ValidatedEnvelope.Meta(rows.length, false, List.of()));
    }

    private static Map<String, Object> row(Object... values) {
        var row = new java.util.LinkedHashMap<String, Object>();
        for (int i = 0; i < values.length; i += 2) row.put((String) values[i], values[i + 1]);
        return row;
    }
}
