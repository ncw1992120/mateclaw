package vip.mate.dataagent.auth.crypto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 存量明文密码迁移任务
 * <p>
 * 存储加密上线前，dataagent_datasource.password 与
 * dataagent_datasource_account.query_password 为明文。应用启动后扫描
 * 非 {@link AesPasswordCryptor#ENCRYPTED_PREFIX} 前缀的记录，统一加密回写，
 * 保证存量数据在双加密改造后仍可正常连接使用。
 * <p>
 * 通过 JdbcTemplate 原生 SQL 写回，绕开实体上的 TypeHandler，避免二次加密。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PasswordCryptoMigrationRunner implements ApplicationRunner {

    /** 数据源主表密码列 */
    private static final String TABLE_DATASOURCE = "dataagent_datasource";

    /** 查询账号表密码列 */
    private static final String TABLE_ACCOUNT = "dataagent_datasource_account";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        int migratedDatasource = migrateColumn(TABLE_DATASOURCE, "password");
        int migratedAccount = migrateColumn(TABLE_ACCOUNT, "query_password");
        if (migratedDatasource + migratedAccount > 0) {
            log.info("[PwdCrypto] 存量明文密码迁移完成: 数据源 {} 条, 查询账号 {} 条",
                    migratedDatasource, migratedAccount);
        }
    }

    /**
     * 迁移单张表的指定密码列：非空且未加密的记录加密回写。
     *
     * @param tableName  表名
     * @param columnName 密码列名
     * @return 迁移条数
     */
    private int migrateColumn(String tableName, String columnName) {
        // 表名/列名为内部常量（非用户输入），且均为小写字母+下划线，无需引号包裹以兼容 MySQL/PG 两种方言
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, " + columnName + " AS pwd FROM " + tableName + " WHERE "
                        + columnName + " IS NOT NULL AND "
                        + columnName + " <> '' AND "
                        + columnName + " NOT LIKE '" + AesPasswordCryptor.ENCRYPTED_PREFIX + "%'");
        int count = 0;
        for (Map<String, Object> row : rows) {
            Object id = row.get("id");
            Object pwd = row.get("pwd");
            if (id == null || pwd == null) {
                continue;
            }
            String encrypted = AesPasswordCryptor.encrypt(pwd.toString());
            jdbcTemplate.update("UPDATE " + tableName + " SET " + columnName + " = ? WHERE id = ?",
                    encrypted, id);
            count++;
        }
        return count;
    }
}