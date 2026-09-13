package vip.mate.dataagent.dataset.jdbc;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.Select;
import org.springframework.stereotype.Component;
import vip.mate.dataagent.dataset.DatasetFilter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;

/** 使用 JSqlParser 做结构校验，再以外层 SELECT 追加受控过滤和分页。 */
@Component
public class JSqlParserValidationService implements SqlValidationService {
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_$]*");
    private static final int MAX_LIMIT = 10_000;

    @Override
    public CompiledJdbcQuery compile(String baseSql, List<String> columns, List<DatasetFilter> filters,
                                     int limit, int offset) {
        String normalized = normalize(baseSql);
        Statement statement;
        try {
            Statements statements = CCJSqlParserUtil.parseStatements(normalized);
            if (statements == null || statements.getStatements().size() != 1) {
                throw new IllegalArgumentException("multiple SQL statements are not allowed");
            }
            statement = statements.getStatements().getFirst();
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException illegal) throw illegal;
            throw new IllegalArgumentException("invalid SQL", e);
        }
        if (!(statement instanceof Select)) {
            throw new IllegalArgumentException("only SELECT/WITH statements are allowed");
        }
        if (limit <= 0 || limit > MAX_LIMIT || offset < 0) {
            throw new IllegalArgumentException("invalid limit or offset");
        }

        Set<String> allowedColumns = new HashSet<>();
        for (String column : columns == null ? List.<String>of() : columns) {
            if (!IDENTIFIER.matcher(column).matches()) {
                throw new IllegalArgumentException("unsafe column identifier: " + column);
            }
            allowedColumns.add(column.toLowerCase(Locale.ROOT));
        }
        StringBuilder projection = new StringBuilder();
        if (columns == null || columns.isEmpty()) {
            projection.append("*");
        } else {
            projection.append(String.join(", ", columns));
        }

        List<Object> parameters = new ArrayList<>();
        StringBuilder predicate = new StringBuilder();
        for (DatasetFilter filter : filters == null ? List.<DatasetFilter>of() : filters) {
            if (!allowedColumns.isEmpty() && !allowedColumns.contains(filter.field().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("unknown filter column: " + filter.field());
            }
            if (!IDENTIFIER.matcher(filter.field()).matches()) {
                throw new IllegalArgumentException("unsafe filter column: " + filter.field());
            }
            if (predicate.length() > 0) predicate.append(" AND ");
            String operator = filter.operator().toLowerCase(Locale.ROOT);
            predicate.append(filter.field()).append(' ').append(operatorSql(operator));
            if (needsValue(operator)) {
                if (operator.equals("in") || operator.equals("not_in")) {
                    appendPlaceholders(predicate, filter.value(), parameters, true);
                } else if (operator.equals("between")) {
                    appendPlaceholders(predicate, filter.value(), parameters, false);
                } else {
                    predicate.append(" ?");
                    parameters.add(filter.value());
                }
            }
        }
        // Re-render the parsed statement so trailing comments cannot consume the
        // wrapper that carries the pushed predicates and pagination.
        String canonicalSql = statement.toString();
        String sql = "SELECT " + projection + " FROM (" + canonicalSql + ") AS _mateclaw_source";
        if (predicate.length() > 0) sql += " WHERE " + predicate;
        sql += " LIMIT ? OFFSET ?";
        parameters.add(limit);
        parameters.add(offset);
        return new CompiledJdbcQuery(sql, parameters, digest(sql));
    }

    private String normalize(String sql) {
        if (sql == null || sql.isBlank()) throw new IllegalArgumentException("base SQL must not be blank");
        return sql.trim();
    }

    private boolean needsValue(String operator) {
        return !Set.of("is_null", "is_not_null").contains(operator.toLowerCase(Locale.ROOT));
    }

    private void appendPlaceholders(StringBuilder predicate, Object value, List<Object> parameters,
                                    boolean parenthesize) {
        List<?> values = value instanceof Collection<?> collection ? List.copyOf(collection) : List.of(value);
        if (values.isEmpty()) throw new IllegalArgumentException("filter value collection must not be empty");
        if (parenthesize) predicate.append(" (");
        else predicate.append(" ");
        predicate.append(String.join(", ", Collections.nCopies(values.size(), "?")));
        if (parenthesize) predicate.append(')');
        parameters.addAll(values);
    }

    private String operatorSql(String operator) {
        return switch (operator.toLowerCase(Locale.ROOT)) {
            case "eq" -> "=";
            case "neq" -> "<>";
            case "gt" -> ">";
            case "gte" -> ">=";
            case "lt" -> "<";
            case "lte" -> "<=";
            case "in" -> "IN";
            case "not_in" -> "NOT IN";
            case "between" -> "BETWEEN";
            case "is_null" -> "IS NULL";
            case "is_not_null" -> "IS NOT NULL";
            default -> throw new IllegalArgumentException("unsupported filter operator: " + operator);
        };
    }

    private String digest(String sql) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(sql.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte value : hash) result.append(String.format("%02x", value));
            return result.toString();
        } catch (Exception e) {
            throw new IllegalStateException("cannot calculate query digest", e);
        }
    }
}
