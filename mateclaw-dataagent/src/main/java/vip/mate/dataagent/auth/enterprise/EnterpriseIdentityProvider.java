package vip.mate.dataagent.auth.enterprise;

/**
 * 企业内部认证提供者 SPI
 * <p>
 * 抽象"凭据校验"环节：实现方负责与企业侧认证服务交互并完成账密代验，
 * 认证通过后由 {@code ShadowAccountService} 同步本地影子账号，
 * 再复用 mateclaw-server 的 JWT 签发链路（下游零感知）。
 * <p>
 * 当前实现：{@link PilotIdentityProvider}（平安领航）。
 * 预留后续扩展 LDAP / OIDC 等其他企业认证源。
 *
 * @author MateClaw Team
 */
public interface EnterpriseIdentityProvider {

    /**
     * 是否启用（未启用时登录走本地密码校验）
     */
    boolean enabled();

    /**
     * 账密代验
     *
     * @param username  用户输入的账号（域账号/工号）
     * @param password  用户输入的明文口令（仅内存传递，禁止落日志）
     * @param requestId 图形验证码请求 ID（无验证码流程时为 null）
     * @param validCode 用户输入的图形验证码（无验证码流程时为 null）
     * @param authnType 认证类型：UM=域账号口令 / AD=用户主机账号口令；
     *                  为 null 时使用配置默认值（mateclaw.pilot.authn-type）
     * @return 认证结果：SUCCESS / NEED_CAPTCHA / WRONG_CAPTCHA / AUTH_FAILED
     * @throws vip.mate.exception.MateClawException 企业认证服务不可用等基础设施故障（502）、不支持的认证类型（400）
     */
    EnterpriseAuthResult authenticate(String username, String password,
                                      String requestId, String validCode, String authnType);

    /**
     * 获取图形验证码（requestId + Base64 PNG），领航 authnMechanism 与认证类型传同一值
     *
     * @param authnType 认证类型（UM/AD），为 null 时使用配置默认值
     * @throws vip.mate.exception.MateClawException 企业认证服务不可用等基础设施故障
     */
    CaptchaChallenge fetchCaptcha(String authnType);

    /**
     * 企业 SSO 免登校验：用浏览器携带的领航 SSO Cookie 调 /v3/assertion/sso 换取身份。
     * 无验证码概念，失败统一为 AUTH_FAILED，不向前端泄露具体原因。
     *
     * @param ssoCookie 浏览器共享域上的领航 SSO Cookie 值（如 32hex-32hex 形态）
     * @param authnType 认证类型（UM/AD），为 null 时使用配置默认值
     * @return SUCCESS（含用户身份）或 AUTH_FAILED
     * @throws vip.mate.exception.MateClawException 企业认证服务不可用等基础设施故障（502）
     */
    EnterpriseAuthResult authenticateBySso(String ssoCookie, String authnType);

    /**
     * 领航 SSO 会话续期透传（/v3/sso/renewal），用于活跃用户保持企业侧会话。
     * 业务失败仅记日志不抛异常——续期失败不影响本地 JWT 会话。
     *
     * @throws vip.mate.exception.MateClawException 企业认证服务不可用等基础设施故障（502）
     */
    void renewSso(String ssoCookie, String authnType);
}
