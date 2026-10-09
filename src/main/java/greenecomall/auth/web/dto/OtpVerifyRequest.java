package greenecomall.auth.web.dto;

import jakarta.validation.constraints.NotBlank;

/** {@code app} — как в {@link OtpRequest}. */
public record OtpVerifyRequest(@NotBlank String phone, @NotBlank String code, String app) {
}
