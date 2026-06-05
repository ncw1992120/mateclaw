package vip.mate.client.provider;

/**
 * 静态 Token 提供者
 * <p>
 * 直接返回配置的 Token 值，不会自动刷新
 */
public class StaticTokenProvider implements TokenProvider {

    private volatile String token;

    public StaticTokenProvider(String token) {
        this.token = token;
    }

    @Override
    public String getToken() {
        return token;
    }

    @Override
    public void refreshToken() {
        // 静态 Token 不支持自动刷新
    }

    /**
     * 更新 Token（用于 X-New-Token 续签场景）
     *
     * @param newToken 新 Token
     */
    public void updateToken(String newToken) {
        this.token = newToken;
    }
}
