package vip.mate.dataagent.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vip.mate.dataagent.config.UidSyncProperties;
import vip.mate.dataagent.dto.UserUidSyncResultDTO;
import vip.mate.dataagent.exception.BusinessException;
import vip.mate.dataagent.service.UserUidMappingSyncService;

/**
 * 用户 Aloudata UID 映射定时同步调度器
 * <p>
 * 按 {@code dataagent.uid-sync.cron}（Spring 6 段 cron）触发全量同步；
 * {@code dataagent.uid-sync.enabled} 关闭时不执行（默认关闭，按环境开启）。
 * <p>
 * 多实例互斥由 {@code UserUidMappingSyncServiceImpl} 内的 ShedLock 锁保证
 * （与管理员手动触发共用同一把锁，避免并发全量同步交错写入）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserUidMappingSyncScheduler {

    private final UidSyncProperties uidSyncProperties;
    private final UserUidMappingSyncService userUidMappingSyncService;

    /**
     * 定时同步入口：cron 由配置注入，enabled 关闭时跳过
     */
    @Scheduled(cron = "${dataagent.uid-sync.cron:0 0 2 * * *}")
    public void syncUserUidMapping() {
        if (!uidSyncProperties.isEnabled()) {
            return;
        }
        try {
            UserUidSyncResultDTO result = userUidMappingSyncService.syncFromSource();
            log.info("[UidSync] 定时同步触发成功: fetched={}, upserted={}, disabled={}, skipped={}",
                    result.getFetched(), result.getUpserted(), result.getDisabled(), result.getSkipped());
        } catch (BusinessException e) {
            // 手动触发正在执行等业务性跳过，不算故障
            log.warn("[UidSync] 定时同步跳过: {}", e.getMessage());
        } catch (Exception e) {
            // 同步失败仅告警，不影响主流程；上次成功映射保留继续可用
            log.error("[UidSync] 定时同步失败: {}", e.getMessage());
        }
    }
}
