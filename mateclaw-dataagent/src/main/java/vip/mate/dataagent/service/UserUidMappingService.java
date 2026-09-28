package vip.mate.dataagent.service;

import vip.mate.dataagent.dto.UserUidMappingStatusVO;
import vip.mate.dataagent.model.UserUidMappingEntity;

import java.util.List;

/**
 * 用户 Aloudata UID 映射服务接口
 * <p>
 * 维护「登录名 + 租户 → Aloudata UID」映射关系的查询能力，
 * 供认证值解析链兜底使用：手动绑定（{@link DatasourceAccountService}）优先，自动映射兜底。
 */
public interface UserUidMappingService {

    /**
     * 按登录名 + 租户查询映射记录
     *
     * @param username 登录名（按 mate_user 同口径归一化后匹配）
     * @param tenantId Aloudata 租户 ID
     * @return 映射实体，不存在时返回 null
     */
    UserUidMappingEntity getByUsernameAndTenant(String username, String tenantId);

    /**
     * 解析指定用户在指定 Aloudata 数据源租户下的自动映射认证值
     * <p>
     * 身份取自入参 {@code userId}（与手动绑定兜底同源，不依赖线程上下文），
     * 以该用户的登录名 + 数据源租户 ID 查询映射表，命中且启用时返回解密后的 UID；
     * 用户不存在、数据源非 Aloudata 类型、租户未配置或映射未命中时返回 null。
     *
     * @param datasourceId 数据源 ID
     * @param userId       用户 ID
     * @return 自动映射的 Aloudata 认证值，无法解析时返回 null
     */
    String resolveAutoAuthValue(Long datasourceId, Long userId);

    /**
     * 查询当前登录用户全部启用的自动映射概要（租户维度）
     * <p>
     * 供前端数据源页面按数据源租户匹配「已自动同步」状态，单次请求返回全量。
     *
     * @return 映射状态列表（按租户），未登录时返回空列表
     */
    List<UserUidMappingStatusVO> listMyEnabledMappings();
}
