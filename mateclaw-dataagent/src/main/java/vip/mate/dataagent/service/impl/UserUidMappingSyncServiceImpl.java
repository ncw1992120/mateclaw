package vip.mate.dataagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import vip.mate.auth.model.UserEntity;
import vip.mate.auth.repository.UserMapper;
import vip.mate.auth.service.AuthService;
import vip.mate.dataagent.config.UidSyncProperties;
import vip.mate.dataagent.constants.DataAgentConstants;
import vip.mate.dataagent.dto.UserUidSyncResultDTO;
import vip.mate.dataagent.exception.BusinessException;
import vip.mate.dataagent.model.UserUidMappingEntity;
import vip.mate.dataagent.repository.UserUidMappingMapper;
import vip.mate.dataagent.service.UserUidMappingSyncService;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 用户 Aloudata UID 映射同步服务实现
 * <p>
 * 通过 JDBC 直连外部用户系统拉取「登录名 + 租户 → UID」映射数据（驱动按源库 URL 前缀自动识别，
 * 支持 MySQL / PostgreSQL），全量 upsert 到本地映射表；源库中已删除的记录在本地置为停用（软失效），
 * 保证映射可追溯。千级数据量采用全量同步，非实时场景足够。
 * <p>
 * 同步入口统一在此加锁：定时任务与管理员手动触发共用同一把锁，避免并发全量同步交错写入。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserUidMappingSyncServiceImpl implements UserUidMappingSyncService {

    /** 同步互斥锁名称（定时任务与手动触发共用） */
    private static final String SYNC_LOCK_NAME = "userUidMappingSync";

    /** 锁最长持有时长：同步异常中断时兜底自动释放 */
    private static final Duration SYNC_LOCK_AT_MOST_FOR = Duration.ofMinutes(30);

    /** 锁最短持有时长：防止相邻触发点重复执行 */
    private static final Duration SYNC_LOCK_AT_LEAST_FOR = Duration.ofSeconds(1);

    /** 源库查询超时（秒），防止外部库网络挂起拖垮同步线程 */
    private static final int SOURCE_QUERY_TIMEOUT_SECONDS = 60;

    /**
     * 拉取 SQL 必须返回的三列：登录名 / 租户 / UID（列名或 {@code as} 别名均可）
     * <p>
     * 取值按列名而非列序，大小写不敏感、列序任意。
     */
    private static final String COLUMN_USERNAME = "username";
    private static final String COLUMN_TENANT_ID = "tenant_id";
    private static final String COLUMN_UID = "aloudata_uid";
    private static final List<String> REQUIRED_COLUMNS =
            List.of(COLUMN_USERNAME, COLUMN_TENANT_ID, COLUMN_UID);

    /** 拉取 SQL 可选返回的昵称列（仅展示用，缺失时回落 mate_user.nickname） */
    private static final String COLUMN_NICKNAME = "nickname";

    /** 与目标表列宽一致：超长用户名/租户不可能命中本地用户或数据源，跳过该行 */
    private static final int MAX_USERNAME_LENGTH = 128;
    private static final int MAX_TENANT_ID_LENGTH = 128;

    /** 昵称列宽（展示用字段，超长按列宽截断） */
    private static final int MAX_NICKNAME_LENGTH = 128;

    /** 本地昵称补齐的分批查询大小 */
    private static final int USER_LOOKUP_BATCH_SIZE = 500;

    private final UserUidMappingMapper userUidMappingMapper;
    private final UidSyncProperties uidSyncProperties;
    private final LockProvider lockProvider;
    private final TransactionTemplate transactionTemplate;
    private final UserMapper userMapper;

    /**
     * 执行一次全量同步
     * <p>
     * 先取同步锁（定时任务与手动触发共用），源库连接即用即关、拉取阶段在本地事务外执行；
     * 写入与失效在同一事务内，失败整体回滚，上一次成功同步的映射保留继续可用。
     */
    @Override
    public UserUidSyncResultDTO syncFromSource() {
        if (!uidSyncProperties.isSourceConfigured()) {
            throw new BusinessException(400,
                    "UID 映射同步源未配置，请检查 dataagent.uid-sync.source-url（或 source-type/host/port/database 分片）"
                            + " / source-username / query-sql");
        }
        Optional<SimpleLock> lock = lockProvider.lock(new LockConfiguration(
                Instant.now(), SYNC_LOCK_NAME, SYNC_LOCK_AT_MOST_FOR, SYNC_LOCK_AT_LEAST_FOR));
        if (lock.isEmpty()) {
            throw new BusinessException(409, "UID 映射同步正在执行中，请稍后重试");
        }
        long startMs = System.currentTimeMillis();
        try {
            FetchResult fetch = fetchFromSource();
            // 昵称仅展示用，补齐失败不应导致整次同步失败
            try {
                fillLocalNicknames(fetch.mappings());
            } catch (Exception e) {
                log.warn("[UidSync] 本地昵称补齐失败，本次昵称以源库为准: {}", e.getMessage());
            }

            UserUidSyncResultDTO result = new UserUidSyncResultDTO();
            result.setFetched(fetch.mappings().size());
            result.setSkipped(fetch.skipped());
            // 写入 + 软失效同事务：拉取已在事务外完成，此处不再持有源库连接
            transactionTemplate.executeWithoutResult(status -> {
                result.setUpserted(upsertMappings(fetch.mappings()));
                result.setDisabled(disableStaleMappings(fetch.mappings()));
            });

            log.info("[UidSync] 全量同步完成: fetched={}, upserted={}, disabled={}, skipped={}, costMs={}",
                    result.getFetched(), result.getUpserted(), result.getDisabled(), result.getSkipped(),
                    System.currentTimeMillis() - startMs);
            return result;
        } finally {
            lock.get().unlock();
        }
    }

    /**
     * 从源库拉取映射数据并构建实体列表
     * <p>
     * 拉取 SQL 必须返回 username / tenant_id / aloudata_uid 三列（列名或 {@code as} 别名均可），
     * 取值按列名（大小写不敏感、列序不限），列名缺失时显式失败；
     * 可选返回 nickname 列，缺失时由 {@link #fillLocalNicknames} 回落本地昵称；
     * 关键字段为空、或用户名/租户超出目标列宽的行计入 skipped，
     * 批内按（登录名 + 租户）去重（同键保留最后一条，取舍依赖结果集顺序，
     * 需要稳定取舍时拉取 SQL 应带确定性排序）。
     */
    private FetchResult fetchFromSource() {
        Map<List<String>, UserUidMappingEntity> byKey = new LinkedHashMap<>();
        int skipped = 0;
        int rowCount = 0;
        LocalDateTime now = LocalDateTime.now();
        // URL 优先取 source-url；留空时按 source-type/host/port/database 分片拼装
        String sourceUrl = uidSyncProperties.resolvedSourceUrl();
        try (Connection conn = DriverManager.getConnection(sourceUrl,
                uidSyncProperties.getSourceUsername(), uidSyncProperties.getSourcePassword());
             PreparedStatement ps = conn.prepareStatement(uidSyncProperties.getQuerySql())) {
            ps.setQueryTimeout(SOURCE_QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = ps.executeQuery()) {
                // 先按列名定位三列：列序写错或别名缺失在此显式失败，
                // 避免错位取值（如 UID 写进 tenant_id）后同步"成功"但映射全部命中不上
                ColumnLabels labels = resolveColumns(rs.getMetaData());
                while (rs.next()) {
                    rowCount++;
                    String username = rs.getString(labels.username());
                    String tenantId = rs.getString(labels.tenantId());
                    String uid = rs.getString(labels.uid());
                    if (isBlank(username) || isBlank(tenantId) || isBlank(uid)) {
                        skipped++;
                        continue;
                    }
                    // 登录名按 mate_user 同口径归一化，保证问数时按登录名能命中映射
                    String normalizedUsername = AuthService.normalizeUsername(username);
                    String trimmedTenantId = tenantId.trim();
                    // 超长值写库会直接失败并拖垮整批同步，且不可能命中本地用户/数据源，跳过
                    if (normalizedUsername.length() > MAX_USERNAME_LENGTH
                            || trimmedTenantId.length() > MAX_TENANT_ID_LENGTH) {
                        skipped++;
                        continue;
                    }
                    UserUidMappingEntity entity = new UserUidMappingEntity();
                    entity.setUsername(normalizedUsername);
                    entity.setTenantId(trimmedTenantId);
                    entity.setNickname(readNickname(rs, labels));
                    entity.setAloudataUid(uid.trim());
                    entity.setStatus(1);
                    entity.setSyncSource(DataAgentConstants.UID_SYNC_SOURCE_JDBC);
                    entity.setSyncTime(now);
                    // 同键保留最后一条：源库结果按序返回时以最后一条为准
                    byKey.put(List.of(normalizedUsername, trimmedTenantId), entity);
                }
            }
        } catch (BusinessException e) {
            // 列校验等已明确归因的错误：保留原始提示，不降级为通用失败文案
            throw e;
        } catch (Exception e) {
            // 源库异常细节只进日志：JDBC 异常消息常带完整连接串（可能内嵌凭据），不回传客户端
            log.error("[UidSync] 源库拉取失败", e);
            throw new BusinessException(500, "UID 映射源库拉取失败，请检查同步源配置与网络连通性（详见服务端日志）");
        }
        // 同键重复行的取舍依赖结果集顺序：源库 SQL 无 ORDER BY 时不确定，
        // 数量异常增多说明源库存在一户多行，建议在拉取 SQL 中加确定性排序
        int duplicates = rowCount - skipped - byKey.size();
        if (duplicates > 0) {
            log.info("[UidSync] 源库结果存在 {} 个同键重复行（登录名+租户），已按结果顺序保留最后一条", duplicates);
        }
        return new FetchResult(new ArrayList<>(byKey.values()), skipped);
    }

    /**
     * 读取可选昵称列并按列宽截断（展示用字段，截断不影响映射生效）
     *
     * @return 源库昵称，SQL 未返回该列时为 null
     */
    private String readNickname(ResultSet rs, ColumnLabels labels) throws SQLException {
        if (labels.nickname() == null) {
            return null;
        }
        String nickname = rs.getString(labels.nickname());
        if (nickname == null) {
            return null;
        }
        String trimmed = nickname.trim();
        return trimmed.length() > MAX_NICKNAME_LENGTH
                ? trimmed.substring(0, MAX_NICKNAME_LENGTH)
                : trimmed;
    }

    /**
     * 补齐本地昵称：源库未提供 nickname（或为空）的映射回落到 mate_user.nickname
     * <p>
     * 本地昵称对所有能登录的用户必然存在，补上后昵称列即可直接用于展示；
     * 源库昵称优先，本地仅填空缺，不覆盖源库值。
     */
    private void fillLocalNicknames(List<UserUidMappingEntity> mappings) {
        List<UserUidMappingEntity> missing = mappings.stream()
                .filter(mapping -> isBlank(mapping.getNickname()))
                .toList();
        if (missing.isEmpty()) {
            return;
        }
        List<String> usernames = missing.stream()
                .map(UserUidMappingEntity::getUsername)
                .distinct()
                .toList();
        Map<String, String> nicknameByUsername = new HashMap<>();
        Set<String> foundUsernames = new HashSet<>();
        for (int i = 0; i < usernames.size(); i += USER_LOOKUP_BATCH_SIZE) {
            List<String> batch = usernames.subList(i, Math.min(i + USER_LOOKUP_BATCH_SIZE, usernames.size()));
            userMapper.selectList(new LambdaQueryWrapper<UserEntity>()
                            .in(UserEntity::getUsername, batch)
                            .select(UserEntity::getUsername, UserEntity::getNickname))
                    .forEach(user -> {
                        foundUsernames.add(user.getUsername());
                        if (!isBlank(user.getNickname())) {
                            nicknameByUsername.put(user.getUsername(), user.getNickname());
                        }
                    });
        }
        // 对账：同步登录名在本地用户表无对应用户的映射永远无法按登录名命中
        //（承重假设"源库账号 == 平台登录名"），数量异常时优先排查两边账号体系是否一致
        long unmatched = usernames.size() - foundUsernames.size();
        if (unmatched > 0) {
            log.warn("[UidSync] {} 个同步登录名在本地用户表无对应用户，相关映射不会生效，"
                    + "请核对源库账号与平台登录名是否一致", unmatched);
        }
        for (UserUidMappingEntity mapping : missing) {
            mapping.setNickname(nicknameByUsername.get(mapping.getUsername()));
        }
    }

    /**
     * 按列名定位拉取结果中的各列
     * <p>
     * 必需三列缺失时抛出 400 并回显实际列名，便于直接定位 SQL 配置问题；
     * 可选昵称列缺失时返回 null，由本地昵称补齐。
     *
     * @param metaData 结果集元数据
     * @return 必需列与可选昵称列的实际列标签
     */
    private ColumnLabels resolveColumns(ResultSetMetaData metaData) throws SQLException {
        Map<String, String> labelByLowerName = new LinkedHashMap<>();
        for (int i = 1; i <= metaData.getColumnCount(); i++) {
            String label = metaData.getColumnLabel(i);
            if (label != null && !label.isBlank()) {
                labelByLowerName.putIfAbsent(label.trim().toLowerCase(Locale.ROOT), label.trim());
            }
        }
        List<String> missing = REQUIRED_COLUMNS.stream()
                .filter(required -> !labelByLowerName.containsKey(required))
                .toList();
        if (!missing.isEmpty()) {
            throw new BusinessException(400, "UID 映射拉取 SQL 必须返回 "
                    + String.join(" / ", REQUIRED_COLUMNS) + " 三列（列名或 as 别名均可），当前缺失: "
                    + String.join(" / ", missing) + "，实际列: "
                    + String.join(" / ", labelByLowerName.values()));
        }
        return new ColumnLabels(labelByLowerName.get(COLUMN_USERNAME),
                labelByLowerName.get(COLUMN_TENANT_ID),
                labelByLowerName.get(COLUMN_UID),
                labelByLowerName.get(COLUMN_NICKNAME));
    }

    /**
     * 分批 upsert 映射记录（冲突键 username + tenant_id）
     */
    private int upsertMappings(List<UserUidMappingEntity> mappings) {
        if (mappings.isEmpty()) {
            return 0;
        }
        int upserted = 0;
        int batchSize = uidSyncProperties.getBatchSize();
        for (int i = 0; i < mappings.size(); i += batchSize) {
            List<UserUidMappingEntity> batch =
                    mappings.subList(i, Math.min(i + batchSize, mappings.size()));
            userUidMappingMapper.upsertBatch(batch);
            upserted += batch.size();
        }
        return upserted;
    }

    /**
     * 软失效源库中已不存在的映射：本次同步产生的启用记录若不在拉取结果中，置为停用
     * <p>
     * 只处理 {@code sync_source = jdbc_sync} 的记录，手动录入（manual）的记录不参与软失效，
     * 避免定时同步覆盖人工维护的数据；拉取结果为 0 条时跳过软失效，
     * 防止源库 SQL 误配（返回空集）把全部用户的映射一次性停用。
     */
    private int disableStaleMappings(List<UserUidMappingEntity> mappings) {
        if (mappings.isEmpty()) {
            log.warn("[UidSync] 本次拉取 0 条映射，跳过软失效以免误停用全部映射，请检查 source-url / query-sql");
            return 0;
        }
        Set<List<String>> syncedKeys = new HashSet<>();
        for (UserUidMappingEntity mapping : mappings) {
            syncedKeys.add(List.of(mapping.getUsername(), mapping.getTenantId()));
        }
        List<UserUidMappingEntity> actives = userUidMappingMapper.selectList(
                new LambdaQueryWrapper<UserUidMappingEntity>()
                        .eq(UserUidMappingEntity::getSyncSource, DataAgentConstants.UID_SYNC_SOURCE_JDBC)
                        .eq(UserUidMappingEntity::getStatus, 1)
                        .select(UserUidMappingEntity::getId,
                                UserUidMappingEntity::getUsername,
                                UserUidMappingEntity::getTenantId));
        List<Long> staleIds = actives.stream()
                .filter(active -> !syncedKeys.contains(List.of(active.getUsername(), active.getTenantId())))
                .map(UserUidMappingEntity::getId)
                .toList();
        if (staleIds.isEmpty()) {
            return 0;
        }
        LambdaUpdateWrapper<UserUidMappingEntity> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.in(UserUidMappingEntity::getId, staleIds)
                .set(UserUidMappingEntity::getStatus, 0)
                .set(UserUidMappingEntity::getSyncTime, LocalDateTime.now());
        userUidMappingMapper.update(null, updateWrapper);
        return staleIds.size();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * 源库拉取结果
     *
     * @param mappings 有效映射（已按登录名 + 租户去重）
     * @param skipped  关键字段为空、或用户名/租户超出列宽被跳过的行数
     */
    private record FetchResult(List<UserUidMappingEntity> mappings, int skipped) {
    }

    /**
     * 拉取结果中各列的实际列标签
     *
     * @param username 登录名列标签
     * @param tenantId 租户列标签
     * @param uid      UID 列标签
     * @param nickname 昵称列标签，SQL 未返回该列时为 null
     */
    private record ColumnLabels(String username, String tenantId, String uid, String nickname) {
    }
}
