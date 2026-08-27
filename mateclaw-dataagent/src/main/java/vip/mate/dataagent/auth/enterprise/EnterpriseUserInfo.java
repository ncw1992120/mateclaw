package vip.mate.dataagent.auth.enterprise;

/**
 * 企业认证通过后的用户身份信息
 *
 * @param principalName 用户唯一标识（领航返回的域账号，content.PRINCIPAL_NAME）
 * @param displayName   展示名（领航未返回时为 null，落库时以 principalName 兜底）
 * @author MateClaw Team
 */
public record EnterpriseUserInfo(String principalName, String displayName) {
}
