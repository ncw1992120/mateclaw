package vip.mate.dataagent.dataset.file;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.apache.hadoop.conf.Configuration;
import org.apache.parquet.avro.AvroParquetReader;
import org.apache.parquet.avro.AvroReadSupport;
import org.apache.parquet.filter2.compat.FilterCompat;
import org.apache.parquet.filter2.predicate.FilterApi;
import org.apache.parquet.filter2.predicate.FilterPredicate;
import org.apache.parquet.hadoop.ParquetFileReader;
import org.apache.parquet.hadoop.metadata.ParquetMetadata;
import org.apache.parquet.format.converter.ParquetMetadataConverter;
import org.apache.hadoop.fs.Path;
import org.apache.poi.ss.usermodel.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vip.mate.dataagent.dataset.*;
import vip.mate.dataagent.model.DatasetEntity;
import vip.mate.dataagent.model.DatasetFieldEntity;
import vip.mate.dataagent.repository.DatasetFieldMapper;
import vip.mate.dataagent.repository.DatasetMapper;
import vip.mate.dataagent.objectref.ObjectRefService;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** 受控对象引用上的 CSV/JSON 流式读取；不接受本地路径或 URL。 */
@Component
@RequiredArgsConstructor
public class FileDatasetAdapter implements DatasetSourceAdapter {
    private static final int MAX_PAGE_SIZE = 10_000;
    private static final int MAX_SCAN_ROWS = 50_000;
    private final DatasetMapper datasetMapper;
    private final DatasetFieldMapper fieldMapper;
    private final ObjectRefService objectRefService;
    private final ObjectMapper objectMapper;

    @Override public boolean supports(DatasetSourceType sourceType) { return sourceType == DatasetSourceType.FILE; }

    @Override public DatasetInputDescriptor describe(DatasetAccessContext context, long datasetId) {
        DatasetEntity dataset = require(context, datasetId);
        List<DatasetColumn> columns = fieldMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DatasetFieldEntity>()
                .eq(DatasetFieldEntity::getDatasetId, datasetId).orderByAsc(DatasetFieldEntity::getOrdinalPosition)).stream()
                .map(f -> new DatasetColumn(f.getColumnName(), f.getColumnAlias(), f.getDataType(), !Boolean.FALSE.equals(f.getNullable()), f.getFieldCategory())).toList();
        return new DatasetInputDescriptor(datasetId, name(dataset), DatasetSourceType.FILE, columns, dataset.getRowCount(), Map.of(), null);
    }

    @Override public DatasetBatch read(DatasetAccessContext context, DatasetReadRequest request) {
        DatasetEntity dataset = require(context, request.datasetId());
        FileDatasetDefinition definition = definition(dataset);
        ObjectRef ref = definition.objectRef();
        if (ref.format() == null || !ref.format().equalsIgnoreCase(definition.format()) || ref.expiresAt() <= System.currentTimeMillis())
            throw new DatasetReadException(DatasetReadErrorCode.INVALID_REQUEST, "文件对象引用格式或有效期无效");
        List<Map<String,Object>> rows = new ArrayList<>();
        final List<Map<String,Object>> scanRows = rows;
        int offset = request.offset() == null ? 0 : request.offset();
        int limit = Math.min(request.limit() == null ? MAX_PAGE_SIZE : request.limit(), MAX_PAGE_SIZE);
        int[] matched = {0};
        ParquetScanResult[] parquetResult = {null};
        // 文件源端不支持排序：orders 非空时先收集有界全量匹配行，扫描完成后本地排序再切片
        boolean residualSort = request.orders() != null && !request.orders().isEmpty();
        int collectLimit = residualSort ? MAX_SCAN_ROWS : limit;
        try (InputStream in = objectRefService.open(context, ref)) {
            RowConsumer consumer = row -> {
                if (!matches(row, request.filters())) return;
                // 残余排序时收集全部匹配行（有界），offset 在排序后统一应用
                if (matched[0]++ < (residualSort ? 0 : offset) || scanRows.size() >= collectLimit) return;
                scanRows.add(project(row, request.columns()));
            };
            switch (definition.format().toLowerCase(Locale.ROOT)) {
                case "json" -> scanJson(in, consumer);
                case "csv" -> scanCsv(in, consumer);
                case "txt" -> scanTxt(in, consumer);
                case "xls", "excel", "xlsx" -> scanXlsx(in, consumer);
                case "parquet" -> parquetResult[0] = scanParquet(in, consumer, request);
                default -> throw new DatasetReadException(DatasetReadErrorCode.INVALID_REQUEST, "不支持的文件格式: " + definition.format());
            }
        } catch (DatasetReadException e) { throw e; }
        catch (IOException e) { throw new DatasetReadException(DatasetReadErrorCode.SOURCE_UNAVAILABLE, "文件读取失败", e); }
        if (matched[0] - offset > MAX_SCAN_ROWS) {
            throw new DatasetReadException(DatasetReadErrorCode.RESULT_LIMIT_EXCEEDED,
                    "文件匹配行数超过有界残余处理上限: " + MAX_SCAN_ROWS);
        }
        if (residualSort) {
            ResidualRowOperations.sort(rows, request.orders());
            rows = new ArrayList<>(ResidualRowOperations.paginate(rows, limit, offset));
        }
        PushdownReport report = parquetResult[0] == null
                ? new PushdownReport(List.of(), request.filters(), List.of(), !request.columns().isEmpty(), !residualSort, false, "file-stream-scan")
                : new PushdownReport(parquetResult[0].pushedFilters(), parquetResult[0].residualFilters(),
                parquetResult[0].ordersPushed(), parquetResult[0].projectionPushed(), parquetResult[0].limitPushed(),
                false, parquetResult[0].sourceQueryDigest());
        return new DatasetBatch(rows, null, rows.size(), true, report);
    }

    /** 上传后的文件草稿样本读取；只接受受控 StoredFileRef，不创建 DatasetEntity。 */
    public DatasetBatch previewDraft(DatasetAccessContext context, StoredFileRef stored, String format, int limit) {
        if (stored == null || context == null || !Objects.equals(context.workspaceId(), stored.workspaceId()))
            throw new DatasetReadException(DatasetReadErrorCode.ACCESS_DENIED, "文件引用上下文不匹配");
        String resolved = format == null || format.isBlank() ? stored.format() : format;
        if (!stored.format().equalsIgnoreCase(resolved))
            throw new DatasetReadException(DatasetReadErrorCode.INVALID_REQUEST, "文件格式与上传对象不一致");
        List<Map<String,Object>> rows = new ArrayList<>();
        int max = Math.min(Math.max(limit, 1), 100);
        try (InputStream in = openStored(context, stored)) {
            RowConsumer consumer = row -> { if (rows.size() < max) rows.add(row); };
            switch (resolved.toLowerCase(Locale.ROOT)) {
                case "json" -> scanJson(in, consumer);
                case "csv" -> scanCsv(in, consumer);
                case "txt" -> scanTxt(in, consumer);
                case "xls", "excel", "xlsx" -> scanXlsx(in, consumer);
                case "parquet" -> scanParquet(in, consumer, new DatasetReadRequest(0L, "draft", List.of(), List.of(), max, 0, Map.of()));
                default -> throw new DatasetReadException(DatasetReadErrorCode.INVALID_REQUEST, "不支持的文件格式: " + resolved);
            }
        } catch (DatasetReadException e) { throw e; }
        catch (IOException e) { throw new DatasetReadException(DatasetReadErrorCode.SOURCE_UNAVAILABLE, "文件草稿预览失败", e); }
        return new DatasetBatch(rows, null, rows.size(), true,
                new PushdownReport(List.of(), List.of(), false, false, "file-draft-preview"));
    }

    private InputStream openStored(DatasetAccessContext context, StoredFileRef stored) {
        ObjectRef ref = new ObjectRef(stored.objectId(), stored.workspaceId(), "upload-" + stored.ownerId(), stored.format(), stored.digest(),
                System.currentTimeMillis() + 86_400_000L);
        return objectRefService.open(context, ref);
    }

    private DatasetEntity require(DatasetAccessContext context, long id) {
        if (context == null || !context.canRead(id)) throw new DatasetReadException(DatasetReadErrorCode.ACCESS_DENIED, "无权读取数据集");
        DatasetEntity d = datasetMapper.selectById(id);
        if (d == null) throw new DatasetReadException(DatasetReadErrorCode.INVALID_REQUEST, "数据集不存在: " + id);
        if (!DatasetSourceType.FILE.name().equalsIgnoreCase(d.getSourceType())) throw new DatasetReadException(DatasetReadErrorCode.INVALID_REQUEST, "数据集不是文件类型");
        return d;
    }
    private FileDatasetDefinition definition(DatasetEntity d) {
        try {
            FileDatasetDefinition definition = objectMapper.readValue(d.getSourceConfig(), FileDatasetDefinition.class);
            if (d.getSchemaVersion() != null && d.getSchemaVersion() != definition.schemaVersion()) {
                throw new DatasetReadException(DatasetReadErrorCode.SCHEMA_MISMATCH,
                        "文件数据集 Schema 版本不匹配: dataset=" + d.getSchemaVersion() + ", object=" + definition.schemaVersion());
            }
            return definition;
        }
        catch (DatasetReadException e) { throw e; }
        catch (Exception e) { throw new DatasetReadException(DatasetReadErrorCode.INVALID_REQUEST, "文件数据集配置无效", e); }
    }
    private void scanJson(InputStream in, RowConsumer consumer) throws IOException { try (var parser=objectMapper.getFactory().createParser(in)) { if(parser.nextToken()!= JsonToken.START_ARRAY) throw new IOException("JSON must be an array"); while(parser.nextToken()!=JsonToken.END_ARRAY) consumer.accept(objectMapper.readValue(parser,new TypeReference<Map<String,Object>>(){})); } }
    private void scanCsv(InputStream in, RowConsumer consumer) throws IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)); String h=r.readLine(); if(h==null) return;
        List<String> names=parse(h); String line;
        while((line=r.readLine())!=null){ if(line.isBlank()) continue; List<String> vals=parse(line); if(vals.size()!=names.size()) throw new IOException("CSV row mismatch"); Map<String,Object> row=new LinkedHashMap<>(); for(int i=0;i<names.size();i++) row.put(names.get(i), vals.get(i).isBlank()?null:vals.get(i)); consumer.accept(row); }
    }
    /** TXT 按首行探测逗号、制表符或分号分隔，仍使用受限 UTF-8 流式读取。 */
    private void scanTxt(InputStream in, RowConsumer consumer) throws IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        String header = r.readLine(); if (header == null) return;
        char delimiter = header.indexOf('\t') >= 0 ? '\t' : (header.indexOf(';') >= 0 ? ';' : ',');
        List<String> names = splitLine(header, delimiter); String line;
        while ((line = r.readLine()) != null) {
            if (line.isBlank()) continue;
            List<String> values = splitLine(line, delimiter);
            if (values.size() != names.size()) throw new IOException("TXT row mismatch");
            Map<String,Object> row = new LinkedHashMap<>();
            for (int i = 0; i < names.size(); i++) row.put(names.get(i), values.get(i).isBlank() ? null : values.get(i));
            consumer.accept(row);
        }
    }
    private List<String> splitLine(String line, char delimiter) { return Arrays.asList(line.split(java.util.regex.Pattern.quote(String.valueOf(delimiter)), -1)); }
    private void scanXlsx(InputStream in, RowConsumer consumer) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(in)) {
            if (workbook.getNumberOfSheets() == 0) throw new IOException("XLSX contains no sheet");
            Sheet sheet = workbook.getSheetAt(0); Row header = sheet.getRow(sheet.getFirstRowNum()); if (header == null) return;
            DataFormatter formatter = new DataFormatter(); List<String> names = new ArrayList<>(); for (Cell c : header) names.add(formatter.formatCellValue(c));
            for (int i=header.getRowNum()+1;i<=sheet.getLastRowNum();i++) { Row r=sheet.getRow(i); if(r==null)continue; Map<String,Object> row=new LinkedHashMap<>(); for(int c=0;c<names.size();c++){Cell cell=r.getCell(c);String v=cell==null?"":formatter.formatCellValue(cell);row.put(names.get(c),v.isBlank()?null:v);} consumer.accept(row); }
        } catch (DatasetReadException e) { throw e; } catch (Exception e) { throw new IOException("invalid XLSX", e); }
    }
    private ParquetScanResult scanParquet(InputStream in, RowConsumer consumer, DatasetReadRequest request) throws IOException {
        Path temp = new Path(java.nio.file.Files.createTempFile("mateclaw-file-", ".parquet").toUri());
        try {
            try (OutputStream out=java.nio.file.Files.newOutputStream(java.nio.file.Path.of(temp.toUri()))) { in.transferTo(out); }
            ParquetMetadata footer = ParquetFileReader.readFooter(new Configuration(), temp, ParquetMetadataConverter.NO_FILTER);
            String schemaJson = footer.getFileMetaData().getKeyValueMetaData().get("parquet.avro.schema");
            if (schemaJson == null) throw new IOException("Parquet 缺少 Avro Schema 元数据");
            Schema fullSchema = new Schema.Parser().parse(schemaJson);
            PushdownPlan plan = buildPushdownPlan(fullSchema, request);
            Configuration conf = new Configuration();
            if (plan.projectionSchema() != null) AvroReadSupport.setRequestedProjection(conf, plan.projectionSchema());
            var builder = AvroParquetReader.<GenericRecord>builder(temp).withConf(conf);
            if (plan.predicate() != null) builder.withFilter(FilterCompat.get(plan.predicate()));
            try (var reader=builder.build()) {
                GenericRecord record;
                while((record=reader.read())!=null){
                    Map<String,Object> row=new LinkedHashMap<>();
                    for (Schema.Field field : record.getSchema().getFields()) {
                        Object value = record.get(field.name());
                        if (value instanceof org.apache.avro.util.Utf8 utf8) value = utf8.toString();
                        row.put(field.name(), value);
                    }
                    consumer.accept(row);
                }
            }
            return new ParquetScanResult(plan.pushedFilters(), plan.residualFilters(), plan.projectionSchema() != null, false, "parquet-filter-pushdown");
        } finally { java.nio.file.Files.deleteIfExists(java.nio.file.Path.of(temp.toUri())); }
    }

    private PushdownPlan buildPushdownPlan(Schema fullSchema, DatasetReadRequest request) {
        List<DatasetFilter> pushed = new ArrayList<>();
        List<DatasetFilter> residual = new ArrayList<>();
        FilterPredicate predicate = null;
        for (DatasetFilter filter : request.filters()) {
            FilterPredicate compiled = compilePredicate(fullSchema, filter);
            if (compiled == null) residual.add(filter);
            else { pushed.add(filter); predicate = predicate == null ? compiled : FilterApi.and(predicate, compiled); }
        }
        LinkedHashSet<String> names = new LinkedHashSet<>(request.columns());
        request.filters().forEach(f -> names.add(f.field()));
        Schema projection = null;
        if (!request.columns().isEmpty()) {
            List<Schema.Field> fields = new ArrayList<>();
            for (String name : names) {
                Schema.Field source = fullSchema.getField(name);
                if (source == null) throw new DatasetReadException(DatasetReadErrorCode.INVALID_REQUEST, "未知文件字段: " + name);
                fields.add(new Schema.Field(name, source.schema(), source.doc(), source.defaultVal()));
            }
            projection = Schema.createRecord(fullSchema.getName(), fullSchema.getDoc(), fullSchema.getNamespace(), false);
            projection.setFields(fields);
        }
        return new PushdownPlan(predicate, pushed, residual, projection);
    }

    private FilterPredicate compilePredicate(Schema schema, DatasetFilter filter) {
        Schema.Field field = schema.getField(filter.field());
        if (field == null) return null;
        String op = filter.operator().toLowerCase(Locale.ROOT);
        if (!(Set.of("eq", "neq", "gt", "gte", "lt", "lte").contains(op))) return null;
        Schema type = field.schema().getTypes().stream().filter(s -> s.getType() != Schema.Type.NULL).findFirst().orElse(field.schema());
        Object value = filter.value();
        try {
            return switch (type.getType()) {
                case BOOLEAN -> compare(FilterApi.booleanColumn(filter.field()), op, Boolean.valueOf(String.valueOf(value)));
                case INT -> compare(FilterApi.intColumn(filter.field()), op, ((Number) value).intValue());
                case LONG -> compare(FilterApi.longColumn(filter.field()), op, ((Number) value).longValue());
                case FLOAT -> compare(FilterApi.floatColumn(filter.field()), op, ((Number) value).floatValue());
                case DOUBLE -> compare(FilterApi.doubleColumn(filter.field()), op, ((Number) value).doubleValue());
                case STRING -> compare(FilterApi.binaryColumn(filter.field()), op, org.apache.parquet.io.api.Binary.fromString(String.valueOf(value)));
                default -> null;
            };
        } catch (RuntimeException ignored) { return null; }
    }

    private <T extends Comparable<T>> FilterPredicate compare(org.apache.parquet.filter2.predicate.Operators.Column<T> column, String op, T value) {
        return switch (op) {
            case "eq" -> FilterApi.eq((org.apache.parquet.filter2.predicate.Operators.Column<T> & org.apache.parquet.filter2.predicate.Operators.SupportsEqNotEq) column, value);
            case "neq" -> FilterApi.notEq((org.apache.parquet.filter2.predicate.Operators.Column<T> & org.apache.parquet.filter2.predicate.Operators.SupportsEqNotEq) column, value);
            case "gt" -> FilterApi.gt((org.apache.parquet.filter2.predicate.Operators.Column<T> & org.apache.parquet.filter2.predicate.Operators.SupportsLtGt) column, value);
            case "gte" -> FilterApi.gtEq((org.apache.parquet.filter2.predicate.Operators.Column<T> & org.apache.parquet.filter2.predicate.Operators.SupportsLtGt) column, value);
            case "lt" -> FilterApi.lt((org.apache.parquet.filter2.predicate.Operators.Column<T> & org.apache.parquet.filter2.predicate.Operators.SupportsLtGt) column, value);
            case "lte" -> FilterApi.ltEq((org.apache.parquet.filter2.predicate.Operators.Column<T> & org.apache.parquet.filter2.predicate.Operators.SupportsLtGt) column, value);
            default -> throw new IllegalArgumentException("unsupported operator");
        };
    }

    private record PushdownPlan(FilterPredicate predicate, List<DatasetFilter> pushedFilters,
                                List<DatasetFilter> residualFilters, Schema projectionSchema) {}
    private record ParquetScanResult(List<DatasetFilter> pushedFilters, List<DatasetFilter> residualFilters,
                                     List<DatasetSort> ordersPushed, boolean projectionPushed,
                                     boolean limitPushed, String sourceQueryDigest) {
        ParquetScanResult(List<DatasetFilter> pushedFilters, List<DatasetFilter> residualFilters,
                          boolean projectionPushed, boolean limitPushed, String sourceQueryDigest) {
            this(pushedFilters, residualFilters, List.of(), projectionPushed, limitPushed, sourceQueryDigest);
        }
    }
    private List<String> parse(String line){ List<String> out=new ArrayList<>(); StringBuilder b=new StringBuilder(); boolean q=false; for(int i=0;i<line.length();i++){char c=line.charAt(i); if(c=='"') q=!q; else if(c==','&&!q){out.add(b.toString());b.setLength(0);} else b.append(c);} if(q) throw new DatasetReadException(DatasetReadErrorCode.INVALID_REQUEST,"CSV quote invalid"); out.add(b.toString()); return out; }
    private boolean matches(Map<String,Object> row,List<DatasetFilter> fs){
        for (DatasetFilter f : fs) {
            Object actual = row.get(f.field());
            String op = f.operator().toLowerCase(Locale.ROOT);
            Object expected = f.value();
            boolean ok = switch (op) {
                case "eq" -> equalValue(actual, expected);
                case "neq" -> !equalValue(actual, expected);
                case "gt" -> compareValue(actual, expected) > 0;
                case "gte" -> compareValue(actual, expected) >= 0;
                case "lt" -> compareValue(actual, expected) < 0;
                case "lte" -> compareValue(actual, expected) <= 0;
                case "in" -> asValues(expected).stream().anyMatch(v -> equalValue(actual, v));
                case "not_in" -> asValues(expected).stream().noneMatch(v -> equalValue(actual, v));
                case "between" -> {
                    List<?> values = asValues(expected);
                    if (values.size() != 2) throw new DatasetReadException(DatasetReadErrorCode.INVALID_REQUEST, "between 需要两个边界值");
                    yield compareValue(actual, values.get(0)) >= 0 && compareValue(actual, values.get(1)) <= 0;
                }
                case "is_null" -> actual == null;
                case "is_not_null" -> actual != null;
                default -> throw new DatasetReadException(DatasetReadErrorCode.UNSUPPORTED_FILTER, "文件过滤操作不支持: " + op);
            };
            if (!ok) return false;
        }
        return true;
    }

    private List<?> asValues(Object value) {
        if (value instanceof Collection<?> collection) return List.copyOf(collection);
        if (value != null && value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            List<Object> values = new ArrayList<>(length);
            for (int i = 0; i < length; i++) values.add(java.lang.reflect.Array.get(value, i));
            return values;
        }
        throw new DatasetReadException(DatasetReadErrorCode.INVALID_REQUEST, "集合过滤值必须是数组");
    }

    private boolean equalValue(Object actual, Object expected) {
        if (actual == null || expected == null) return actual == expected;
        try { return new BigDecimal(String.valueOf(actual)).compareTo(new BigDecimal(String.valueOf(expected))) == 0; }
        catch (NumberFormatException ignored) { return String.valueOf(actual).equals(String.valueOf(expected)); }
    }

    private int compareValue(Object actual, Object expected) {
        if (actual == null || expected == null) return actual == expected ? 0 : actual == null ? -1 : 1;
        try { return new BigDecimal(String.valueOf(actual)).compareTo(new BigDecimal(String.valueOf(expected))); }
        catch (NumberFormatException ignored) { return String.valueOf(actual).compareTo(String.valueOf(expected)); }
    }
    private Map<String,Object> project(Map<String,Object> row,List<String> cols){ if(cols.isEmpty()) return row; Map<String,Object> out=new LinkedHashMap<>(); cols.forEach(c->{if(!row.containsKey(c)) throw new DatasetReadException(DatasetReadErrorCode.INVALID_REQUEST,"未知文件字段: "+c); out.put(c,row.get(c));}); return out; }
    private String name(DatasetEntity d){return d.getName()==null||d.getName().isBlank()?"dataset-"+d.getId():d.getName();}
    @FunctionalInterface private interface RowConsumer { void accept(Map<String,Object> row) throws IOException; }
}
