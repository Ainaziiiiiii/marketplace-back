package greenecomall.auth.web;

import greenecomall.auth.AuthErrors;
import greenecomall.auth.account.AuthenticationService;
import greenecomall.auth.account.PhoneLoginService;
import greenecomall.auth.account.ProfileService;
import greenecomall.auth.domain.LoginApp;
import greenecomall.auth.domain.Role;
import greenecomall.auth.domain.User;
import greenecomall.auth.token.TokenService;
import greenecomall.auth.web.dto.LoginRequest;
import greenecomall.auth.web.dto.LoginResponse;
import greenecomall.auth.web.dto.MeResponse;
import greenecomall.auth.web.dto.OtpChallengeResponse;
import greenecomall.auth.web.dto.OtpRequest;
import greenecomall.auth.web.dto.OtpVerifyRequest;
import greenecomall.auth.web.dto.RefreshRequest;
import greenecomall.auth.web.dto.TokenResponse;
import greenecomall.auth.web.dto.UpdateMeRequest;
import greenecomall.common.domain.DomainException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Вход и профиль. Покупатель, магазин и курьер — только по SMS-коду ({@code /auth/otp/*}),
 * пароль ({@code /auth/login}) — только для сотрудников админки.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final PhoneLoginService phoneLogin;
    private final AuthenticationService authenticationService;
    private final TokenService tokenService;
    private final ProfileService profiles;

    public AuthController(PhoneLoginService phoneLogin, AuthenticationService authenticationService,
                          TokenService tokenService, ProfileService profiles) {
        this.phoneLogin = phoneLogin;
        this.authenticationService = authenticationService;
        this.tokenService = tokenService;
        this.profiles = profiles;
    }

    @PostMapping("/otp/request")
    public OtpChallengeResponse requestOtp(@Valid @RequestBody OtpRequest request) {
        PhoneLoginService.Challenge c = phoneLogin.requestCode(request.phone(), parseApp(request.app()));
        return new OtpChallengeResponse(c.phone(), c.codeLength(), c.resendAfterSec(), c.expiresInSec());
    }

    @PostMapping("/otp/verify")
    public LoginResponse verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
        PhoneLoginService.Login login = phoneLogin.verify(request.phone(), request.code(), parseApp(request.app()));
        return LoginResponse.from(login.tokens(), login.created());
    }

    /** Вход сотрудника админки по email/телефону и паролю. */
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return TokenResponse.from(authenticationService.loginWithPassword(request.login(), request.password()));
    }

    @PostMapping("/token/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return TokenResponse.from(tokenService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest request) {
        tokenService.revoke(request.refreshToken());
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        return toMe(profiles.get(UUID.fromString(jwt.getSubject())), jwt);
    }

    @PatchMapping("/me")
    public MeResponse updateMe(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateMeRequest request) {
        User user = profiles.update(UUID.fromString(jwt.getSubject()),
                request.displayName(), request.email(), request.locale(), request.cityId());
        return toMe(user, jwt);
    }

    private static MeResponse toMe(User user, Jwt jwt) {
        Set<String> roles = user.getRoles().stream().map(Role::getCode).collect(Collectors.toUnmodifiableSet());
        return new MeResponse(user.getId(), user.getPhone(), user.getEmail(), user.getDisplayName(),
                user.getLocale(), user.getCityId(), roles, user.getClientType().name().toLowerCase(),
                jwt.getClaimAsString("mlm_user_id"));
    }

    private static LoginApp parseApp(String raw) {
        if (raw == null || raw.isBlank()) {
            return LoginApp.CLIENT;
        }
        try {
            return LoginApp.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new DomainException(AuthErrors.OTP_PURPOSE_UNSUPPORTED, "unknown app: " + raw);
        }
    }
}
