package greenecomall.auth.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Частичное обновление профиля: {@code null} — поле не меняется, пустая строка — очистить имя/email. */
public record UpdateMeRequest(
        @Size(max = 120) String displayName,
        @Email @Size(max = 320) String email,
        @Pattern(regexp = "ru|ky") String locale,
        UUID cityId) {
}
