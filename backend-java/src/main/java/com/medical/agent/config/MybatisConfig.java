package com.medical.agent.config;

import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

@Configuration
public class MybatisConfig {
    @Bean
    public ConfigurationCustomizer configurationCustomizer() {
        return configuration -> {
            configuration.getTypeHandlerRegistry().register(UUID.class, new BaseTypeHandler<UUID>() {
                @Override
                public void setNonNullParameter(PreparedStatement ps, int i, UUID parameter, JdbcType jdbcType) throws SQLException {
                    ps.setObject(i, parameter);
                }

                @Override
                public UUID getNullableResult(ResultSet rs, String columnName) throws SQLException {
                    Object val = rs.getObject(columnName);
                    return val == null ? null : (val instanceof UUID ? (UUID) val : UUID.fromString(val.toString()));
                }

                @Override
                public UUID getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
                    Object val = rs.getObject(columnIndex);
                    return val == null ? null : (val instanceof UUID ? (UUID) val : UUID.fromString(val.toString()));
                }

                @Override
                public UUID getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
                    Object val = cs.getObject(columnIndex);
                    return val == null ? null : (val instanceof UUID ? (UUID) val : UUID.fromString(val.toString()));
                }
            });
        };
    }
}
