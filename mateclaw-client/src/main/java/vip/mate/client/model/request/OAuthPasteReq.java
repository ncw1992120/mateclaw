package vip.mate.client.model.request;

import lombok.Data;

import java.io.Serializable;

/**
 * OpenAI OAuth 粘贴回调请求
 */
@Data
public class OAuthPasteReq implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 用户粘贴的浏览器回调 URL */
    private String callbackUrl;
}