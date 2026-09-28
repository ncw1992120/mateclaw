package vip.mate.dataagent.service;

import vip.mate.dataagent.dto.UserUidSyncResultDTO;

/**
 * 用户 Aloudata UID 映射同步服务接口
 * <p>
 * 从外部用户系统（MySQL / PostgreSQL 源库）拉取「登录名 + 租户 → UID」映射数据，
 * 全量 upsert 到本地映射表；源库中已删除的记录在本地置为停用（软失效）。
 */
public interface UserUidMappingSyncService {

    /**
     * 执行一次全量同步
     * <p>
     * 先取同步锁（定时任务与管理员手动触发共用，重复触发抛 409）；
     * 拉取阶段在事务外执行（源库连接即用即关，不占用本地连接池）；
     * 写入与软失效阶段在同一事务内，任一步失败整体回滚，保留上一次成功同步的映射继续可用。
     * 拉取结果为 0 条时跳过软失效，避免源库 SQL 误配把全部映射一次性停用。
     *
     * @return 同步统计结果
     * @throws vip.mate.dataagent.exception.BusinessException 同步源未配置（400）、同步已在执行（409）、
     *                                                       源库拉取失败（500）
     */
    UserUidSyncResultDTO syncFromSource();
}
