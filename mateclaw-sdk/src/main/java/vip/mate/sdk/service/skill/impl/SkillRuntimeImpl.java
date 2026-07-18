package vip.mate.sdk.service.skill.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import vip.mate.exception.MateClawException;
import vip.mate.sdk.service.skill.SkillRuntime;
import vip.mate.skill.installer.SkillInstaller;
import vip.mate.skill.installer.ZipSkillFetcher;
import vip.mate.skill.installer.model.HubSkillInfo;
import vip.mate.skill.installer.model.InstallRequest;
import vip.mate.skill.installer.model.InstallTask;
import vip.mate.skill.model.SkillEntity;
import vip.mate.skill.runtime.SkillFrontmatterParser;
import vip.mate.skill.service.SkillService;

import java.util.List;
import java.util.Map;

/**
 * 技能运行时实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillRuntimeImpl implements SkillRuntime {

    private final SkillService skillService;
    private final SkillInstaller skillInstaller;
    private final SkillFrontmatterParser skillFrontmatterParser;

    @Override
    public IPage<SkillEntity> pageSkills(int page, int size, String keyword, String skillType,
                                          Boolean enabled, Long workspaceId,
                                          String sort, String lifecycleState) {
        return skillService.pageSkills(page, size, keyword, skillType, enabled, null, sort, null, null,
                java.util.Set.of(), workspaceId, lifecycleState);
    }

    @Override
    public List<SkillEntity> listSkills(Long workspaceId) {
        return skillService.listSkills(workspaceId);
    }

    @Override
    public List<SkillEntity> listEnabledSkills(Long workspaceId) {
        return skillService.listEnabledSkills(workspaceId);
    }

    @Override
    public SkillEntity getSkill(Long id) {
        return skillService.getSkill(id);
    }

    @Override
    public SkillEntity createSkill(SkillEntity entity) {
        return skillService.createSkill(entity);
    }

    @Override
    public SkillEntity updateSkill(SkillEntity entity) {
        return skillService.updateSkill(entity);
    }

    @Override
    public void hardDeleteSkill(Long id) {
        skillService.hardDeleteSkill(id);
    }

    @Override
    public SkillEntity toggleSkill(Long id, boolean enabled) {
        return skillService.toggleSkill(id, enabled);
    }

    @Override
    public List<HubSkillInfo> searchSkillHub(String query, int limit) {
        return skillInstaller.searchHub(query, limit);
    }

    @Override
    public InstallTask startInstallSkill(InstallRequest request) {
        return skillInstaller.startInstall(request);
    }

    @Override
    public InstallTask getInstallTaskStatus(String taskId) {
        return skillInstaller.getTaskStatus(taskId);
    }

    @Override
    public void cancelInstallTask(String taskId) {
        skillInstaller.cancelTask(taskId);
    }

    /**
     * 通过上传 ZIP 包同步安装技能
     */
    @Override
    public Map<String, Object> installSkillFromZip(MultipartFile zipFile, boolean enable, boolean overwrite,
                                                   String targetName, Long workspaceId) {
        if (zipFile == null || zipFile.isEmpty()) {
            throw new MateClawException("err.skill.zip_empty", "ZIP 文件不能为空");
        }
        String filename = zipFile.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".zip")) {
            throw new MateClawException("err.skill.zip_invalid", "仅支持 .zip 文件");
        }
        try {
            var bundle = ZipSkillFetcher.parse(zipFile, skillFrontmatterParser);
            return skillInstaller.installFromBundle(bundle, enable, overwrite, targetName, workspaceId);
        } catch (MateClawException e) {
            throw e;
        } catch (Exception e) {
            log.error("ZIP install failed: {}", e.getMessage(), e);
            throw new MateClawException("err.skill.zip_install_failed", "ZIP 安装失败: " + e.getMessage());
        }
    }

    @Override
    public void uninstallSkillByName(String skillName, Long workspaceId) {
        skillInstaller.uninstall(skillName, workspaceId);
    }
}
