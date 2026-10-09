package greenecomall.auth.account;

import greenecomall.auth.AuthErrors;
import greenecomall.auth.config.AuthProperties;
import greenecomall.auth.domain.LoginApp;
import greenecomall.auth.domain.OtpChannel;
import greenecomall.auth.domain.OtpPurpose;
import greenecomall.auth.domain.Role;
import greenecomall.auth.domain.User;
import greenecomall.auth.domain.UserStatus;
import greenecomall.auth.otp.OtpService;
import greenecomall.auth.repo.RoleRepository;
import greenecomall.auth.repo.UserRepository;
import greenecomall.auth.support.Phones;
import greenecomall.auth.support.Tracing;
import greenecomall.auth.token.IssuedTokens;
import greenecomall.auth.token.TokenService;
import greenecomall.common.domain.DomainException;
import greenecomall.common.events.DomainEventPublisher;
import greenecomall.common.events.EventEnvelope;
import greenecomall.common.events.EventTypes;
import greenecomall.common.events.Topics;
import greenecomall.common.events.payload.AuthEvents;
import greenecomall.common.security.Roles;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Вход по SMS-коду — единственный способ входа для покупателя, магазина и курьера (пароля и email нет).
 * Новый номер в приложении покупателя/магазина создаёт аккаунт автоматически. Курьеров заводит админ:
 * чужому номеру SMS даже не отправляется.
 */
@Service
public class PhoneLoginService {

    private static final String PRODUCER = "auth-service";

    private final UserRepository users;
    private final RoleRepository roles;
    private final OtpService otpService;
    private final TokenService tokenService;
    private final AuthProperties props;
    private final DomainEventPublisher events;

    public PhoneLoginService(UserRepository users, RoleRepository roles, OtpService otpService,
                             TokenService tokenService, AuthProperties props,
                             @Qualifier("authEvents") DomainEventPublisher events) {
        this.users = users;
        this.roles = roles;
        this.otpService = otpService;
        this.tokenService = tokenService;
        this.props = props;
        this.events = events;
    }

    /** Параметры выданного кода — для таймера «Отправить ещё раз через 0:42». */
    public record Challenge(String phone, int codeLength, long resendAfterSec, long expiresInSec) {
    }

    /** Результат входа: {@code created} — аккаунт заведён этим входом (покупателю можно предложить ввести имя). */
    public record Login(IssuedTokens tokens, boolean created) {
    }

    @Transactional
    public Challenge requestCode(String rawPhone, LoginApp app) {
        String phone = Phones.normalize(rawPhone);
        Optional<User> existing = users.findByPhone(phone);
        existing.ifPresent(PhoneLoginService::requireNotBlocked);
        if (app == LoginApp.COURIER) {
            requireCourier(existing);
        }
        int length = codeLength(app);
        otpService.issue(phone, OtpChannel.SMS, OtpPurpose.LOGIN, length);
        return new Challenge(phone, length, props.otp().resendInterval().toSeconds(), props.otp().ttl().toSeconds());
    }

    /** noRollbackFor: счётчик неудачных попыток кода должен пережить ошибку (см. {@code OtpService.verify}). */
    @Transactional(noRollbackFor = DomainException.class)
    public Login verify(String rawPhone, String code, LoginApp app) {
        String phone = Phones.normalize(rawPhone);
        otpService.verify(phone, OtpPurpose.LOGIN, code);

        Optional<User> existing = users.findByPhone(phone);
        if (app == LoginApp.COURIER) {
            requireCourier(existing);
        }
        if (existing.isPresent()) {
            User user = existing.get();
            requireNotBlocked(user);
            if (user.getStatus() == UserStatus.PENDING) {
                user.activate();
            }
            return new Login(tokenService.issueFor(user), false);
        }

        User user = User.forPhoneLogin(phone);
        user.addRole(requireRole(Roles.CLIENT_EXTERNAL));
        users.save(user);
        events.publish(Topics.AUTH, user.getId().toString(),
                EventEnvelope.of(EventTypes.USER_REGISTERED, PRODUCER, Tracing.currentTraceId(),
                        new AuthEvents.UserRegistered(user.getId(), null, phone, user.getLocale())));
        return new Login(tokenService.issueFor(user), true);
    }

    private int codeLength(LoginApp app) {
        return app == LoginApp.COURIER ? props.otp().courierLength() : props.otp().length();
    }

    private static void requireCourier(Optional<User> user) {
        if (user.isEmpty() || !user.get().hasRole(Roles.COURIER)) {
            throw new DomainException(AuthErrors.COURIER_NO_ACCESS, "phone is not registered as a courier");
        }
    }

    private static void requireNotBlocked(User user) {
        if (user.getStatus() == UserStatus.BLOCKED) {
            throw new DomainException(AuthErrors.USER_BLOCKED, "user is blocked");
        }
    }

    private Role requireRole(String code) {
        return roles.findByCode(code).orElseThrow(() -> new IllegalStateException("role not seeded: " + code));
    }
}
