package vip.mate.dataagent.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 企业认证图形验证码 VO
 *
 * @author MateClaw Team
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PilotCaptchaVO {

    /** 领航验证码请求 ID，登录重试时回传 additionalInfo.requestId */
    private String requestId;

    /** 验证码图片（Base64 PNG，前端直接用于 <img src="data:image/png;base64,...">） */
    private String captchaImage;
}
