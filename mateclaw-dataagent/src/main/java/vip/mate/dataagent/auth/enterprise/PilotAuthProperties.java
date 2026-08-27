package vip.mate.dataagent.auth.enterprise;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * 平安领航认证配置（mateclaw.pilot.*）
 * <p>
 * 对应 application.yml：
 * <pre>
 * mateclaw:
 *   pilot:
 *     auth-server: ${MATECLAW_PILOT_AUTH_SERVER:...}
 *     captcha-server: ${MATECLAW_PILOT_CAPTCHA_SERVER:...}
 *     sso-server: ${MATECLAW_PILOT_TOKEN_SERVER:...}          # 本期预留不接
 *     sso-renewal-server: ${MATECLAW_PILOT_SSO_RENEWAL_SERVER:...}  # 本期预留不接
 * </pre>
 *
 * @author MateClaw Team
 */
@Data
@ConfigurationProperties(prefix = "mateclaw.pilot")
public class PilotAuthProperties {

    /** 总开关：是否启用领航企业认证（false 时走本地账密登录） */
    private boolean enabled = false;

    /** 登录认证端点（/v3/assertion），账密代验主链路 */
    private String authServer;

    /** 验证码端点（/v3/assertion/captcha） */
    private String captchaServer;

    /** SSO Token 验证端点（本期预留，不参与账密代验） */
    private String ssoServer;

    /** SSO Token 续期端点（本期预留，不参与账密代验） */
    private String ssoRenewalServer;

    /** 默认认证类型：UM=域账号口令 / AD=用户主机账号口令；请求未指定时使用 */
    private String authnType = "UM";

    /** 断言生命周期（秒），领航侧参数，与本系统 JWT 无关 */
    private String lifeTime = "432000";

    /** 调用领航服务的连接/读取超时 */
    private Duration timeout = Duration.ofSeconds(5);

    /**
     * 企业认证模式下的本地兜底白名单：命中的用户名仍走本地密码校验，
     * 用于 admin 等管理账号在企业认证服务故障时不被锁死。
     */
    private List<String> localFallbackUsers = List.of("admin");

    /**
     * 浏览器共享域上的领航 SSO Cookie 名称。
     * 已确认生产形态：Domain=.paic.com.cn（共享父域）、非 HttpOnly，前端 JS 可读；
     * 配置后登录页会尝试读取该 Cookie 并静默调用 /v1/auth/sso/login 免登；
     * 置空可关闭前端 SSO 免登探测（仅账密代验）。
     */
    private String ssoCookieName = "CAS_SSO_COOKIE";
}
