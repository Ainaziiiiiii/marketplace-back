package greenecomall;

import greenecomall.auth.domain.OtpChannel;
import greenecomall.auth.domain.OtpPurpose;
import greenecomall.auth.otp.OtpSender;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Дымовой тест всего приложения без Docker: встроенный Postgres, все миграции, проверка JPA-схемы,
 * сквозной путь «вход по SMS → профиль» и доставка события между модулями через шину.
 * Запуск: {@code ./mvnw test -Dtest=ApplicationBootTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"auth.outbox.poll-interval=200ms", "notification.outbox-poll-interval=200ms"})
@AutoConfigureTestRestTemplate
class ApplicationBootTest {

    static EmbeddedPostgres pg;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) throws Exception {
        pg = EmbeddedPostgres.builder().start();
        registry.add("spring.datasource.url", () -> pg.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
    }

    @Autowired
    TestRestTemplate rest;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    CapturingOtpSender sms;

    @Test
    void appStarts_smsLogin_andEventsReachOtherModules() throws Exception {
        assertThat(jdbc.queryForList("select schema_name from information_schema.schemata", String.class))
                .contains("auth", "catalog", "orders", "finance", "courier", "mlm", "notification");
        assertThat(rest.getForEntity("/actuator/health", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);

        String phone = "0555 12 34 56";
        assertThat(rest.postForEntity("/auth/otp/request", Map.of("phone", phone), String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        var login = rest.postForEntity("/auth/otp/verify",
                Map.of("phone", phone, "code", sms.codes.get("+996555123456")), Map.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login.getBody().get("created")).isEqualTo(true);

        HttpHeaders auth = new HttpHeaders();
        auth.setBearerAuth((String) login.getBody().get("accessToken"));
        var me = rest.exchange("/auth/me", HttpMethod.GET, new HttpEntity<>(auth), Map.class);
        assertThat(me.getBody().get("phone")).isEqualTo("+996555123456");

        // UserRegistered: outbox auth → шина → модуль уведомлений
        long deadline = System.currentTimeMillis() + 10_000;
        long processed = 0;
        while (processed == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(200);
            processed = jdbc.queryForObject("select count(*) from notification.processed_events", Long.class);
        }
        assertThat(processed).isPositive();
    }

    @TestConfiguration
    static class SmsCapture {
        /** Postgres гасится вместе с контекстом — после остановки планировщика, без ошибок соединения в логе. */
        @Bean
        AutoCloseable embeddedPostgresCloser() {
            return () -> pg.close();
        }

        @Bean
        @Primary
        CapturingOtpSender capturingOtpSender() {
            return new CapturingOtpSender();
        }
    }

    static class CapturingOtpSender implements OtpSender {
        final Map<String, String> codes = new ConcurrentHashMap<>();

        @Override
        public void send(String target, OtpChannel channel, OtpPurpose purpose, String code) {
            codes.put(target, code);
        }
    }
}
