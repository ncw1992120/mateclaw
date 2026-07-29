package vip.mate.sdk.service.cron;

import vip.mate.cron.model.CronJobDTO;

import java.util.List;

/**
 * 定时任务运行时接口
 * <p>
 * 提供定时任务 CRUD、启停、立即执行等编程式访问能力。
 */
public interface CronJobRuntime {

    /**
     * 获取定时任务列表
     *
     * @param workspaceId 工作区 ID
     * @return 定时任务 DTO 列表
     */
    List<CronJobDTO> listCronJobs(Long workspaceId);

    /**
     * 获取定时任务详情
     *
     * @param id          定时任务 ID
     * @param workspaceId 工作区 ID
     * @return 定时任务 DTO
     */
    CronJobDTO getCronJob(Long id, Long workspaceId);

    /**
     * 创建定时任务
     *
     * @param dto         定时任务 DTO
     * @param workspaceId 工作区 ID
     * @return 创建后的定时任务 DTO
     */
    CronJobDTO createCronJob(CronJobDTO dto, Long workspaceId);

    /**
     * 更新定时任务
     *
     * @param id          定时任务 ID
     * @param dto         定时任务 DTO
     * @param workspaceId 工作区 ID
     * @return 更新后的定时任务 DTO
     */
    CronJobDTO updateCronJob(Long id, CronJobDTO dto, Long workspaceId);

    /**
     * 删除定时任务
     *
     * @param id          定时任务 ID
     * @param workspaceId 工作区 ID
     */
    void deleteCronJob(Long id, Long workspaceId);

    /**
     * 启用/禁用定时任务
     *
     * @param id          定时任务 ID
     * @param enabled     是否启用
     * @param workspaceId 工作区 ID
     */
    void toggleCronJob(Long id, boolean enabled, Long workspaceId);

    /**
     * 立即执行定时任务
     *
     * @param id          定时任务 ID
     * @param workspaceId 工作区 ID
     */
    void runCronJobNow(Long id, Long workspaceId);
}
