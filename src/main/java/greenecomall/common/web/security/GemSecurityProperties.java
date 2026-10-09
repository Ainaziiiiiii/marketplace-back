package greenecomall.common.web.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Общие настройки безопасности приложения (префикс {@code gem.security}).
 *
 * @param corsAllowedOrigins домены браузерных фронтов (покупатель, магазин, курьер, админка); {@code *} — любой.
 *                           Токен ходит в заголовке {@code Authorization}, а не в cookie, поэтому учётные данные не шлются.
 */
@ConfigurationProperties(prefix = "gem.security")
public record GemSecurityProperties(List<String> corsAllowedOrigins) {

    public GemSecurityProperties {
        if (corsAllowedOrigins == null || corsAllowedOrigins.isEmpty()) {
            corsAllowedOrigins = List.of("*");
        }
    }
}
