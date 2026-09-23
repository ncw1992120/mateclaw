package vip.mate.dataagent.service;

import org.springframework.stereotype.Service;
import vip.mate.dataagent.dto.FinalResultQueryConfigDTO;
import vip.mate.dataagent.dto.FinalResultQueryContextDTO;
import vip.mate.dataagent.dto.QueryContextDTO;
import vip.mate.dataagent.service.code.ScriptResultContractService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class FinalResultQueryServiceImpl implements FinalResultQueryService {

    @Override
    public FinalResultPage query(ScriptResultContractService.ValidatedEnvelope envelope,
                                 FinalResultQueryConfigDTO config,
                                 FinalResultQueryContextDTO context) {
        Objects.requireNonNull(envelope, "result envelope is required");
        Objects.requireNonNull(config, "final result query config is required");
        Objects.requireNonNull(context, "final result query context is required");
        if (!"table".equals(envelope.kind())) {
            if (!context.parameters().isEmpty() || context.sort() != null || context.pagination() != null) {
                throw new IllegalArgumentException("final result query only supports table output");
            }
            return new FinalResultPage(envelope, 0, 1, 0);
        }

        var table = envelope.data();
        var columns = table.columns();
        var columnByName = new HashMap<String, ScriptResultContractService.ValidatedEnvelope.Column>();
        columns.forEach(column -> columnByName.put(column.name(), column));
        var filterByParameter = new HashMap<String, FinalResultQueryConfigDTO.ParameterBinding>();
        for (var binding : config.parameterBindings()) {
            var filter = config.filterFields().stream()
                    .filter(candidate -> candidate.field().equals(binding.field()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("parameter binding field is not filterable: " + binding.field()));
            if (!columnByName.containsKey(binding.field()) || !filter.operators().contains(binding.operator())) {
                throw new IllegalArgumentException("parameter binding is not allowed: " + binding.parameterName());
            }
            if (filterByParameter.put(binding.parameterName(), binding) != null) {
                throw new IllegalArgumentException("duplicate parameter binding: " + binding.parameterName());
            }
        }

        List<Map<String, Object>> rows = new ArrayList<>(table.rows());
        rows.removeIf(row -> !matches(row, context.parameters(), filterByParameter, columnByName));
        int totalRows = rows.size();

        QueryContextDTO.SortSpec sort = context.sort();
        if (sort != null) {
            if (!config.sortPolicy().enabled() || !config.sortPolicy().allowedFields().contains(sort.field())
                    || !columnByName.containsKey(sort.field())) {
                throw new IllegalArgumentException("sort field is not allowed: " + sort.field());
            }
            Comparator<Map<String, Object>> comparator = Comparator.comparing(
                    row -> (ComparableValue) ComparableValue.of(row.get(sort.field()), columnByName.get(sort.field()).dataType()),
                    Comparator.nullsLast(Comparator.naturalOrder()));
            if ("desc".equals(sort.direction())) comparator = comparator.reversed();
            rows.sort(comparator);
        }

        QueryContextDTO.PaginationSpec pagination = context.pagination();
        int page = 1;
        int pageSize = rows.size();
        if (pagination != null) {
            if (!config.paginationPolicy().enabled()) {
                throw new IllegalArgumentException("pagination is not enabled");
            }
            if (pagination.pageSize() > config.paginationPolicy().maxPageSize()) {
                throw new IllegalArgumentException("pagination.pageSize exceeds final result max");
            }
            page = pagination.page();
            pageSize = pagination.pageSize();
            int from = Math.min(pagination.offset(), rows.size());
            int to = Math.min(from + pagination.pageSize(), rows.size());
            rows = new ArrayList<>(rows.subList(from, to));
        }

        List<ScriptResultContractService.ValidatedEnvelope.Column> resultColumns = columns;
        if (!config.displayFields().isEmpty()) {
            resultColumns = config.displayFields().stream().map(field -> {
                var column = columnByName.get(field.field());
                if (column == null) throw new IllegalArgumentException("display field is not in result schema: " + field.field());
                return column;
            }).toList();
            var selectedColumns = resultColumns;
            rows = rows.stream().map(row -> {
                var projected = new LinkedHashMap<String, Object>();
                selectedColumns.forEach(column -> projected.put(column.name(), row.get(column.name())));
                return (Map<String, Object>) projected;
            }).toList();
        }

        var result = new ScriptResultContractService.ValidatedEnvelope(
                envelope.schemaVersion(), envelope.kind(),
                new ScriptResultContractService.ValidatedEnvelope.TableData(resultColumns, rows),
                new ScriptResultContractService.ValidatedEnvelope.Meta(totalRows, rows.size() < totalRows,
                        envelope.meta().sourceInputs()));
        return new FinalResultPage(result, totalRows, page, pageSize);
    }

    private boolean matches(Map<String, Object> row, Map<String, Object> parameters,
                            Map<String, FinalResultQueryConfigDTO.ParameterBinding> bindings,
                            Map<String, ScriptResultContractService.ValidatedEnvelope.Column> columns) {
        for (var entry : parameters.entrySet()) {
            var binding = bindings.get(entry.getKey());
            if (binding == null) throw new IllegalArgumentException("parameter binding is not allowed: " + entry.getKey());
            Object actual = row.get(binding.field());
            if (!matches(actual, entry.getValue(), binding.operator(), columns.get(binding.field()).dataType())) return false;
        }
        return true;
    }

    private boolean matches(Object actual, Object expected, String operator, String dataType) {
        return switch (operator) {
            case "eq" -> Objects.equals(actual, expected);
            case "neq" -> !Objects.equals(actual, expected);
            case "contains" -> actual != null && String.valueOf(actual).contains(String.valueOf(expected));
            case "gt" -> compare(actual, expected, dataType) > 0;
            case "gte" -> compare(actual, expected, dataType) >= 0;
            case "lt" -> compare(actual, expected, dataType) < 0;
            case "lte" -> compare(actual, expected, dataType) <= 0;
            case "in" -> expected instanceof Collection<?> values && values.stream().anyMatch(value -> Objects.equals(actual, value));
            case "not_in" -> expected instanceof Collection<?> values && values.stream().noneMatch(value -> Objects.equals(actual, value));
            case "is_null" -> actual == null;
            case "is_not_null" -> actual != null;
            default -> throw new IllegalArgumentException("unsupported final result operator: " + operator);
        };
    }

    private int compare(Object actual, Object expected, String dataType) {
        if (actual == null || expected == null) return actual == expected ? 0 : (actual == null ? -1 : 1);
        if ("number".equals(dataType)) {
            return new BigDecimal(String.valueOf(actual)).compareTo(new BigDecimal(String.valueOf(expected)));
        }
        return String.valueOf(actual).compareTo(String.valueOf(expected));
    }

    private record ComparableValue(Object value) implements Comparable<ComparableValue> {
        static ComparableValue of(Object value, String dataType) {
            if (value == null) return new ComparableValue(null);
            return new ComparableValue("number".equals(dataType)
                    ? new BigDecimal(String.valueOf(value)) : String.valueOf(value));
        }

        @Override
        public int compareTo(ComparableValue other) {
            if (value == null && other.value == null) return 0;
            if (value == null) return 1;
            if (other.value == null) return -1;
            if (value instanceof BigDecimal left && other.value instanceof BigDecimal right) return left.compareTo(right);
            return String.valueOf(value).compareTo(String.valueOf(other.value));
        }
    }
}
