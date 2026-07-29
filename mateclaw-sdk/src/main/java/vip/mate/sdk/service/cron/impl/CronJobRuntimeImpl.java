package vip.mate.sdk.service.cron.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vip.mate.cron.model.CronJobDTO;
import vip.mate.cron.service.CronJobService;
import vip.mate.sdk.service.cron.CronJobRuntime;

import java.util.List;

/**
 * 定时任务运行时实现
 * <p>
 * 委托给 mateclaw-server 的 CronJobService 执行业务逻辑。
 */
@Service
@RequiredArgsConstructor
public class CronJobRuntimeImpl implements CronJobRuntime {

    private final CronJobService cronJobService;

    @Override
    public List<CronJobDTO> listCronJobs(Long workspaceId) {
        return cronJobService.list(workspaceId);
    }

    @Override
    public CronJobDTO getCronJob(Long id, Long workspaceId) {
        return cronJobService.getById(id, workspaceId);
    }

    @Override
    public CronJobDTO createCronJob(CronJobDTO dto, Long workspaceId) {
        return cronJobService.create(dto, workspaceId);
    }

    @Override
    public CronJobDTO updateCronJob(Long id, CronJobDTO dto, Long workspaceId) {
        return cronJobService.update(id, dto, workspaceId);
    }

    @Override
    public void deleteCronJob(Long id, Long workspaceId) {
        cronJobService.delete(id, workspaceId);
    }

    @Override
    public void toggleCronJob(Long id, boolean enabled, Long workspaceId) {
        cronJobService.toggle(id, enabled, workspaceId);
    }

    @Override
    public void runCronJobNow(Long id, Long workspaceId) {
        cronJobService.runNow(id, workspaceId);
    }
}
