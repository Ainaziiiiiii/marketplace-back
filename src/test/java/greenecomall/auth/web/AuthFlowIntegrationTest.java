package greenecomall.auth.web;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import greenecomall.auth.domain.OtpChannel;
import greenecomall.auth.domain.OtpPurpose;
import greenecomall.auth.otp.OtpSender;
import greenecomall.auth.repo.OutboxRepository;
import greenecomall.auth.web.dto.LoginRequest;
import greenecomall.auth.web.dto.LoginResponse;
import greenecomall.auth.web.dto.OtpChallengeResponse;
import greenecomall.auth.web.dto.OtpRequest;
import greenecomall.auth.web.dto.UpdateMeRequest;
import greenecomall.auth.web.dto.MeResponse;
import greenecomall.auth.web.dto.OtpVerifyRequest;
import greenecomall.auth.web.dto.RefreshRequest;
import greenecomall.auth.web.dto.SsoRequest;
import greenecomall.auth.web.dto.SsoTokenResponse;
import greenecomall.auth.web.dto.TokenResponse;
import greenecomall.common.events.EventTypes;
import greenecomall.common.security.Roles;
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
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Полный путь через реальные HTTP-контроллеры против настоящего Postgres (Testcontainers).
 * {@code OutboxRelay} отключён большим poll-interval, проверяем факт записи
 * события в outbox — этого достаточно (см. docs/TASK-01-auth-service.md §7).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class AuthFlowIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("auth.outbox.poll-interval", () -> "1h");
    }

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private CapturingOtpSender otpSender;
    @Autowired
    private OutboxRepository outbox;

    @Test
    void smsLogin_createsAccount_profile_refresh_logout() {
        String phone = "+996 555 " + (100000 + new java.util.Random().nextInt(899999));

        ResponseEntity<OtpChallengeResponse> challenge = rest.postForEntity(
                "/auth/otp/request", new OtpRequest(phone, null), OtpChallengeResponse.class);
        assertThat(challenge.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(challenge.getBody().codeLength()).isEqualTo(6);
        String normalized = challenge.getBody().phone();
        assertThat(normalized).startsWith("+996555").hasSize(13);

        String otpCode = otpSender.lastCodeFor(normalized);
        ResponseEntity<LoginResponse> confirmed = rest.postForEntity(
                "/auth/otp/verify", new OtpVerifyRequest(phone, otpCode, null), LoginResponse.class);
        assertThat(confirmed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(confirmed.getBody().created()).isTrue();
        String firstRefreshToken = confirmed.getBody().refreshToken();

        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setBearerAuth(confirmed.getBody().accessToken());
        ResponseEntity<MeResponse> me = rest.exchange("/auth/me", HttpMethod.PATCH,
                new HttpEntity<>(new UpdateMeRequest("Айзада Касымова", null, "ky", null), authHeaders), MeResponse.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody().phone()).isEqualTo(normalized);
        assertThat(me.getBody().displayName()).isEqualTo("Айзада Касымова");
        assertThat(me.getBody().roles()).contains(Roles.CLIENT_EXTERNAL);

        assertThat(rest.postForEntity("/auth/login", new LoginRequest(normalized, "x"), String.class).getStatusCode())
                .as("у покупателя нет пароля").isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(rest.postForEntity("/auth/otp/request", new OtpRequest(normalized, "courier"), String.class)
                .getStatusCode()).as("курьерское приложение — только курьерам").isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<TokenResponse> refreshed = rest.postForEntity(
                "/auth/token/refresh", new RefreshRequest(firstRefreshToken), TokenResponse.class);
        assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(refreshed.getBody().refreshToken()).isNotEqualTo(firstRefreshToken);

        // тот же (уже отозванный ротацией) refresh предъявлен повторно — трактуется как компрометация
        ResponseEntity<String> reused = rest.postForEntity(
                "/auth/token/refresh", new RefreshRequest(firstRefreshToken), String.class);
        assertThat(reused.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<Void> logout = rest.postForEntity(
                "/auth/logout", new RefreshRequest(refreshed.getBody().refreshToken()), Void.class);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(outbox.findAll()).anyMatch(m -> m.getEventType().equals(EventTypes.USER_REGISTERED));
    }

    @Test
    void mlmSso_createsUserOnFirstLogin_andRejectsTokenReplay() throws Exception {
        String token = buildMlmSsoToken("U-" + UUID.randomUUID());

        ResponseEntity<SsoTokenResponse> first = rest.postForEntity(
                "/auth/sso/mlm", new SsoRequest(token), SsoTokenResponse.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody().created()).isTrue();

        ResponseEntity<String> replay = rest.postForEntity(
                "/auth/sso/mlm", new SsoRequest(token), String.class);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        assertThat(outbox.findAll()).anyMatch(m -> m.getEventType().equals(EventTypes.MLM_USER_LINKED));
    }

    /** Совпадает с {@code infra/mock-mlm/app.py} / дефолтом {@code MLM_SSO_SHARED_SECRET}. */
    private String buildMlmSsoToken(String mlmUserId) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("mock-mlm")
                .audience("green-eco-mall")
                .subject(mlmUserId)
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(60)))
                .claim("mlm_user_id", mlmUserId)
                .claim("access_status", "access_paid")
                .claim("locale", "ru")
                .build();
        JWSSigner signer = new MACSigner("local-dev-mlm-shared-secret-change-me".getBytes(StandardCharsets.UTF_8));
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(signer);
        return jwt.serialize();
    }

    @TestConfiguration
    static class TestOtpConfig {
        @Bean
        @Primary
        CapturingOtpSender capturingOtpSender() {
            return new CapturingOtpSender();
        }
    }

    /** Заменяет {@code LoggingOtpSender} в тесте — код нужен для прохождения /auth/otp/verify. */
    static class CapturingOtpSender implements OtpSender {
        private final Map<String, String> lastCodeByTarget = new ConcurrentHashMap<>();

        @Override
        public void send(String target, OtpChannel channel, OtpPurpose purpose, String code) {
            lastCodeByTarget.put(target, code);
        }

        String lastCodeFor(String target) {
            return lastCodeByTarget.get(target);
        }
    }
}
