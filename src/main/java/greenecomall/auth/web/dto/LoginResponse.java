package greenecomall.auth.web.dto;

import greenecomall.auth.token.IssuedTokens;

/** {@code created} — аккаунт создан этим входом (новый номер). */
public record LoginResponse(String accessToken, String refreshToken, long expiresIn, boolean created) {

    public static LoginResponse from(IssuedTokens tokens, boolean created) {
        return new LoginResponse(tokens.accessToken(), tokens.refreshToken(), tokens.expiresIn(), created);
    }
}
