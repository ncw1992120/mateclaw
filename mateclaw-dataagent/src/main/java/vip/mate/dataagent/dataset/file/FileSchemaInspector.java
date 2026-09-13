package vip.mate.dataagent.dataset.file;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import vip.mate.dataagent.dataset.DatasetColumn;
import vip.mate.dataagent.dataset.DatasetReadErrorCode;
import vip.mate.dataagent.dataset.DatasetReadException;
import org.apache.poi.ss.usermodel.*;
import org.apache.parquet.avro.AvroParquetReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.hadoop.fs.Path;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** 对登记后的文件流做有上限的 Schema 探测；不接受路径或 URL。 */
public final class FileSchemaInspector {
    private final ObjectMapper mapper;
    public FileSchemaInspector(ObjectMapper mapper) { this.mapper = Objects.requireNonNull(mapper); }

    public List<DatasetColumn> inspect(String fileName, InputStream input, FileReadOptions options) {
        if (fileName == null || input == null) throw invalid("fileName and input are required");
        FileReadOptions limits = options == null ? FileReadOptions.defaults() : options;
        String format = format(fileName);
        try (InputStream bounded = new BoundedInputStream(input, limits.maxBytes())) {
            return switch (format) {
                case "csv" -> inspectRows(parseCsv(new BufferedReader(new InputStreamReader(bounded, StandardCharsets.UTF_8)), limits.sampleRows()), limits);
                case "json" -> inspectJson(bounded, limits);
                case "xlsx" -> inspectXlsx(bounded, limits);
                case "parquet" -> inspectParquet(bounded, limits);
                default -> throw invalid("format is not implemented by the basic inspector: " + format);
            };
        } catch (DatasetReadException e) { throw e; }
        catch (IOException e) { throw new DatasetReadException(DatasetReadErrorCode.INVALID_REQUEST, "文件内容无效", e); }
    }

    private List<DatasetColumn> inspectXlsx(InputStream input, FileReadOptions limits) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(input)) {
            if (workbook.getNumberOfSheets() == 0) throw invalid("XLSX contains no sheet");
            Sheet sheet = workbook.getSheetAt(0); Row header = sheet.getRow(sheet.getFirstRowNum());
            if (header == null) throw invalid("XLSX header is empty");
            DataFormatter formatter = new DataFormatter(); List<String> names = new ArrayList<>();
            for (Cell cell : header) names.add(formatter.formatCellValue(cell).trim());
            if (names.isEmpty() || names.size() > limits.maxColumns() || names.stream().anyMatch(String::isBlank)
                    || new HashSet<>(names).size() != names.size()) throw invalid("XLSX header is invalid");
            List<Map<String,Object>> rows = new ArrayList<>();
            for (int i = header.getRowNum()+1; i <= sheet.getLastRowNum() && rows.size() < limits.sampleRows(); i++) {
                Row row = sheet.getRow(i); if (row == null) continue; Map<String,Object> values = new LinkedHashMap<>();
                for (int c=0;c<names.size();c++) { Cell cell=row.getCell(c); String value=cell==null?"":formatter.formatCellValue(cell); values.put(names.get(c), value.isBlank()?null:value); }
                rows.add(values);
            }
            return inspectRows(rows, limits);
        } catch (DatasetReadException e) { throw e; }
        catch (Exception e) { throw new IOException("invalid XLSX", e); }
    }

    private List<DatasetColumn> inspectParquet(InputStream input, FileReadOptions limits) throws IOException {
        java.nio.file.Path temp = java.nio.file.Files.createTempFile("mateclaw-parquet-", ".parquet");
        try { java.nio.file.Files.copy(input, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            List<Map<String,Object>> rows = new ArrayList<>();
            try (var reader = AvroParquetReader.<GenericRecord>builder(new Path(temp.toUri())).build()) {
                GenericRecord record; while (rows.size() < limits.sampleRows() && (record = reader.read()) != null) {
                    GenericRecord current = record;
                    Map<String,Object> row = new LinkedHashMap<>(); current.getSchema().getFields().forEach(f -> row.put(f.name(), current.get(f.name()))); rows.add(row);
                }
            }
            return inspectRows(rows, limits);
        } finally { java.nio.file.Files.deleteIfExists(temp); }
    }

    private List<DatasetColumn> inspectJson(InputStream input, FileReadOptions limits) throws IOException {
        JsonNode root = mapper.readTree(input);
        if (root == null || !root.isArray() || root.isEmpty()) throw invalid("JSON must be a non-empty array");
        List<Map<String,Object>> rows = mapper.convertValue(root, new TypeReference<>() {});
        return inspectRows(rows.subList(0, Math.min(rows.size(), limits.sampleRows())), limits);
    }

    private List<DatasetColumn> inspectRows(List<Map<String,Object>> rows, FileReadOptions limits) {
        if (rows.isEmpty()) throw invalid("file contains no data rows");
        LinkedHashSet<String> names = new LinkedHashSet<>();
        rows.forEach(row -> names.addAll(row.keySet()));
        if (names.isEmpty()) throw invalid("file contains no columns");
        if (names.size() > limits.maxColumns()) throw new DatasetReadException(DatasetReadErrorCode.RESULT_LIMIT_EXCEEDED, "column count exceeds limit");
        List<DatasetColumn> columns = new ArrayList<>();
        for (String name : names) {
            boolean nullable = rows.stream().anyMatch(r -> r.get(name) == null);
            String type = inferType(rows.stream().map(r -> r.get(name)).filter(Objects::nonNull).toList());
            columns.add(new DatasetColumn(name, name, type, nullable, numeric(type) ? "measure" : "dimension"));
        }
        return List.copyOf(columns);
    }

    private String inferType(List<Object> values) {
        if (values.isEmpty()) return "string";
        if (values.stream().allMatch(v -> v instanceof Boolean || "true".equalsIgnoreCase(String.valueOf(v)) || "false".equalsIgnoreCase(String.valueOf(v)))) return "boolean";
        if (values.stream().allMatch(v -> v instanceof Number || String.valueOf(v).matches("[-+]?\\d+"))) return "integer";
        if (values.stream().allMatch(v -> v instanceof Number || String.valueOf(v).matches("[-+]?(\\d+\\.\\d+|\\d+e[-+]?\\d+)"))) return "decimal";
        return "string";
    }
    private boolean numeric(String type) { return "integer".equals(type) || "decimal".equals(type); }

    private List<Map<String,Object>> parseCsv(BufferedReader reader, int maxRows) throws IOException {
        String header = reader.readLine();
        if (header == null || header.isBlank()) throw invalid("CSV header is empty");
        List<String> names = parseLine(header);
        if (names.stream().anyMatch(String::isBlank) || new HashSet<>(names).size() != names.size()) throw invalid("CSV header has duplicate or blank columns");
        List<Map<String,Object>> rows = new ArrayList<>(); String line;
        while (rows.size() < maxRows && (line = reader.readLine()) != null) {
            if (line.isBlank()) continue;
            List<String> fields = parseLine(line);
            if (fields.size() != names.size()) throw invalid("CSV row column count mismatch");
            Map<String,Object> row = new LinkedHashMap<>();
            for (int i=0;i<names.size();i++) row.put(names.get(i), fields.get(i).isBlank() ? null : fields.get(i));
            rows.add(row);
        }
        return rows;
    }
    private List<String> parseLine(String line) {
        List<String> out = new ArrayList<>(); StringBuilder cell = new StringBuilder(); boolean quoted = false;
        for (int i=0;i<line.length();i++) { char c=line.charAt(i); if (c=='"') { if (quoted && i+1<line.length() && line.charAt(i+1)=='"') { cell.append('"'); i++; } else quoted=!quoted; } else if (c==',' && !quoted) { out.add(cell.toString()); cell.setLength(0); } else cell.append(c); }
        if (quoted) throw invalid("unterminated CSV quote"); out.add(cell.toString()); return out;
    }
    private String format(String fileName) { int dot=fileName.lastIndexOf('.'); if (dot<0) throw invalid("file extension is required"); return fileName.substring(dot+1).toLowerCase(Locale.ROOT); }
    private DatasetReadException invalid(String message) { return new DatasetReadException(DatasetReadErrorCode.INVALID_REQUEST, message); }

    private static final class BoundedInputStream extends FilterInputStream {
        private final long max; private long count;
        BoundedInputStream(InputStream in, long max) { super(in); this.max=max; }
        @Override public int read() throws IOException { int v=super.read(); if (v>=0 && ++count>max) throw new DatasetReadException(DatasetReadErrorCode.RESULT_LIMIT_EXCEEDED, "文件大小超过限制"); return v; }
        @Override public int read(byte[] b,int o,int l) throws IOException { int n=super.read(b,o,l); if(n>0 && (count+=n)>max) throw new DatasetReadException(DatasetReadErrorCode.RESULT_LIMIT_EXCEEDED, "文件大小超过限制"); return n; }
    }
}
