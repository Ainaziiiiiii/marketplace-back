package greenecomall.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

/** Общие бины приложения. */
@Configuration
@EnableScheduling
public class AppConfig {

    /** Инжектируется в сервисы вместо статических Instant.now() — для тестируемости. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
