package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 登录响应
 */
@Data
public class LoginResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String token;
    private String username;
    private String nickname;
    private String role;
}
