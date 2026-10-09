package greenecomall.auth.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * @param phone номер в любом привычном виде («+996 555 12 34 56», «0555123456»)
 * @param app   {@code client} (по умолчанию), {@code shop} или {@code courier}
 */
public record OtpRequest(@NotBlank String phone, String app) {
}
