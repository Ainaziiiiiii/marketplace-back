package greenecomall.auth.account;

import greenecomall.auth.AuthErrors;
import greenecomall.auth.domain.Role;
import greenecomall.auth.domain.User;
import greenecomall.auth.domain.UserStatus;
import greenecomall.auth.repo.RoleRepository;
import greenecomall.auth.repo.UserRepository;
import greenecomall.auth.support.Contacts;
import greenecomall.auth.support.Phones;
import greenecomall.common.domain.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Служебные операции для других модулей (catalog при регистрации магазина, courier при регистрации
 * курьера): завести аккаунт без пароля/OTP и выдавать ему роли. Вызывается напрямую из кода модулей,
 * наружу по HTTP не опубликован.
 */
@Service
public class InternalUserService {

    private final UserRepository users;
    private final RoleRepository roles;

    public InternalUserService(UserRepository users, RoleRepository roles) {
        this.users = users;
        this.roles = roles;
    }

    @Transactional
    public UUID createUser(String email, String phone, String roleCode, UserStatus status) {
        String normalizedEmail = Contacts.normalize(email);
        String normalizedPhone = phone == null ? null : Phones.normalize(phone);
        if (normalizedEmail == null && normalizedPhone == null) {
            throw new DomainException(AuthErrors.CREDENTIALS_INVALID, "email or phone is required");
        }
        if (normalizedEmail != null && users.existsByEmail(normalizedEmail)) {
            throw new DomainException(AuthErrors.CONTACT_TAKEN, "email already registered");
        }
        if (normalizedPhone != null && users.existsByPhone(normalizedPhone)) {
            throw new DomainException(AuthErrors.CONTACT_TAKEN, "phone already registered");
        }

        User user = User.forInternalProvisioning(normalizedEmail, normalizedPhone, status);
        user.addRole(requireRole(roleCode));
        return users.save(user).getId();
    }

    /**
     * Админ заводит сотрудника по номеру (например, курьера): находит аккаунт по телефону или создаёт
     * активный, проставляет имя, если его ещё нет, и выдаёт роль. Дальше человек входит по SMS.
     */
    @Transactional
    public UUID ensureUserWithRole(String phone, String displayName, String roleCode) {
        String normalizedPhone = Phones.normalize(phone);
        User user = users.findByPhone(normalizedPhone).orElseGet(() -> users.save(User.forPhoneLogin(normalizedPhone)));
        if (user.getDisplayName() == null && displayName != null && !displayName.isBlank()) {
            user.updateProfile(displayName, null, null, null);
        }
        user.addRole(requireRole(roleCode));
        return user.getId();
    }

    @Transactional
    public void removeRole(UUID userId, String roleCode) {
        findUser(userId).removeRole(requireRole(roleCode));
    }

    @Transactional
    public void addRole(UUID userId, String roleCode) {
        User user = findUser(userId);
        user.addRole(requireRole(roleCode));
    }

    @Transactional(readOnly = true)
    public User findUser(UUID userId) {
        return users.findById(userId)
                .orElseThrow(() -> new DomainException(AuthErrors.CREDENTIALS_INVALID, "user not found"));
    }

    private Role requireRole(String code) {
        return roles.findByCode(code)
                .orElseThrow(() -> new IllegalStateException("role not seeded: " + code));
    }
}
