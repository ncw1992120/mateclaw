package vip.mate.dataagent.audit;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import vip.mate.dataagent.auth.context.UserContext;
import vip.mate.dataagent.auth.context.UserContextHolder;
import vip.mate.sdk.service.AuditRuntime;

import java.util.Map;

/**
 * DataAgent 操作审计拦截器
 * <p>
 * 对 {@code /v1/**} 下的写请求（POST/PUT/PATCH/DELETE）异步记录操作审计，
 * 复用 mateclaw 内置 {@code mate_audit_event} 表。审计失败只记 debug 日志，绝不影响业务请求。
 * <p>
 * 排除高频/个人维度路径：聊天、上传、个人会话、个人查询账号、搜索等；
 * 用户身份、IP、User-Agent 由底层 AuditEventService 在请求线程上自动捕获。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataAgentAuditInterceptor implements HandlerInterceptor {

    private static final String ATTR_START_TIME = "DATAAGENT_AUDIT_START_MS";

    /** 高频或纯个人维度路径前缀：不写审计 */
    private static final String[] EXCLUDED_PREFIXES = {
            "/v1/chat", "/v1/chat-optimize", "/v1/chat-upload",
            "/v1/llm", "/v1/agentscope", "/v1/conversations",
            "/v1/datasource-accounts", "/v1/schema-search",
            "/v1/files/generated", "/v1/auth"
    };

    private final AuditRuntime auditRuntime;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(ATTR_START_TIME, System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        try {
            if (!(handler instanceof HandlerMethod handlerMethod)) {
                return;
            }
            String httpMethod = request.getMethod();
            if ("GET".equalsIgnoreCase(httpMethod) || "OPTIONS".equalsIgnoreCase(httpMethod)
                    || "HEAD".equalsIgnoreCase(httpMethod)) {
                return;
            }
            String action = switch (httpMethod.toUpperCase()) {
                case "POST" -> "CREATE";
                case "PUT", "PATCH" -> "UPDATE";
                case "DELETE" -> "DELETE";
                default -> null;
            };
            if (action == null) {
                return;
            }

            String uri = stripContextPath(request);
            for (String prefix : EXCLUDED_PREFIXES) {
                if (uri.startsWith(prefix)) {
                    return;
                }
            }

            Long start = (Long) request.getAttribute(ATTR_START_TIME);
            long costMs = start == null ? -1L : System.currentTimeMillis() - start;

            auditRuntime.record(action, resolveResourceType(uri), resolveResourceId(request),
                    null, buildDetail(handlerMethod, uri, httpMethod, request.getQueryString(),
                            response.getStatus(), costMs, ex),
                    currentWorkspaceIdOrNull());
        } catch (Exception e) {
            log.debug("[Audit] 记录审计事件失败: {}", e.getMessage());
        }
    }

    /** 去掉 context-path 前缀，得到以 /v1 开头的请求路径 */
    private String stripContextPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }

    /**
     * 从路径推导资源类型，如 /v1/business-terms/embed → BUSINESS_TERM、
     * /v1/insight/dashboards → INSIGHT_DASHBOARD
     */
    private String resolveResourceType(String uri) {
        String[] parts = uri.split("/");
        String first = null;
        String second = null;
        for (int i = 0; i < parts.length; i++) {
            if ("v1".equals(parts[i]) && i + 1 < parts.length) {
                first = parts[i + 1];
                if (i + 2 < parts.length) {
                    second = parts[i + 2];
                }
                break;
            }
        }
        if (first == null || first.isBlank()) {
            return "UNKNOWN";
        }
        if ("insight".equals(first)) {
            return "dashboards".equals(second) ? "INSIGHT_DASHBOARD"
                    : "reports".equals(second) ? "INSIGHT_REPORT" : "INSIGHT";
        }
        return switch (first) {
            case "business-terms" -> "BUSINESS_TERM";
            case "knowledge" -> "KNOWLEDGE";
            case "agents" -> "AGENT";
            case "cron-jobs" -> "CRON_JOB";
            case "datasources" -> "DATASOURCE";
            case "datasets" -> "DATASET";
            case "semantic-models" -> "SEMANTIC_MODEL";
            case "logical-relations" -> "LOGICAL_RELATION";
            case "resource-grants" -> "RESOURCE_GRANT";
            case "help-center" -> "HELP_CENTER";
            default -> first.toUpperCase().replace('-', '_');
        };
    }

    /** 从路径变量中取资源 ID：优先 id，其次任意以 Id 结尾的变量 */
    private String resolveResourceId(HttpServletRequest request) {
        Object attr = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (attr instanceof Map<?, ?> vars) {
            Object id = vars.get("id");
            if (id != null) {
                return truncate(String.valueOf(id), 128);
            }
            for (Map.Entry<?, ?> entry : vars.entrySet()) {
                if (String.valueOf(entry.getKey()).endsWith("Id") && entry.getValue() != null) {
                    return truncate(String.valueOf(entry.getValue()), 128);
                }
            }
        }
        return null;
    }

    /** 组装 detail JSON：swagger 摘要 + URI + 状态 + 耗时 + 异常摘要 */
    private String buildDetail(HandlerMethod handlerMethod, String uri, String httpMethod,
                               String query, int status, long costMs, Exception ex) {
        Operation operation = handlerMethod.getMethodAnnotation(Operation.class);
        StringBuilder sb = new StringBuilder("{");
        appendField(sb, "summary", operation != null ? operation.summary() : null, true);
        appendField(sb, "uri", uri, true);
        appendField(sb, "method", httpMethod, true);
        appendField(sb, "query", query, true);
        appendField(sb, "status", String.valueOf(status), false);
        appendField(sb, "costMs", String.valueOf(costMs), false);
        if (ex != null) {
            appendField(sb, "error", ex.getMessage(), true);
        }
        sb.append('}');
        return truncate(sb.toString(), 1000);
    }

    private void appendField(StringBuilder sb, String key, String value, boolean quoted) {
        if (sb.length() > 1) {
            sb.append(',');
        }
        sb.append('"').append(key).append("\":");
        if (value == null) {
            sb.append("null");
        } else if (quoted) {
            sb.append('"').append(escape(value)).append('"');
        } else {
            sb.append(value);
        }
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private String truncate(String s, int maxLen) {
        if (s == null || s.length() <= maxLen) {
            return s;
        }
        return s.substring(0, maxLen);
    }

    private Long currentWorkspaceIdOrNull() {
        UserContext ctx = UserContextHolder.get();
        return ctx != null ? ctx.getWorkspaceId() : null;
    }
}
