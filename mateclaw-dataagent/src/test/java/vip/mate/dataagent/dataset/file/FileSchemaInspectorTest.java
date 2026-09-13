package vip.mate.dataagent.dataset.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import vip.mate.dataagent.dataset.DatasetReadException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;

import static org.junit.jupiter.api.Assertions.*;

class FileSchemaInspectorTest {
    private final FileSchemaInspector inspector = new FileSchemaInspector(new ObjectMapper());

    @Test
    void infersCsvSchemaWithNullableAndRoles() {
        var schema = inspector.inspect("orders.csv", stream("id,status\n1,PAID\n2,\n"), FileReadOptions.defaults());
        assertEquals(List.of("id", "status"), schema.stream().map(c -> c.name()).toList());
        assertEquals("integer", schema.getFirst().dataType());
        assertEquals("measure", schema.getFirst().semanticRole());
        assertTrue(schema.get(1).nullable());
    }

    @Test
    void infersJsonSchemaAndRejectsUnsafeInput() {
        var schema = inspector.inspect("orders.json", stream("[{\"id\":1,\"paid\":true}]"), FileReadOptions.defaults());
        assertEquals("integer", schema.getFirst().dataType());
        assertEquals("boolean", schema.get(1).dataType());
        assertThrows(DatasetReadException.class, () -> inspector.inspect("orders.txt", stream("x"), FileReadOptions.defaults()));
    }

    @Test
    void enforcesColumnAndByteLimits() {
        DatasetReadException columns = assertThrows(DatasetReadException.class, () -> inspector.inspect("x.csv", stream("a,b\n1,2\n"), new FileReadOptions(10, 1, 100)));
        assertEquals(vip.mate.dataagent.dataset.DatasetReadErrorCode.RESULT_LIMIT_EXCEEDED, columns.code());
        DatasetReadException bytes = assertThrows(DatasetReadException.class, () -> inspector.inspect("x.csv", stream("a\n12345\n"), new FileReadOptions(10, 10, 3)));
        assertEquals(vip.mate.dataagent.dataset.DatasetReadErrorCode.RESULT_LIMIT_EXCEEDED, bytes.code());
    }

    @Test
    void rejectsEmptyAndCorruptFilesWithInvalidRequest() {
        DatasetReadException empty = assertThrows(DatasetReadException.class,
                () -> inspector.inspect("empty.csv", stream("a\n"), FileReadOptions.defaults()));
        assertEquals(vip.mate.dataagent.dataset.DatasetReadErrorCode.INVALID_REQUEST, empty.code());
        DatasetReadException corrupt = assertThrows(DatasetReadException.class,
                () -> inspector.inspect("broken.json", stream("{not-json"), FileReadOptions.defaults()));
        assertEquals(vip.mate.dataagent.dataset.DatasetReadErrorCode.INVALID_REQUEST, corrupt.code());
    }

    @Test
    void inspectsXlsxContent() throws Exception {
        try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("orders"); var header = sheet.createRow(0); header.createCell(0).setCellValue("id"); header.createCell(1).setCellValue("status");
            var row = sheet.createRow(1); row.createCell(0).setCellValue(1); row.createCell(1).setCellValue("PAID"); workbook.write(out);
            var schema = inspector.inspect("orders.xlsx", new ByteArrayInputStream(out.toByteArray()), FileReadOptions.defaults());
            assertEquals(List.of("id", "status"), schema.stream().map(c -> c.name()).toList());
        }
    }

    @Test
    void inspectsParquetContent() throws Exception {
        var schema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"Order\",\"fields\":[{\"name\":\"id\",\"type\":\"long\"},{\"name\":\"status\",\"type\":\"string\"}]}");
        var file = java.nio.file.Files.createTempFile("mateclaw-test-", ".parquet");
        java.nio.file.Files.deleteIfExists(file);
        try (var writer = AvroParquetWriter.<org.apache.avro.generic.GenericRecord>builder(new Path(file.toUri())).withSchema(schema)
                .withConf(new Configuration()).withCompressionCodec(CompressionCodecName.UNCOMPRESSED).build()) {
            var row = new GenericData.Record(schema); row.put("id", 1L); row.put("status", "PAID"); writer.write(row);
        }
        try (var input = java.nio.file.Files.newInputStream(file)) {
            var columns = inspector.inspect("orders.parquet", input, FileReadOptions.defaults());
            assertEquals(List.of("id", "status"), columns.stream().map(c -> c.name()).toList());
        } finally { java.nio.file.Files.deleteIfExists(file); }
    }

    private ByteArrayInputStream stream(String text) { return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)); }
}
