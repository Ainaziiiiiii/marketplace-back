package greenecomall.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Миграции по модулям: одна база, у каждого модуля своя схема и своя история Flyway
 * ({@code classpath:db/migration/<схема>}). Автоконфигурация Flyway выключена ({@code spring.flyway.enabled=false});
 * бины {@link FlywayMigrationInitializer} Spring Boot сам выполняет до создания JPA.
 */
@Configuration
public class ModuleMigrations {

    @Bean
    public FlywayMigrationInitializer authMigrations(DataSource dataSource) {
        return migrations(dataSource, "auth");
    }

    @Bean
    public FlywayMigrationInitializer catalogMigrations(DataSource dataSource) {
        return migrations(dataSource, "catalog");
    }

    @Bean
    public FlywayMigrationInitializer ordersMigrations(DataSource dataSource) {
        return migrations(dataSource, "orders");
    }

    @Bean
    public FlywayMigrationInitializer financeMigrations(DataSource dataSource) {
        return migrations(dataSource, "finance");
    }

    @Bean
    public FlywayMigrationInitializer courierMigrations(DataSource dataSource) {
        return migrations(dataSource, "courier");
    }

    @Bean
    public FlywayMigrationInitializer mlmMigrations(DataSource dataSource) {
        return migrations(dataSource, "mlm");
    }

    @Bean
    public FlywayMigrationInitializer notificationMigrations(DataSource dataSource) {
        return migrations(dataSource, "notification");
    }

    private static FlywayMigrationInitializer migrations(DataSource dataSource, String schema) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .createSchemas(true)
                .locations("classpath:db/migration/" + schema)
                .baselineOnMigrate(true)
                .load();
        return new FlywayMigrationInitializer(flyway);
    }
}
