package vip.mate.dataagent.auth.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import vip.mate.auth.model.LoginRequest;

/**
 * DataAgent 登录请求
 * <p>
 * 继承 server 的 {@link LoginRequest}（username/password），扩展领航所需的
 * 认证类型与图形验证码字段；无验证码流程时验证码字段为 null。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DataAgentLoginRequest extends LoginRequest {

    /** 领航验证码请求 ID（fetchCaptcha 返回） */
    private String requestId;

    /** 用户输入的图形验证码 */
    private String validCode;

    /**
     * 认证类型：UM=域账号口令 / AD=用户主机账号口令；
     * 为 null 时后端使用配置默认值 mateclaw.pilot.authn-type
     */
    private String authnType;

    /**
     * 登录通道提示：local=强制本地账密校验（对应前端"本地账号登录"表单）；
     * 缺省按白名单自动路由。注意：这为持有真实本地密码的账号提供了绕过领航的
     * 第二入口，影子账号因随机密码天然免疫，预建真实密码的账号应严格受限。
     */
    private String channel;
}
