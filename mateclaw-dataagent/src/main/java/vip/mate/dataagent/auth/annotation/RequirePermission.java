package vip.mate.dataagent.auth.annotation;

import java.lang.annotation.*;

/**
 * DataAgent 细粒度权限点校验注解
 * <p>
 * 标注在 Controller 方法上，拦截器会根据 {@link vip.mate.dataagent.auth.permission.DataAgentPermission}
 * 的角色映射矩阵校验当前用户是否拥有指定权限点。
 * <p>
 * 与 {@link RequireWorkspaceRole} 的区别：
 * <ul>
 *   <li>{@code @RequireWorkspaceRole} 按角色等级比较（viewer < member < admin < owner）</li>
 *   <li>{@code @RequirePermission} 按权限点-角色映射矩阵判断，支持更细粒度的控制
 *       （如 model:manage 仅全局 admin，datasource:sync 仅 admin+）</li>
 * </ul>
 * <p>
 * 全局 admin（mate_user.role=admin）自动放行所有权限点。
 * <p>
 * 示例：
 * <pre>
 * &#64;RequirePermission(DataAgentConstants.PERM_MODEL_MANAGE)
 * &#64;RequirePermission(DataAgentConstants.PERM_DATASOURCE_SYNC)
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequirePermission {

    /**
     * 权限点 code，对应 {@link vip.mate.dataagent.auth.permission.DataAgentPermission#getCode()}
     * 或 {@link DataAgentConstants} 中的 PERM_* 常量
     */
    String value();
}
