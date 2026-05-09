package com.example.security.bootstrap.config;

import com.clickhouse.jdbc.ClickHouseDataSource;
import java.sql.SQLException;
import java.util.Properties;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "security.clickhouse.enabled", havingValue = "true", matchIfMissing = false)
public class ClickHouseConfig {

  @Bean(name = "clickHouseDataSource")
  DataSource clickHouseDataSource(
      @Value("${security.clickhouse.url:jdbc:clickhouse://localhost:8123/default}") String url,
      @Value("${security.clickhouse.user:default}") String user,
      @Value("${security.clickhouse.password:}") String password)
      throws SQLException {
    var props = new Properties();
    props.setProperty("user", user);
    props.setProperty("password", password);
    return new ClickHouseDataSource(url, props);
  }
}
