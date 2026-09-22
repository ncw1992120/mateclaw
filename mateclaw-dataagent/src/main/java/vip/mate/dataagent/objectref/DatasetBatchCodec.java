package vip.mate.dataagent.objectref;

import org.apache.avro.Schema;
import org.apache.avro.LogicalTypes;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.parquet.avro.AvroParquetReader;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import vip.mate.dataagent.dataset.*;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/** DatasetBatch 与 Parquet ObjectRef 之间的列式编码。 */
public final class DatasetBatchCodec {
    private DatasetBatchCodec() {}

    public static ObjectRef writeParquet(DatasetAccessContext context, DatasetBatch batch, ObjectRefService refs) {
        if (context == null || batch == null || refs == null || batch.rows() == null || batch.rows().isEmpty())
            throw new DatasetReadException(DatasetReadErrorCode.INVALID_REQUEST, "Parquet 编码需要非空内联批次");
        List<String> names = new ArrayList<>(batch.rows().getFirst().keySet());
        if (names.isEmpty() || names.size() > 100) throw new DatasetReadException(DatasetReadErrorCode.RESULT_LIMIT_EXCEEDED, "列数超限");
        if (batch.rows().stream().anyMatch(row -> !row.keySet().equals(new LinkedHashSet<>(names)))) {
            throw new DatasetReadException(DatasetReadErrorCode.INVALID_REQUEST, "Parquet 编码要求批次内每行 Schema 一致");
        }
        Schema schema = schema(names, batch.rows()); java.nio.file.Path temp = null;
        try {
            temp = Files.createTempFile("mateclaw-batch-", ".parquet"); Files.deleteIfExists(temp);
            try (var writer = AvroParquetWriter.<GenericRecord>builder(new Path(temp.toUri())).withSchema(schema)
                    .withConf(new Configuration()).withCompressionCodec(CompressionCodecName.SNAPPY).build()) {
                for (Map<String,Object> row : batch.rows()) { GenericRecord record = new GenericData.Record(schema); for (String name : names) record.put(name, avroValue(row.get(name), schema.getField(name).schema())); writer.write(record); }
            }
            try (InputStream in = Files.newInputStream(temp)) { return refs.put(context, "parquet", in); }
        } catch (DatasetReadException e) { throw e; }
        catch (Exception e) { throw new DatasetReadException(DatasetReadErrorCode.SOURCE_UNAVAILABLE, "Parquet 编码失败", e); }
        finally { if (temp != null) try { Files.deleteIfExists(temp); } catch (Exception ignored) {} }
    }

    public static Schema readSchema(DatasetAccessContext context, ObjectRef reference, ObjectRefService refs) {
        if (context == null || reference == null || refs == null) throw new DatasetReadException(DatasetReadErrorCode.INVALID_REQUEST, "对象引用不能为空");
        try (InputStream in = refs.open(context, reference)) {
            java.nio.file.Path temp = Files.createTempFile("mateclaw-schema-", ".parquet");
            try { Files.copy(in, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING); try (var reader=AvroParquetReader.<GenericRecord>builder(new Path(temp.toUri())).build()) { GenericRecord first = reader.read(); return first == null ? null : first.getSchema(); } }
            finally { Files.deleteIfExists(temp); }
        } catch (DatasetReadException e) { throw e; } catch (Exception e) { throw new DatasetReadException(DatasetReadErrorCode.SOURCE_UNAVAILABLE, "Parquet Schema 读取失败", e); }
    }

    /** 受控读取 Parquet 结果用于页面预览；正式大结果仍保留 ObjectRef，不一次性加载无限数据。 */
    public static List<Map<String, Object>> readRows(DatasetAccessContext context, ObjectRef reference,
                                                       ObjectRefService refs, int maxRows) {
        return readRows(context, reference, refs, 0, maxRows);
    }

    /** 受控分批读取：跳过 offset 行后最多读取 maxRows 行（用于 prepared input 批次游标续读）。 */
    public static List<Map<String, Object>> readRows(DatasetAccessContext context, ObjectRef reference,
                                                     ObjectRefService refs, int offset, int maxRows) {
        if (offset < 0) throw new DatasetReadException(DatasetReadErrorCode.INVALID_REQUEST, "offset 不能为负");
        if (maxRows <= 0 || maxRows > 10_000) throw new DatasetReadException(DatasetReadErrorCode.RESULT_LIMIT_EXCEEDED, "结果行数超限");
        java.nio.file.Path temp = null;
        try (InputStream in = refs.open(context, reference)) {
            temp = Files.createTempFile("mateclaw-result-", ".parquet");
            Files.copy(in, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            List<Map<String, Object>> rows = new ArrayList<>();
            try (var reader = AvroParquetReader.<GenericRecord>builder(new Path(temp.toUri())).build()) {
                GenericRecord record;
                int skipped = 0;
                while (rows.size() < maxRows && (record = reader.read()) != null) {
                    if (skipped++ < offset) continue;
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (Schema.Field field : record.getSchema().getFields()) {
                        Object value = record.get(field.name());
                        if (value instanceof org.apache.avro.util.Utf8 utf8) value = utf8.toString();
                        value = fromAvroValue(value, field.schema());
                        row.put(field.name(), value);
                    }
                    rows.add(row);
                }
            }
            return rows;
        } catch (DatasetReadException e) { throw e;
        } catch (Exception e) { throw new DatasetReadException(DatasetReadErrorCode.SOURCE_UNAVAILABLE, "Parquet 结果读取失败", e);
        } finally { if (temp != null) try { Files.deleteIfExists(temp); } catch (Exception ignored) {} }
    }

    private static Schema schema(List<String> names, List<Map<String,Object>> rows) {
        List<Schema.Field> fields = new ArrayList<>();
        for (String name : names) {
            Schema type = fieldType(name, rows);
            fields.add(new Schema.Field(name, Schema.createUnion(List.of(Schema.create(Schema.Type.NULL), type)), null, (Object) null));
        }
        Schema record=Schema.createRecord("DatasetBatch", null, "vip.mate.dataagent", false); record.setFields(fields); return record;
    }
    private static Schema fieldType(String name, List<Map<String,Object>> rows) {
        Object sample = rows.stream().map(r -> r.get(name)).filter(Objects::nonNull).findFirst().orElse("");
        if (sample instanceof BigDecimal) {
            int scale = rows.stream().map(r -> r.get(name)).filter(BigDecimal.class::isInstance)
                    .mapToInt(v -> ((BigDecimal) v).scale()).max().orElse(((BigDecimal) sample).scale());
            int precision = rows.stream().map(r -> r.get(name)).filter(BigDecimal.class::isInstance)
                    .mapToInt(v -> {
                        BigDecimal decimal = (BigDecimal) v;
                        return decimal.precision() + Math.max(0, scale - decimal.scale());
                    }).max().orElse(((BigDecimal) sample).precision());
            return LogicalTypes.decimal(Math.max(precision, scale + 1), scale)
                    .addToSchema(Schema.create(Schema.Type.BYTES));
        }
        if (sample instanceof LocalDate) return LogicalTypes.date().addToSchema(Schema.create(Schema.Type.INT));
        if (sample instanceof Instant) return LogicalTypes.timestampMillis().addToSchema(Schema.create(Schema.Type.LONG));
        return primitive(sample);
    }
    private static Schema primitive(Object value) { if(value instanceof Boolean)return Schema.create(Schema.Type.BOOLEAN); if(value instanceof Byte||value instanceof Short||value instanceof Integer||value instanceof Long)return Schema.create(Schema.Type.LONG); if(value instanceof Float||value instanceof Double||value instanceof Number)return Schema.create(Schema.Type.DOUBLE); return Schema.create(Schema.Type.STRING); }
    private static Object avroValue(Object value, Schema union) {
        if(value==null)return null;
        Schema type=union.getTypes().stream().filter(s->s.getType()!=Schema.Type.NULL).findFirst().orElse(union);
        if (type.getLogicalType() instanceof LogicalTypes.Decimal decimal) {
            BigDecimal decimalValue = ((BigDecimal) value).setScale(decimal.getScale());
            return ByteBuffer.wrap(decimalValue.unscaledValue().toByteArray());
        }
        if (type.getLogicalType() == LogicalTypes.date()) return ((LocalDate) value).toEpochDay();
        if (type.getLogicalType() == LogicalTypes.timestampMillis()) return ((Instant) value).toEpochMilli();
        return switch(type.getType()){case LONG->((Number)value).longValue();case DOUBLE->((Number)value).doubleValue();case BOOLEAN->(value instanceof Boolean?value:Boolean.valueOf(String.valueOf(value)));default->String.valueOf(value);};
    }
    private static Object fromAvroValue(Object value, Schema union) {
        if (value == null) return null;
        Schema type = union.getTypes().stream().filter(s -> s.getType() != Schema.Type.NULL).findFirst().orElse(union);
        if (type.getLogicalType() instanceof LogicalTypes.Decimal decimal) {
            ByteBuffer bytes = value instanceof ByteBuffer buffer ? buffer : ByteBuffer.wrap((byte[]) value);
            byte[] raw = new byte[bytes.remaining()]; bytes.get(raw);
            return new BigDecimal(new BigInteger(raw), decimal.getScale());
        }
        if (type.getLogicalType() == LogicalTypes.date()) return LocalDate.ofEpochDay(((Number) value).longValue());
        if (type.getLogicalType() == LogicalTypes.timestampMillis()) return Instant.ofEpochMilli(((Number) value).longValue());
        return value;
    }
}
