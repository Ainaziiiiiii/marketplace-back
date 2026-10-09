package greenecomall.auth.account;

import greenecomall.auth.AuthErrors;
import greenecomall.auth.domain.User;
import greenecomall.auth.repo.RefreshTokenRepository;
import greenecomall.auth.repo.UserRepository;
import greenecomall.auth.support.Contacts;
import greenecomall.common.domain.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Профиль пользователя (имя, контактный email, язык, город) и блокировка админом. */
@Service
public class ProfileService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;

    public ProfileService(UserRepository users, RefreshTokenRepository refreshTokens) {
        this.users = users;
        this.refreshTokens = refreshTokens;
    }

    @Transactional(readOnly = true)
    public User get(UUID userId) {
        return users.findById(userId)
                .orElseThrow(() -> new DomainException(AuthErrors.USER_NOT_FOUND, "user not found: " + userId));
    }

    /** Email — только контакт для чеков и писем, не логин. Пустая строка очищает поле. */
    @Transactional
    public User update(UUID userId, String displayName, String email, String locale, UUID cityId) {
        User user = get(userId);
        String normalizedEmail = email == null ? null : (email.isBlank() ? "" : Contacts.normalize(email));
        if (normalizedEmail != null && !normalizedEmail.isEmpty()
                && !normalizedEmail.equals(user.getEmail()) && users.existsByEmail(normalizedEmail)) {
            throw new DomainException(AuthErrors.CONTACT_TAKEN, "email already used by another account");
        }
        user.updateProfile(displayName, normalizedEmail, locale, cityId);
        return user;
    }

    /** Блокировка: вход запрещён, активные refresh-токены гасятся (access-токен доживает свои 15 минут). */
    @Transactional
    public User block(UUID userId) {
        User user = get(userId);
        user.block();
        refreshTokens.revokeAllActiveForUser(userId);
        return user;
    }

    @Transactional
    public User unblock(UUID userId) {
        User user = get(userId);
        user.unblock();
        return user;
    }
}
