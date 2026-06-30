package vip.mate.dataagent.service;

import vip.mate.dataagent.dto.DatasourceAccountRequest;
import vip.mate.dataagent.dto.DatasourceAccountVO;
import vip.mate.dataagent.model.DatasourceAccountEntity;

import java.util.List;

/**
 * 数据源用户查询账号服务接口
 * <p>
 * 管理用户与数据源的查询账号绑定关系，查询时优先使用用户自己的查询账号，而非数据源的管理员同步账号。
 * <p>
 * 不同数据源类型的账号模型：
 * <ul>
 *   <li>JDBC 类型（mysql/postgresql 等）：queryUsername + queryPassword 为数据库账号密码</li>
 *   <li>Aloudata 类型：queryPassword 存储 Aloudata 平台分配的 auth-value（认证值），
 *       tenant-id 和 auth-type 仍来自数据源共享配置，queryUsername 不使用</li>
 * </ul>
 */
public interface DatasourceAccountService {

    /**
     * 查询当前用户在指定数据源上绑定的查询账号
     *
     * @param datasourceId 数据源 ID
     * @param userId       用户 ID
     * @return 查询账号实体，未绑定时返回 null
     */
    DatasourceAccountEntity getByDatasourceIdAndUserId(Long datasourceId, Long userId);

    /**
     * 解析当前用户在指定 Aloudata 数据源上的认证值（auth-value）
     * <p>
     * 如果用户绑定了查询账号且状态为启用，返回绑定的认证值；否则返回 null，表示使用数据源默认账号。
     *
     * @param datasourceId 数据源 ID
     * @param userId       用户 ID
     * @return 用户的 Aloudata 认证值，未绑定时返回 null
     */
    String resolveAloudataAuthValue(Long datasourceId, Long userId);

    /**
     * 查询当前用户所有已绑定的查询账号
     *
     * @param userId 用户 ID
     * @return 查询账号视图对象列表
     */
    List<DatasourceAccountVO> listByUserId(Long userId);

    /**
     * 创建或更新用户查询账号绑定（upsert）
     * <p>
     * 如果该用户已绑定该数据源的查询账号，则更新；否则新建。
     *
     * @param request 查询账号请求
     * @param userId  用户 ID
     * @return 绑定后的视图对象
     */
    DatasourceAccountVO upsertAccount(DatasourceAccountRequest request, Long userId);

    /**
     * 删除用户查询账号绑定
     *
     * @param datasourceId 数据源 ID
     * @param userId       用户 ID
     */
    void deleteAccount(Long datasourceId, Long userId);

    /**
     * 测试用户查询账号连接
     *
     * @param datasourceId 数据源 ID
     * @param userId       用户 ID
     * @return 连接是否成功
     */
    boolean testAccountConnection(Long datasourceId, Long userId);
}
