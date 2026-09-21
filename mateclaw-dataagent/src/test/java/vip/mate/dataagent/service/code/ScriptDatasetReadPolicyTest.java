package vip.mate.dataagent.service.code;

import org.junit.jupiter.api.Test;
import vip.mate.dataagent.dataset.DatasetFilter;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class ScriptDatasetReadPolicyTest {

    private DatasetFilter inFilter(int count) {
        return new DatasetFilter("customer_id", "dimension", "in", IntStream.range(0, count).boxed().toList());
    }

    @Test
    void acceptsNormalRead() {
        new ScriptDatasetReadPolicy().validate(
                List.of(new DatasetFilter("status", "dimension", "eq", "PAID"),
                        inFilter(1000)),
                1024);
    }

    @Test
    void rejectsMoreThanFiftyFilters() {
        var filters = IntStream.range(0, 51)
                .mapToObj(i -> new DatasetFilter("f" + i, "dimension", "eq", i))
                .toList();
        var error = assertThrows(IllegalArgumentException.class,
                () -> new ScriptDatasetReadPolicy().validate(filters, 1024));
        assertTrue(error.getMessage().contains("too many script dataset filters"));
    }

    @Test
    void rejectsInValuesOverLimit() {
        var error = assertThrows(IllegalArgumentException.class,
                () -> new ScriptDatasetReadPolicy().validate(List.of(inFilter(1001)), 1024));
        assertTrue(error.getMessage().contains("filter value count exceeds 1000"));
    }

    @Test
    void rejectsOversizedPayloadBeforeAdapter() {
        var error = assertThrows(IllegalArgumentException.class,
                () -> new ScriptDatasetReadPolicy().validate(List.of(), 256L * 1024 + 1));
        assertTrue(error.getMessage().contains("payload too large"));
    }

    @Test
    void rejectsInFilterWithNonListValue() {
        assertThrows(IllegalArgumentException.class,
                () -> new ScriptDatasetReadPolicy().validate(
                        List.of(new DatasetFilter("customer_id", "dimension", "in", 7)), 1024));
    }
}
