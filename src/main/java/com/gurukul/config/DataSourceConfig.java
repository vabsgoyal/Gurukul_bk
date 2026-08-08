package com.gurukul.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;

/**
 * Picks the production DataSource from a single flag (app.database.provider, env DB_PROVIDER)
 * instead of a different Spring profile per database:
 *   - "supabase" (default) - Supabase's managed Postgres, plain username/password over TLS.
 *   - "aurora"              - AWS Aurora Postgres via the AWS Advanced JDBC Wrapper, IAM auth,
 *                              no password (mints short-lived tokens from the AWS credential chain).
 *
 * Both read the same SPRING_DATASOURCE_URL / SPRING_DATASOURCE_USERNAME env vars, so switching
 * providers is just changing DB_PROVIDER plus the matching connection details - no code change
 * or rebuild. Only active for the "prod" profile; "local"/"test" keep the H2 datasource Spring
 * Boot autoconfigures directly from application-local.properties.
 */
@Configuration
@Profile("prod")
public class DataSourceConfig {

    @Bean
    @ConditionalOnProperty(prefix = "app.database", name = "provider", havingValue = "aurora")
    public DataSource auroraDataSource(
            @Value("${SPRING_DATASOURCE_URL}") String jdbcUrl,
            @Value("${SPRING_DATASOURCE_USERNAME}") String username,
            @Value("${AWS_REGION:eu-north-1}") String iamRegion) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setDriverClassName("software.amazon.jdbc.Driver");
        config.addDataSourceProperty("wrapperPlugins", "iam");
        config.addDataSourceProperty("iamRegion", iamRegion);
        config.setMaxLifetime(1_260_000);
        config.setExceptionOverrideClassName("software.amazon.jdbc.util.HikariCPSQLException");
        return new HikariDataSource(config);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.database", name = "provider", havingValue = "supabase", matchIfMissing = true)
    public DataSource supabaseDataSource(
            @Value("${SPRING_DATASOURCE_URL}") String jdbcUrl,
            @Value("${SPRING_DATASOURCE_USERNAME}") String username,
            @Value("${SPRING_DATASOURCE_PASSWORD}") String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("org.postgresql.Driver");
        return new HikariDataSource(config);
    }
}
