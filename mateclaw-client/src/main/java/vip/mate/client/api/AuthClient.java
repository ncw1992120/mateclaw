package vip.mate.client.api;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestTemplate;
import vip.mate.client.constant.ApiPathConstants;
import vip.mate.client.model.R;
import vip.mate.client.model.User;
import vip.mate.client.model.request.LoginReq;
import vip.mate.client.model.response.LoginResp;

import java.util.List;

/**
 * 认证管理客户端
 * <p>
 * 对应服务端 /api/v1/auth 接口，提供登录、用户管理等认证相关功能
 */
public class AuthClient extends AbstractApiClient {

    public AuthClient(String baseUrl, RestTemplate restTemplate) {
        super(baseUrl, restTemplate);
    }

    /**
     * 用户登录
     *
     * @param request 登录请求参数（包含 username、password 等）
     * @return 登录响应（包含 token 等信息）
     */
    public R<LoginResp> login(LoginReq request) {
        return post(ApiPathConstants.AUTH_LOGIN, request, new ParameterizedTypeReference<R<LoginResp>>() {});
    }

    /**
     * 获取用户列表
     *
     * @return 用户列表
     */
    public R<List<User>> listUsers() {
        return get(ApiPathConstants.AUTH_USERS, new ParameterizedTypeReference<R<List<User>>>() {});
    }

    /**
     * 创建用户
     *
     * @param user 用户信息
     * @return 创建的用户信息
     */
    public R<User> createUser(User user) {
        return post(ApiPathConstants.AUTH_USERS, user, new ParameterizedTypeReference<R<User>>() {});
    }

    /**
     * 修改密码
     *
     * @param id          用户ID
     * @param oldPassword 旧密码
     * @param newPassword 新密码
     * @return 操作结果
     */
    public R<Void> changePassword(Long id, String oldPassword, String newPassword) {
        String path = resolvePath(ApiPathConstants.AUTH_USER_PASSWORD, id) + "?oldPassword=" + oldPassword + "&newPassword=" + newPassword;
        return put(path, new ParameterizedTypeReference<R<Void>>() {});
    }
}
