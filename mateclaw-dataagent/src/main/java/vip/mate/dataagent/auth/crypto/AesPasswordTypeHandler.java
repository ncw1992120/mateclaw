package vip.mate.dataagent.auth.crypto;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 密码字段存储加密 TypeHandler
 * <p>
 * 挂载在 {@code dataagent_datasource.password} 与
 * {@code dataagent_datasource_account.query_password} 列对应的实体字段上，
 * 写入时统一 AES-256-GCM 加密落库、读取时自动解密；业务代码对明文透明，
 * 无需在 JDBC 连接、Aloudata 认证等消费点逐一改造。
 */
@MappedTypes(String.class)
@MappedJdbcTypes(JdbcType.VARCHAR)
public class AesPasswordTypeHandler extends BaseTypeHandler<String> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, AesPasswordCryptor.encrypt(parameter));
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return AesPasswordCryptor.decrypt(rs.getString(columnName));
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return AesPasswordCryptor.decrypt(rs.getString(columnIndex));
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return AesPasswordCryptor.decrypt(cs.getString(columnIndex));
    }
}