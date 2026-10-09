package greenecomall.auth.account;

import greenecomall.auth.AuthErrors;
import greenecomall.auth.domain.User;
import greenecomall.auth.domain.UserStatus;
import greenecomall.auth.repo.UserRepository;
import greenecomall.auth.support.Contacts;
import greenecomall.auth.token.IssuedTokens;
import greenecomall.auth.token.TokenService;
import greenecomall.common.domain.DomainException;
import greenecomall.common.security.Roles;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Вход по паролю — только для сотрудников (админка). Покупатели, магазины и курьеры входят по SMS
 * ({@link PhoneLoginService}). Сообщения об ошибках намеренно неинформативны.
 */
@Service
public class AuthenticationService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public AuthenticationService(UserRepository users, PasswordEncoder passwordEncoder, TokenService tokenService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    @Transactional
    public IssuedTokens loginWithPassword(String login, String rawPassword) {
        User user = users.findByLogin(Contacts.normalize(login))
                .orElseThrow(() -> new DomainException(AuthErrors.CREDENTIALS_INVALID, "bad credentials"));

        if (user.getStatus() == UserStatus.BLOCKED) {
            throw new DomainException(AuthErrors.USER_BLOCKED, "user is blocked");
        }
        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new DomainException(AuthErrors.CREDENTIALS_INVALID, "bad credentials");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new DomainException(AuthErrors.USER_NOT_ACTIVE, "user is not active");
        }
        if (!user.hasRole(Roles.ADMIN) && !user.hasRole(Roles.SUPER_ADMIN)) {
            throw new DomainException(AuthErrors.STAFF_ONLY, "password login is for staff; use SMS code");
        }
        return tokenService.issueFor(user);
    }
}
