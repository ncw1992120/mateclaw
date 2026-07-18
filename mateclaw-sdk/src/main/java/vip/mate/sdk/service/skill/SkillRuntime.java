package vip.mate.sdk.service.skill;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.springframework.web.multipart.MultipartFile;
import vip.mate.skill.installer.model.HubSkillInfo;
import vip.mate.skill.installer.model.InstallRequest;
import vip.mate.skill.installer.model.InstallTask;
import vip.mate.skill.model.SkillEntity;

import java.util.List;
import java.util.Map;

/**
 * 技能运行时接口
 * <p>
 * 提供技能 CRUD、导入安装等编程式访问能力。
 */
public interface SkillRuntime {

    /**
     * 获取技能分页列表
     *
     * @param page           页码（从 1 开始）
     * @param size           每页条数
     * @param keyword        关键字（可选，模糊匹配名称/描述/标签）
     * @param skillType      技能类型（可选，builtin / mcp / dynamic 等）
     * @param enabled        是否启用（可选）
     * @param workspaceId    工作区 ID（可选）
     * @param sort           排序字段（可选）
     * @param lifecycleState 生命周期状态（可选）
     * @return 技能分页数据
     */
    IPage<SkillEntity> pageSkills(
            int page, int size, String keyword, String skillType, Boolean enabled,
            Long workspaceId, String sort, String lifecycleState);

    /**
     * 获取所有技能列表（不分页）
     *
     * @param workspaceId 工作区 ID（可选）
     * @return 技能列表
     */
    List<SkillEntity> listSkills(Long workspaceId);

    /**
     * 获取已启用技能列表
     *
     * @param workspaceId 工作区 ID（可选）
     * @return 已启用技能列表
     */
    List<SkillEntity> listEnabledSkills(Long workspaceId);

    /**
     * 获取技能详情
     *
     * @param id 技能 ID
     * @return 技能实体
     */
    SkillEntity getSkill(Long id);

    /**
     * 创建技能
     *
     * @param entity 技能实体
     * @return 创建后的技能实体
     */
    SkillEntity createSkill(SkillEntity entity);

    /**
     * 更新技能
     *
     * @param entity 技能实体（需包含 ID）
     * @return 更新后的技能实体
     */
    SkillEntity updateSkill(SkillEntity entity);

    /**
     * 硬删除技能（admin only）
     *
     * @param id 技能 ID
     */
    void hardDeleteSkill(Long id);

    /**
     * 切换技能启停状态
     *
     * @param id      技能 ID
     * @param enabled 是否启用
     * @return 更新后的技能实体
     */
    SkillEntity toggleSkill(Long id, boolean enabled);

    /**
     * 在 ClawHub 市场搜索可用技能
     *
     * @param query 搜索关键词
     * @param limit 返回条数上限
     * @return 市场技能信息列表
     */
    List<HubSkillInfo> searchSkillHub(String query, int limit);

    /**
     * 启动一个异步技能安装任务
     * <p>
     * 适用于从 GitHub 仓库或 ClawHub 市场安装，支持取消与任务状态轮询。
     *
     * @param request 安装请求（含 bundleUrl、version、enable、overwrite、targetName 等）
     * @return 安装任务（包含 taskId 与初始状态）
     */
    InstallTask startInstallSkill(InstallRequest request);

    /**
     * 查询安装任务状态
     *
     * @param taskId 任务 ID
     * @return 任务状态对象，未找到时返回 null
     */
    InstallTask getInstallTaskStatus(String taskId);

    /**
     * 取消正在执行的安装任务
     *
     * @param taskId 任务 ID
     */
    void cancelInstallTask(String taskId);

    /**
     * 通过上传 ZIP 包同步安装技能
     *
     * @param zipFile     上传的 ZIP 文件
     * @param enable      安装后是否启用
     * @param overwrite   同名技能已存在时是否覆盖
     * @param targetName  指定安装后的 skill 名称（可选）
     * @param workspaceId 所属工作区 ID
     * @return 安装结果摘要（skillId、name、version、filesCount）
     */
    Map<String, Object> installSkillFromZip(MultipartFile zipFile, boolean enable, boolean overwrite,
                                            String targetName, Long workspaceId);

    /**
     * 通过名称卸载技能
     *
     * @param skillName   技能名称
     * @param workspaceId 所属工作区 ID
     */
    void uninstallSkillByName(String skillName, Long workspaceId);
}
