package vip.mate.dataagent.dataset.file;

import org.junit.jupiter.api.Test;
import vip.mate.dataagent.dataset.DatasetColumn;
import vip.mate.dataagent.dataset.DatasetReadException;

import java.util.List;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SchemaVersionValidatorTest {
    @Test void rejectsColumnDriftWithoutVersionBump() {
        var oldSchema = List.of(new DatasetColumn("id","id","integer",false,"measure"));
        var changed = List.of(new DatasetColumn("id","id","string",false,"dimension"));
        assertThrows(DatasetReadException.class, () -> SchemaVersionValidator.requireCompatible(1,1,oldSchema,changed));
    }
    @Test void allowsExplicitVersionBump() {
        var oldSchema = List.of(new DatasetColumn("id","id","integer",false,"measure"));
        var changed = List.of(new DatasetColumn("id","id","string",false,"dimension"));
        SchemaVersionValidator.requireCompatible(1,2,oldSchema,changed);
    }
}
