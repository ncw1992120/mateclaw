package vip.mate.dataagent.auth.service;

import vip.mate.dataagent.auth.dto.AuthModeVO;
import vip.mate.dataagent.auth.dto.DataAgentLoginRequest;
import vip.mate.dataagent.auth.dto.PilotCaptchaVO;
import vip.mate.dataagent.auth.dto.PilotSsoRequest;
import vip.mate.dataagent.auth.enterprise.EnterpriseIdentityProvider;
import vip.mate.dataagent.auth.dto.DataAgentLoginResponse;

/**
 * DataAgent 认证服务
 * <p>
 * 复用 mateclaw-server 的 {@code AuthService} 进行密码校验与 JWT 签发，
 * 在此基础上组装工作区列表，供前端登录后直接渲染工作区切换器。
 * <p>
 * 企业认证模式（mateclaw.pilot.enabled=true）下，登录改为账密代验：
 * 由 {@link EnterpriseIdentityProvider} 向企业认证服务校验凭据，
 * 通过影子账号机制映射到本地用户后签发同样的 JWT。
 */
public interface DataAgentAuthService {

    /**
     * 用户登录（本地账密 / 企业账密代验双轨）
     *
     * @param request 登录请求（用户名 + 密码 + 可选图形验证码）
     * @return 登录响应（含 JWT 令牌与工作区列表）
     */
    DataAgentLoginResponse login(DataAgentLoginRequest request);

    /**
     * 获取当前登录用户信息（含工作区列表）
     * <p>
     * 用于前端刷新页面后恢复用户状态。
     *
     * @param username 当前登录用户名
     * @return 用户信息（含工作区列表）
     */
    DataAgentLoginResponse getCurrentUserInfo(String username);

    /**
     * 获取企业认证图形验证码（requestId + Base64 PNG）
     *
     * @param authnType 认证类型：UM=域账号口令 / AD=用户主机账号口令；
     *                  为 null 时使用配置默认值
     * @return 验证码挑战
     */
    PilotCaptchaVO fetchCaptcha(String authnType);

    /**
     * 查询当前认证模式（登录页初始化用，公开接口）
     *
     * @return provider=local（本地账密）或 pilot（领航代验），pilot 时附带支持的认证类型
     */
    AuthModeVO getAuthMode();

    /**
     * 企业 SSO 免登：浏览器携带的领航 SSO Cookie 经 /v3/assertion/sso 校验后换取本地会话
     *
     * @param request SSO 请求（ssoCookie 必填，authnType 可选）
     * @return 登录响应（含 JWT 令牌与工作区列表）
     */
    DataAgentLoginResponse loginBySso(PilotSsoRequest request);

    /**
     * 领航 SSO 会话续期透传（活跃用户保持企业侧会话；失败不影响本地 JWT 会话）
     */
    void renewSsoSession(PilotSsoRequest request);
}
