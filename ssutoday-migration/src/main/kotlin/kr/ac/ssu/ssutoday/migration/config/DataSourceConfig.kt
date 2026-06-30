package kr.ac.ssu.ssutoday.migration.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import javax.sql.DataSource

@Configuration
class DataSourceConfig {
    @Bean(DB.OLD_DATASOURCE)
    fun oldDataSource(
        @Value("\${old-db.url}") url: String,
        @Value("\${old-db.username}") username: String,
        @Value("\${old-db.password}") password: String,
    ): DataSource =
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = url
                this.username = username
                this.password = password
                maximumPoolSize = 5
                isReadOnly = true
            },
        )

    @Bean(DB.NEW_DATASOURCE)
    @Primary
    fun newDataSource(
        @Value("\${new-db.url}") url: String,
        @Value("\${new-db.username}") username: String,
        @Value("\${new-db.password}") password: String,
    ): DataSource =
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = url
                this.username = username
                this.password = password
                maximumPoolSize = 5
            },
        )

    @Bean(DB.OLD_JDBC)
    fun oldJdbcTemplate(
        @Qualifier(DB.OLD_DATASOURCE) dataSource: DataSource,
    ): JdbcTemplate = JdbcTemplate(dataSource)

    @Bean(DB.NEW_JDBC)
    fun newJdbcTemplate(
        @Qualifier(DB.NEW_DATASOURCE) dataSource: DataSource,
    ): JdbcTemplate = JdbcTemplate(dataSource)
}
