package greenecomall.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Репозитории всех модулей; имена бинов — полные имена классов: в каждом модуле есть
 * свой {@code OutboxRepository}/{@code ProcessedEventRepository}.
 */
@Configuration
@EnableJpaRepositories(basePackages = "greenecomall", nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
public class PersistenceConfig {
}
