package greenecomall.common.web.security;

import greenecomall.common.security.Roles;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;
import java.util.List;

/**
 * Единая модель безопасности приложения (docs/ARCHITECTURE.md §2.8): resource server, access-JWT
 * проверяется ключом модуля auth ({@code JwtDecoder} из {@code JwtConfig}) — подпись, {@code iss}, {@code aud}, срок.
 * Идентичность берётся только из проверенного токена ({@link AuthPrincipalArgumentResolver}).
 * Общие правила: preflight и actuator открыты, {@code /admin/**} — только ADMIN/SUPER_ADMIN;
 * правила модулей — бины {@link ServiceAuthorizationRules}; остальное — только с токеном.
 * Пути здесь — без {@code server.servlet.context-path} ({@code /api}).
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(GemSecurityProperties.class)
public class ServiceSecurityConfiguration implements WebMvcConfigurer {

    private static final String[] ACTUATOR_PUBLIC = {"/actuator/health/**", "/actuator/info", "/actuator/prometheus"};

    @Bean
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, ObjectProvider<JwtDecoder> decoders,
                                                      ObjectProvider<ServiceAuthorizationRules> serviceRules,
                                                      CorsConfigurationSource corsConfigurationSource)
            throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    // preflight браузера уходит без токена — иначе ни один фронт не достучится
                    auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
                    auth.requestMatchers(ACTUATOR_PUBLIC).permitAll();
                    auth.requestMatchers("/admin/**").hasAnyRole(Roles.ADMIN, Roles.SUPER_ADMIN);
                    serviceRules.orderedStream().forEach(rules -> rules.configure(auth));
                    auth.anyRequest().authenticated();
                })
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.decoder(decoders.getIfUnique()).jwtAuthenticationConverter(rolesConverter()))
                        .authenticationEntryPoint(SecurityProblems.unauthorized())
                        .accessDeniedHandler(SecurityProblems.forbidden()))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(SecurityProblems.unauthorized())
                        .accessDeniedHandler(SecurityProblems.forbidden()))
                .build();
    }

    /** CORS для браузерных фронтов на других доменах ({@code gem.security.cors-allowed-origins}). */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(GemSecurityProperties props) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(props.corsAllowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Location"));
        config.setMaxAge(Duration.ofHours(1));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /** Claim {@code roles} → authorities {@code ROLE_*}; principal name = {@code sub}. */
    static JwtAuthenticationConverter rolesConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix(Roles.AUTHORITY_PREFIX);
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new AuthPrincipalArgumentResolver());
    }
}
