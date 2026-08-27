package vip.mate.dataagent.auth.enterprise;

/**
 * 企业认证结果
 * <p>
 * 用状态枚举而非异常表达"需要验证码/验证码错误"，便于服务层翻译成
 * 对应的 HTTP 语义（429 / 401）并驱动前端验证码交互；
 * authnType 仅在 SUCCESS 时携带，用于审计日志记录实际生效的认证通道。
 *
 * @author MateClaw Team
 */
public record EnterpriseAuthResult(Status status, EnterpriseUserInfo user, String authnType) {

    public enum Status {
        /** 认证成功 */
        SUCCESS,
        /** 领航风控要求图形验证码（message = NEED_RAND_CODE） */
        NEED_CAPTCHA,
        /** 图形验证码错误（message = WRONG_IMAGE_CODE），需刷新验证码重试 */
        WRONG_CAPTCHA,
        /** 账号或口令错误（领航侧拒绝，不向前端泄露具体原因） */
        AUTH_FAILED
    }

    public static EnterpriseAuthResult success(EnterpriseUserInfo user, String authnType) {
        return new EnterpriseAuthResult(Status.SUCCESS, user, authnType);
    }

    public static EnterpriseAuthResult needCaptcha() {
        return new EnterpriseAuthResult(Status.NEED_CAPTCHA, null, null);
    }

    public static EnterpriseAuthResult wrongCaptcha() {
        return new EnterpriseAuthResult(Status.WRONG_CAPTCHA, null, null);
    }

    public static EnterpriseAuthResult authFailed() {
        return new EnterpriseAuthResult(Status.AUTH_FAILED, null, null);
    }
}
