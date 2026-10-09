package greenecomall.auth.config;

import greenecomall.common.web.security.ServiceAuthorizationRules;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

/** Вход по SMS, вход сотрудника, обновление токена и SSO из MLM — без токена; {@code /auth/me} — с Bearer JWT. */
@Configuration
public class AuthSecurityRules {

    private static final String[] PUBLIC_POST_PATHS = {
            "/auth/otp/request", "/auth/otp/verify",
            "/auth/login", "/auth/token/refresh", "/auth/logout", "/auth/sso/mlm"
    };

    @Bean
    ServiceAuthorizationRules authAuthorizationRules() {
        return rules -> rules
                .requestMatchers(HttpMethod.GET, "/oauth2/jwks").permitAll()
                .requestMatchers(HttpMethod.POST, PUBLIC_POST_PATHS).permitAll();
    }
}
