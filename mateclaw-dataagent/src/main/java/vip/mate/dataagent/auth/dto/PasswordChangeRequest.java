package vip.mate.dataagent.auth.dto;

import lombok.Data;

/**
 * 修改密码请求
 * <p>
 * 通过请求体传输旧/新密码，避免使用 URL query 参数——
 * 口令进 URL 会被网关/反向代理访问日志、浏览器历史等渠道记录下来。
 */
@Data
public class PasswordChangeRequest {

    /** 原密码 */
    private String oldPassword;

    /** 新密码 */
    private String newPassword;
}