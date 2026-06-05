package vip.mate.client.model.request;

import lombok.Data;

import java.io.Serializable;

/**
 * 登录请求
 */
@Data
public class LoginReq implements Serializable {
    private static final long serialVersionUID = 1L;

    private String username;
    private String password;
}
