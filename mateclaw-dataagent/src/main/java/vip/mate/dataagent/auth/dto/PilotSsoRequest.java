package vip.mate.dataagent.auth.dto;

import lombok.Data;

/**
 * 领航 SSO 免登/续期请求
 *
 * @author MateClaw Team
 */
@Data
public class PilotSsoRequest {

    /** 浏览器共享域上的领航 SSO Cookie 值（如 32hex-32hex 形态） */
    private String ssoCookie;

    /** 认证类型：UM=域账号 / AD=主机账号；缺省用配置默认值 */
    private String authnType;
}
