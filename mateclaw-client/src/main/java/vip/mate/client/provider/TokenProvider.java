package vip.mate.client.provider;

/**
 * Token 提供者接口
 * <p>
 * 支持动态获取和刷新 Token
 */
public interface TokenProvider {

    /**
     * 获取当前有效的 Token
     *
     * @return Token 字符串，不含 Bearer 前缀
     */
    String getToken();

    /**
     * 刷新 Token（如收到 401 或 X-New-Token 时调用）
     */
    void refreshToken();
}
