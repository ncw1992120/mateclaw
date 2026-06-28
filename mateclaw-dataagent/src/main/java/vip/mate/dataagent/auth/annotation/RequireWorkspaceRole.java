package vip.mate.dataagent.auth.annotation;

import java.lang.annotation.*;

/**
 * DataAgent 声明式 Workspace 权限检查注解
 * <p>
 * 标注在 Controller 方法上，拦截器会自动校验当前用户在请求 Workspace 中的角色 ≥ value()。
 * 工作区 ID 从 {@code X-Workspace-Id} 请求头解析，缺省回退到默认工作区（id=1）。
 * <p>
 * 角色等级: owner(4) > admin(3) > member(2) > viewer(1)
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireWorkspaceRole {

    /**
     * 最低角色要求，默认 viewer（即只要是工作区成员就可以访问）
     */
    String value() default "viewer";
}
