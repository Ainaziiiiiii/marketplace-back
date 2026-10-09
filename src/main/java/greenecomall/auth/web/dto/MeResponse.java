package greenecomall.auth.web.dto;

import java.util.Set;
import java.util.UUID;

/**
 * @param displayName имя, как ввёл пользователь; {@code null} — ещё не указано
 * @param cityId      выбранный город (дублирует выбор на клиенте, чтобы он переживал смену устройства)
 */
public record MeResponse(
        UUID userId, String phone, String email, String displayName, String locale, UUID cityId,
        Set<String> roles, String clientType, String mlmUserId) {
}
