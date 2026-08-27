package vip.mate.dataagent.auth.enterprise;

/**
 * 图形验证码挑战：领航验证码接口返回的 requestId + Base64 PNG 图片
 *
 * @param requestId   提交认证时需回传的请求 ID（additionalInfo.requestId）
 * @param imageBase64 验证码图片（Base64 编码的 PNG，不含 data: 前缀）
 * @author MateClaw Team
 */
public record CaptchaChallenge(String requestId, String imageBase64) {
}
