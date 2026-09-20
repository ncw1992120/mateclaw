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
        return compile(baseSql, columns, filters, limit, offset, Map.of());
    }

    @Override
    public CompiledJdbcQuery compile(String baseSql, List<String> columns, List<DatasetFilter> filters,
                                     int limit, int offset, Map<String, Object> namedParameters) {
        // 命名参数必须在**解析之前**换成 ? —— JSqlParser 认不出 `:name`，带占位符的 SQL 直接解析不过去。
        // 值按出现顺序收集，与随后追加的 filters / limit / offset 拼成一个有序参数列表。
        List<Object> parameters = new ArrayList<>();
        String bound = bindNamedParameters(normalize(baseSql), namedParameters, parameters);
        Statement statement;
        try {
            Statements statements = CCJSqlParserUtil.parseStatements(bound);
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
                } else if (operator.equals("contains")) {
                    // 包含子串：值两侧自动补通配符 —— 用户填纯文本即可，不必自己写 %（也不会被当通配符注入）
                    predicate.append(" ?");
                    parameters.add("%" + filter.value() + "%");
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

    /**
     * 把 SQL 里的 {@code :name} 绑成 {@code ?}，并按出现顺序把值收进 {@code out}。
     * <p>
     * 用引号感知的扫描而不是纯正则：字符串字面量里的冒号（如 {@code ':notice'}）不是参数，
     * 纯正则替换会把 SQL 改坏。同时天然跳过 {@code ::} 类型转换（前一个字符是冒号）
     * 与 {@code :30}（冒号后不是标识符起始字符）。
     * <p>
     * 占位符没有对应值时直接报错，而不是塞 null —— 静默传 null 会让查询返回空结果，
     * 用户很难判断是"没数据"还是"参数没传进来"。
     */
    private String bindNamedParameters(String sql, Map<String, Object> namedParameters, List<Object> out) {
        Map<String, Object> values = namedParameters == null ? Map.of() : namedParameters;
        StringBuilder result = new StringBuilder(sql.length());
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        for (int i = 0; i < sql.length(); i++) {
            char current = sql.charAt(i);
            if (current == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
                result.append(current);
                continue;
            }
            if (current == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
                result.append(current);
                continue;
            }
            boolean isPlaceholder = current == ':' && !inSingleQuote && !inDoubleQuote
                    && i + 1 < sql.length()
                    && (i == 0 || sql.charAt(i - 1) != ':')
                    && Character.isJavaIdentifierStart(sql.charAt(i + 1));
            if (!isPlaceholder) {
                result.append(current);
                continue;
            }
            int end = i + 1;
            while (end < sql.length() && Character.isJavaIdentifierPart(sql.charAt(end))) end++;
            String name = sql.substring(i + 1, end);
            if (!values.containsKey(name)) {
                throw new IllegalArgumentException("missing SQL parameter: " + name);
            }
            result.append('?');
            out.add(values.get(name));
            i = end - 1;
        }
        return result.toString();
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
            case "contains" -> "LIKE";
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
