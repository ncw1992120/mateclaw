package vip.mate.client.api;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestTemplate;
import vip.mate.client.constant.ApiPathConstants;
import vip.mate.client.model.PageData;
import vip.mate.client.model.R;
import vip.mate.client.model.Skill;
import vip.mate.client.model.request.SkillArchiveReq;
import vip.mate.client.model.request.SkillPinReq;
import vip.mate.client.model.request.SkillSynthesizeReq;
import vip.mate.client.model.response.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 技能管理客户端
 */
public class SkillClient extends AbstractApiClient {

    public SkillClient(String baseUrl, RestTemplate restTemplate) {
        super(baseUrl, restTemplate);
    }

    /**
     * 分页查询技能列表
     */
    public R<PageData<Skill>> list(int page, int size, String keyword, String skillType,
                                   Boolean enabled, String scanStatus, String sort,
                                   String source, String runtime, String lifecycleState) {
        Map<String, Object> params = new HashMap<>();
        params.put("page", page);
        params.put("size", size);
        params.put("keyword", keyword);
        params.put("skillType", skillType);
        params.put("enabled", enabled);
        params.put("scanStatus", scanStatus);
        params.put("sort", sort);
        params.put("source", source);
        params.put("runtime", runtime);
        params.put("lifecycleState", lifecycleState);
        return get(ApiPathConstants.SKILL, params, new ParameterizedTypeReference<R<PageData<Skill>>>() {});
    }

    /**
     * 获取技能计数统计
     *
     * @return 技能数量统计
     */
    public R<SkillCountsResp> counts() {
        return get(ApiPathConstants.SKILL_COUNTS, new ParameterizedTypeReference<R<SkillCountsResp>>() {});
    }

    /**
     * 重新扫描技能
     */
    public R<Skill> rescan(Long id) {
        return post(resolvePath(ApiPathConstants.SKILL_RESCAN, id), null,
                new ParameterizedTypeReference<R<Skill>>() {});
    }

    /**
     * 同步技能文件
     */
    public R<SkillSyncResp> syncFiles(Long id) {
        return post(resolvePath(ApiPathConstants.SKILL_SYNC_FILES, id), null,
                new ParameterizedTypeReference<R<SkillSyncResp>>() {});
    }

    /**
     * 同步所有技能文件
     */
    public R<SkillSyncAllResp> syncAllFiles() {
        return post(ApiPathConstants.SKILL_SYNC_ALL_FILES, null,
                new ParameterizedTypeReference<R<SkillSyncAllResp>>() {});
    }

    /**
     * 获取已启用的技能列表
     */
    public R<List<Skill>> listEnabled() {
        return get(ApiPathConstants.SKILL_ENABLED, new ParameterizedTypeReference<R<List<Skill>>>() {});
    }

    /**
     * 按类型获取技能列表
     */
    public R<List<Skill>> listByType(String skillType) {
        return get(resolvePath(ApiPathConstants.SKILL_BY_TYPE, skillType),
                new ParameterizedTypeReference<R<List<Skill>>>() {});
    }

    /**
     * 获取技能摘要
     *
     * @return 技能摘要信息
     */
    public R<SkillSummaryResp> summary() {
        return get(ApiPathConstants.SKILL_SUMMARY, new ParameterizedTypeReference<R<SkillSummaryResp>>() {});
    }

    /**
     * 获取技能详情
     */
    public R<Skill> get(Long id) {
        return get(resolvePath(ApiPathConstants.SKILL_BY_ID, id), new ParameterizedTypeReference<R<Skill>>() {});
    }

    /**
     * 创建技能
     */
    public R<Skill> create(Skill skill) {
        return post(ApiPathConstants.SKILL, skill, new ParameterizedTypeReference<R<Skill>>() {});
    }

    /**
     * 更新技能
     */
    public R<Skill> update(Long id, Skill skill) {
        return put(resolvePath(ApiPathConstants.SKILL_BY_ID, id), skill,
                new ParameterizedTypeReference<R<Skill>>() {});
    }

    /**
     * 删除技能
     */
    public R<Void> delete(Long id) {
        return delete(resolvePath(ApiPathConstants.SKILL_BY_ID, id), new ParameterizedTypeReference<R<Void>>() {});
    }

    /**
     * 切换技能启用/禁用状态
     */
    public R<Skill> toggle(Long id, boolean enabled) {
        return put(resolvePath(ApiPathConstants.SKILL_TOGGLE, id) + "?enabled=" + enabled, null,
                new ParameterizedTypeReference<R<Skill>>() {});
    }

    /**
     * 预览技能提示词
     */
    public R<PromptPreviewResp> promptPreview() {
        return get(ApiPathConstants.SKILL_PROMPT_PREVIEW,
                new ParameterizedTypeReference<R<PromptPreviewResp>>() {});
    }

    /**
     * 获取运行时活跃技能
     */
    public R<SkillActiveDataResp> getActiveSkills() {
        return get(ApiPathConstants.SKILL_RUNTIME_ACTIVE,
                new ParameterizedTypeReference<R<SkillActiveDataResp>>() {});
    }

    /**
     * 获取运行时状态
     */
    public R<List<ResolvedSkillResp>> getRuntimeStatus() {
        return get(ApiPathConstants.SKILL_RUNTIME_STATUS,
                new ParameterizedTypeReference<R<List<ResolvedSkillResp>>>() {});
    }

    /**
     * 刷新运行时
     */
    public R<SkillRuntimeRefreshResp> refreshRuntime(boolean resync) {
        return post(ApiPathConstants.SKILL_RUNTIME_REFRESH + "?resync=" + resync, null,
                new ParameterizedTypeReference<R<SkillRuntimeRefreshResp>>() {});
    }

    /**
     * 获取技能依赖要求
     */
    public R<SkillRequirementsResp> requirements(Long id) {
        return get(resolvePath(ApiPathConstants.SKILL_REQUIREMENTS, id),
                new ParameterizedTypeReference<R<SkillRequirementsResp>>() {});
    }

    /**
     * 获取技能关联的员工列表
     */
    public R<List<SkillEmployeeResp>> employees(Long id) {
        return get(resolvePath(ApiPathConstants.SKILL_EMPLOYEES, id),
                new ParameterizedTypeReference<R<List<SkillEmployeeResp>>>() {});
    }

    /**
     * 获取技能经验教训
     */
    public R<SkillLessonsResp> getLessons(Long id) {
        return get(resolvePath(ApiPathConstants.SKILL_LESSONS, id),
                new ParameterizedTypeReference<R<SkillLessonsResp>>() {});
    }

    /**
     * 清除技能经验教训
     */
    public R<SkillLessonsClearResp> clearLessons(Long id) {
        return post(resolvePath(ApiPathConstants.SKILL_LESSONS_CLEAR, id), null,
                new ParameterizedTypeReference<R<SkillLessonsClearResp>>() {});
    }

    /**
     * 从对话合成技能
     *
     * @param request 合成请求参数
     * @return 合成结果
     */
    public R<SkillSynthesizeResp> synthesizeFromConversation(SkillSynthesizeReq request) {
        return post(ApiPathConstants.SKILL_SYNTHESIZE, request,
                new ParameterizedTypeReference<R<SkillSynthesizeResp>>() {});
    }

    /**
     * 导出技能到工作区
     */
    public R<SkillExportResp> exportToWorkspace(Long id) {
        return post(resolvePath(ApiPathConstants.SKILL_EXPORT_WORKSPACE, id), null,
                new ParameterizedTypeReference<R<SkillExportResp>>() {});
    }

    /**
     * 获取技能工作区信息
     */
    public R<SkillWorkspaceInfoResp> getWorkspaceInfo(Long id) {
        return get(resolvePath(ApiPathConstants.SKILL_WORKSPACE_INFO, id),
                new ParameterizedTypeReference<R<SkillWorkspaceInfoResp>>() {});
    }

    /**
     * 置顶/取消置顶技能
     *
     * @param id      技能 ID
     * @param request 置顶请求
     * @return 更新后的技能
     */
    public R<Skill> pin(Long id, SkillPinReq request) {
        return post(resolvePath(ApiPathConstants.SKILL_PIN, id), request,
                new ParameterizedTypeReference<R<Skill>>() {});
    }

    /**
     * 归档技能
     *
     * @param id      技能 ID
     * @param force   是否强制归档
     * @param request 归档请求
     * @return 更新后的技能
     */
    public R<Skill> archive(Long id, boolean force, SkillArchiveReq request) {
        return post(resolvePath(ApiPathConstants.SKILL_ARCHIVE, id) + "?force=" + force, request,
                new ParameterizedTypeReference<R<Skill>>() {});
    }

    /**
     * 恢复已归档技能
     */
    public R<Skill> restore(Long id) {
        return post(resolvePath(ApiPathConstants.SKILL_RESTORE, id), null,
                new ParameterizedTypeReference<R<Skill>>() {});
    }

    /**
     * 策展器试运行
     */
    public R<SkillCuratorReportResp> curatorDryRun() {
        return post(ApiPathConstants.SKILL_CURATOR_DRY_RUN, null,
                new ParameterizedTypeReference<R<SkillCuratorReportResp>>() {});
    }

    /**
     * 策展器激活
     */
    public R<CuratorStatusResp> curatorActivate(boolean activate) {
        return post(ApiPathConstants.SKILL_CURATOR_ACTIVATE + "?activate=" + activate, null,
                new ParameterizedTypeReference<R<CuratorStatusResp>>() {});
    }

    /**
     * 策展器暂停
     */
    public R<CuratorStatusResp> curatorPause() {
        return post(ApiPathConstants.SKILL_CURATOR_PAUSE, null,
                new ParameterizedTypeReference<R<CuratorStatusResp>>() {});
    }

    /**
     * 策展器恢复
     */
    public R<CuratorStatusResp> curatorResume() {
        return post(ApiPathConstants.SKILL_CURATOR_RESUME, null,
                new ParameterizedTypeReference<R<CuratorStatusResp>>() {});
    }

    /**
     * 获取策展器状态
     */
    public R<CuratorStatusResp> curatorStatus() {
        return get(ApiPathConstants.SKILL_CURATOR_STATUS,
                new ParameterizedTypeReference<R<CuratorStatusResp>>() {});
    }

    /**
     * 获取策展器报告列表
     */
    public R<List<String>> curatorReports() {
        return get(ApiPathConstants.SKILL_CURATOR_REPORTS,
                new ParameterizedTypeReference<R<List<String>>>() {});
    }

    /**
     * 获取策展器报告详情
     */
    public R<Object> curatorReport(String runId) {
        return get(resolvePath(ApiPathConstants.SKILL_CURATOR_REPORT_BY_ID, runId),
                new ParameterizedTypeReference<R<Object>>() {});
    }

    /**
     * 解析技能
     *
     * @param name 技能名称
     * @return 解析后的技能信息
     */
    public R<ResolvedSkillResp> resolve(String name) {
        return get(resolvePath(ApiPathConstants.SKILL + "/resolve", name),
                new ParameterizedTypeReference<R<ResolvedSkillResp>>() {});
    }
}
