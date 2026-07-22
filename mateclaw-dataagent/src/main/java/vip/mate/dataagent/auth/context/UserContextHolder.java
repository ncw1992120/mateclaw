package vip.mate.dataagent.auth.context;

/**
 * 用户上下文 ThreadLocal 持有器
 * <p>
 * 在请求线程内传递当前用户身份信息，请求结束时由拦截器清理。
 * 业务层调用 {@link #get()} 获取当前用户，调用 {@link #requireUserId()} 获取必填的用户 ID。
 */
public final class UserContextHolder {

    // 使用 InheritableThreadLocal 替代 ThreadLocal，使子线程（含虚拟线程）自动继承父线程的用户上下文。
    // Agent 工具在 sseExecutor 线程上单工具内联执行时，ThreadLocal 即可满足；
    // 但多个 concurrency-safe 工具并行批执行时，ToolExecutionExecutor 通过
    // CompletableFuture.supplyAsync 派发到虚拟线程（TOOL_EXECUTOR），ThreadLocal 无法跨线程传递，
    // InheritableThreadLocal 可在虚拟线程创建时从父线程继承值，覆盖并行批场景。
    private static final InheritableThreadLocal<UserContext> CONTEXT = new InheritableThreadLocal<>();

    private UserContextHolder() {
    }

    /**
     * 设置当前线程的用户上下文
     */
    public static void set(UserContext context) {
        CONTEXT.set(context);
    }

    /**
     * 获取当前线程的用户上下文（可能为 null，如未认证请求）
     */
    public static UserContext get() {
        return CONTEXT.get();
    }

    /**
     * 获取当前用户 ID，未登录时返回 null
     */
    public static Long getUserId() {
        UserContext ctx = CONTEXT.get();
        return ctx != null ? ctx.getUserId() : null;
    }

    /**
     * 获取当前工作区 ID，未设置时返回 null
     */
    public static Long getWorkspaceId() {
        UserContext ctx = CONTEXT.get();
        return ctx != null ? ctx.getWorkspaceId() : null;
    }

    /**
     * 要求当前用户已登录，返回用户 ID，否则抛出异常
     */
    public static Long requireUserId() {
        Long userId = getUserId();
        if (userId == null) {
            throw new IllegalStateException("当前请求未携带有效的用户身份信息");
        }
        return userId;
    }

    /**
     * 清理当前线程的用户上下文（防止内存泄漏）
     */
    public static void clear() {
        CONTEXT.remove();
    }
}
