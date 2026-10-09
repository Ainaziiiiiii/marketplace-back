package greenecomall.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Настройки модуля auth (префикс {@code auth} в application.yml).
 */
@ConfigurationProperties(prefix = "auth")
public record AuthProperties(Jwt jwt, Refresh refresh, Otp otp, MlmSso mlmSso, Outbox outbox,
                             BootstrapAdmin bootstrapAdmin) {

    public record Jwt(String issuer, String audience, Duration accessTokenTtl) {
    }

    /**
     * Первый администратор, создаётся при старте, если такого email ещё нет (см. {@code BootstrapAdmin}).
     *
     * @param superAdmin выдать ещё и роль SUPER_ADMIN
     */
    public record BootstrapAdmin(String email, String password, boolean superAdmin) {
    }

    public record Refresh(Duration ttl) {
    }

    /** @param courierLength длина кода в приложении курьера (по умолчанию 4) */
    public record Otp(Duration ttl, Duration resendInterval, int length, Integer courierLength) {

        public Otp {
            if (courierLength == null || courierLength <= 0) {
                courierLength = 4;
            }
        }
    }

    /**
     * Параметры проверки входящего SSO-токена от внешней MLM-системы.
     *
     * @param algorithm HMAC-алгоритм подписи (HS256/HS384/HS512). Реальный MLM-бэк подписывает через
     *                  jjwt {@code Keys.hmacShaKeyFor(secret)}, который выбирает алгоритм по длине ключа —
     *                  значение должно совпасть с их секретом (см. docs/ARCHITECTURE.md §5)
     */
    public record MlmSso(String sharedSecret, String issuer, String audience, Duration clockSkew, String algorithm) {

        public MlmSso {
            if (algorithm == null || algorithm.isBlank()) {
                algorithm = "HS256";
            }
        }
    }

    public record Outbox(Duration pollInterval) {
    }
}
