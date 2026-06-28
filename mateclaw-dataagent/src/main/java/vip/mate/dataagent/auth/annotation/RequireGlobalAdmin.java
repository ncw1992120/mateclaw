package vip.mate.dataagent.auth.annotation;

import java.lang.annotation.*;

/**
 * DataAgent 全局管理员权限检查注解
 * <p>
 * 标注在 Controller 方法上，拦截器校验当前用户的 mate_user.role = admin。
 * 与 {@link RequireWorkspaceRole} 互斥，两者同时标注时本注解优先。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireGlobalAdmin {
}
