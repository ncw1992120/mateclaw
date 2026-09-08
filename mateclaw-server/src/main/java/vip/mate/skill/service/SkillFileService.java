package vip.mate.skill.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vip.mate.exception.MateClawException;
import vip.mate.skill.model.SkillFileEntity;
import vip.mate.skill.repository.SkillFileMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Persistence layer for skill bundle files.
 * <p>
 * Treated as the canonical store: every install writes the full set of
 * scripts/references rows here, and {@code SkillFileSyncer} mirrors them
 * to the local workspace cache on every node so script execution works
 * across a multi-instance deployment that shares one database.
 *
 * @author MateClaw Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillFileService {

    /**
     * 单个 bundle 文件的内容大小上限（字节），与 {@code ZipSkillFetcher.MAX_FILE_SIZE}
     * 保持一致，避免 ZIP 安装能写入而在线编辑被拒的不对称限制。
     */
    public static final long MAX_BUNDLE_FILE_SIZE = 1_000_000;

    /** 允许存放 bundle 文件的两个目录前缀。 */
    private static final String REFERENCES_PREFIX = "references/";
    private static final String SCRIPTS_PREFIX = "scripts/";

    private final SkillFileMapper mapper;

    /** All file rows owned by a skill. */
    public List<SkillFileEntity> listBySkillId(Long skillId) {
        if (skillId == null) return List.of();
        QueryWrapper<SkillFileEntity> q = new QueryWrapper<>();
        q.eq("skill_id", skillId);
        return mapper.selectList(q);
    }

    /** 按路径查询单个文件行，不存在时返回 null。 */
    public SkillFileEntity getBySkillIdAndPath(Long skillId, String filePath) {
        if (skillId == null || filePath == null || filePath.isBlank()) {
            return null;
        }
        for (SkillFileEntity row : listBySkillId(skillId)) {
            if (filePath.equals(row.getFilePath())) {
                return row;
            }
        }
        return null;
    }

    /**
     * 校验并归一化 bundle 文件路径。
     * <p>
     * 规则与 {@code SkillFileSyncer.materializeOne} 的落盘校验一致：
     * 必须以 {@code references/} 或 {@code scripts/} 为前缀、拒绝相对跳转
     * （{@code ..}）、拒绝绝对路径。统一将反斜杠归一为正斜杠。
     *
     * @param filePath 原始路径
     * @return 归一化后的安全路径
     * @throws MateClawException 路径非法时抛出
     */
    public static String validateBundlePath(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            throw new MateClawException("err.skill.file_path_invalid", 400, "文件路径不能为空");
        }
        String normalized = filePath.replace('\\', '/').trim();
        if (normalized.startsWith("/")) {
            throw new MateClawException("err.skill.file_path_invalid", 400,
                    "文件路径必须为相对路径: " + filePath);
        }
        if (!normalized.startsWith(REFERENCES_PREFIX) && !normalized.startsWith(SCRIPTS_PREFIX)) {
            throw new MateClawException("err.skill.file_path_invalid", 400,
                    "文件路径必须位于 references/ 或 scripts/ 目录下: " + filePath);
        }
        if (normalized.contains("..")) {
            throw new MateClawException("err.skill.file_path_invalid", 400,
                    "文件路径不允许包含相对跳转: " + filePath);
        }
        return normalized;
    }

    /**
     * 更新（或新建）单个 bundle 文件，其余文件保持不变。
     * <p>
     * {@link #applyBundleFiles} 是整体替换语义：传入集合中未提及的同 bucket
     * 文件会被裁剪。因此这里先全量读入现有文件集、仅替换目标路径后整体提交，
     * 并保持 {@code force=false} 以保留空 bucket 保护。
     *
     * @param skillId  所属技能 ID
     * @param filePath 文件路径（references/ 或 scripts/ 下）
     * @param content  新的 UTF-8 文本内容（null 归一为空串）
     * @return 更新后的文件行
     */
    @Transactional
    public SkillFileEntity updateSingleBundleFile(Long skillId, String filePath, String content) {
        String safePath = validateBundlePath(filePath);
        String safeContent = content == null ? "" : content;
        if (safeContent.getBytes(StandardCharsets.UTF_8).length > MAX_BUNDLE_FILE_SIZE) {
            throw new MateClawException("err.skill.file_too_large", 400,
                    "文件内容超过大小上限 " + MAX_BUNDLE_FILE_SIZE + " 字节: " + safePath);
        }
        Map<String, String> fullFiles = new LinkedHashMap<>();
        for (SkillFileEntity row : listBySkillId(skillId)) {
            if (row.getFilePath() != null) {
                fullFiles.put(row.getFilePath(), row.getContent() == null ? "" : row.getContent());
            }
        }
        fullFiles.put(safePath, safeContent);
        applyBundleFiles(skillId, fullFiles, false);
        return getBySkillIdAndPath(skillId, safePath);
    }

    /** Compute SHA-256 hex of a UTF-8 string (used for idempotent diffs). */
    public static String sha256Hex(String content) {
        if (content == null) content = "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable on this JVM", e);
        }
    }

    /**
     * Replace the skill's full file set with {@code newFiles}, using
     * write-then-prune semantics to mirror the on-disk applyBundleFiles.
     *
     * <p>Empty-bundle guard: if {@code newFiles} contains zero entries
     * for a bucket (scripts/ or references/) and there are existing rows
     * for that bucket, the rows are preserved unless {@code force=true}.
     * This blocks the same data-loss scenario that tripped up the FS path.
     *
     * @param skillId  owning skill id
     * @param newFiles new full file set, keyed by path under workspace root
     *                 (e.g. {@code "scripts/run.py"})
     * @param force    bypass empty-bundle guard
     */
    @Transactional
    public ApplyResult applyBundleFiles(Long skillId, Map<String, String> newFiles, boolean force) {
        if (skillId == null) {
            return new ApplyResult(0, 0, false, false);
        }

        Map<String, String> incoming = newFiles == null ? Map.of() : newFiles;
        boolean newHasScripts = bucketHasEntries(incoming, "scripts/");
        boolean newHasRefs = bucketHasEntries(incoming, "references/");

        List<SkillFileEntity> existing = listBySkillId(skillId);
        boolean existingHasScripts = existing.stream().anyMatch(e -> e.getFilePath() != null && e.getFilePath().startsWith("scripts/"));
        boolean existingHasRefs = existing.stream().anyMatch(e -> e.getFilePath() != null && e.getFilePath().startsWith("references/"));

        boolean preserveScripts = !newHasScripts && existingHasScripts && !force;
        boolean preserveRefs = !newHasRefs && existingHasRefs && !force;

        Map<String, SkillFileEntity> existingByPath = new HashMap<>();
        for (SkillFileEntity e : existing) existingByPath.put(e.getFilePath(), e);

        Set<String> keepPaths = new HashSet<>();
        if (preserveScripts) {
            for (SkillFileEntity e : existing) {
                if (e.getFilePath() != null && e.getFilePath().startsWith("scripts/")) {
                    keepPaths.add(e.getFilePath());
                }
            }
        }
        if (preserveRefs) {
            for (SkillFileEntity e : existing) {
                if (e.getFilePath() != null && e.getFilePath().startsWith("references/")) {
                    keepPaths.add(e.getFilePath());
                }
            }
        }
        keepPaths.addAll(incoming.keySet());

        int written = 0;
        LocalDateTime now = LocalDateTime.now();
        for (var entry : incoming.entrySet()) {
            String path = entry.getKey();
            String content = entry.getValue() == null ? "" : entry.getValue();
            String hash = sha256Hex(content);
            int size = content.getBytes(StandardCharsets.UTF_8).length;

            SkillFileEntity prior = existingByPath.get(path);
            if (prior == null) {
                SkillFileEntity row = new SkillFileEntity();
                row.setSkillId(skillId);
                row.setFilePath(path);
                row.setContent(content);
                row.setContentSize(size);
                row.setSha256(hash);
                row.setCreateTime(now);
                row.setUpdateTime(now);
                mapper.insert(row);
                written++;
            } else if (!hash.equals(prior.getSha256())) {
                prior.setContent(content);
                prior.setContentSize(size);
                prior.setSha256(hash);
                prior.setUpdateTime(now);
                mapper.updateById(prior);
                written++;
            }
        }

        int pruned = 0;
        for (SkillFileEntity e : existing) {
            if (!keepPaths.contains(e.getFilePath())) {
                mapper.deleteById(e.getId());
                pruned++;
            }
        }

        if (preserveScripts) {
            log.warn("Refused to prune scripts/ for skill_id={} — new bundle is empty. Pass force=true to override.", skillId);
        }
        if (preserveRefs) {
            log.warn("Refused to prune references/ for skill_id={} — new bundle is empty. Pass force=true to override.", skillId);
        }

        return new ApplyResult(written, pruned, preserveScripts, preserveRefs);
    }

    /** Drop every file row for a skill (used on hard-delete). */
    @Transactional
    public int deleteAllForSkill(Long skillId) {
        if (skillId == null) return 0;
        return mapper.deleteBySkillId(skillId);
    }

    private boolean bucketHasEntries(Map<String, String> files, String prefix) {
        for (String key : files.keySet()) {
            if (key != null && key.startsWith(prefix)) return true;
        }
        return false;
    }

    /** Outcome of {@link #applyBundleFiles}. */
    public record ApplyResult(int rowsWritten,
                              int rowsPruned,
                              boolean scriptsPreservedDueToEmptyBundle,
                              boolean referencesPreservedDueToEmptyBundle) {}
}
