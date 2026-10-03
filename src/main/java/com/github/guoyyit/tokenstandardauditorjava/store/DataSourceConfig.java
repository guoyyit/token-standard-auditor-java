package com.github.guoyyit.tokenstandardauditorjava.store;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Builds the SQLite data source, creating the data directory first.
 */
@Configuration
public class DataSourceConfig {

    @Bean
    public DataSource dataSource(Environment env) throws Exception {
        String dir = env.getProperty("auditor.data-dir");
        if (dir != null && !dir.isBlank()) {
            Files.createDirectories(Path.of(dir));
        }
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(env.getRequiredProperty("spring.datasource.url"));
        dataSource.setDriverClassName(env.getProperty("spring.datasource.driver-class-name",
                "org.sqlite.JDBC"));
        dataSource.setMaximumPoolSize(env.getProperty("spring.datasource.hikari.maximum-pool-size",
                Integer.class, 1));
        return dataSource;
    }
}
