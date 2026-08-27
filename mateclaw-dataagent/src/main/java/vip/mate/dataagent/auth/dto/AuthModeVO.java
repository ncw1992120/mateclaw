package vip.mate.dataagent.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 认证模式 VO（登录页初始化用，公开接口返回）
 *
 * @author MateClaw Team
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthModeVO {

    /** provider = local：本地账密登录（隐藏企业认证选择器）；pilot：领航账密代验 */
    private String provider;

    /** 企业认证支持的类型列表；local 模式下为空列表 */
    private List<String> authTypes;

    /**
     * 领航 SSO Cookie 名称（浏览器共享域上）；非空时前端尝试读取并静默免登。
     * local 模式或未配置时为 null。
     */
    private String ssoCookieName;
}
