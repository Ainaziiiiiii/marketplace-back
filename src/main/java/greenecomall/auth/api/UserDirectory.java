package greenecomall.auth.api;

import greenecomall.auth.domain.Role;
import greenecomall.auth.domain.User;
import greenecomall.auth.repo.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Чтение пользователей другими модулями: имя и телефон для таблиц админки, кабинета магазина,
 * курьера и отзывов. Другие модули не трогают таблицы auth напрямую — только через этот класс.
 */
@Component
public class UserDirectory {

    private final UserRepository users;

    public UserDirectory(UserRepository users) {
        this.users = users;
    }

    /**
     * @param shortName «Айзада К.» — имя и инициал фамилии; {@code null}, если имя не указано
     */
    public record UserView(UUID id, String displayName, String shortName, String phone, String email,
                           String status, Set<String> roles, UUID cityId, Instant createdAt) {
    }

    @Transactional(readOnly = true)
    public Optional<UserView> find(UUID userId) {
        return userId == null ? Optional.empty() : users.findById(userId).map(UserDirectory::view);
    }

    @Transactional(readOnly = true)
    public Map<UUID, UserView> findAll(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return users.findAllById(Set.copyOf(userIds)).stream()
                .map(UserDirectory::view)
                .collect(Collectors.toMap(UserView::id, Function.identity()));
    }

    public static UserView view(User u) {
        return new UserView(u.getId(), u.getDisplayName(), shortName(u.getDisplayName()), u.getPhone(),
                u.getEmail(), u.getStatus().name(),
                u.getRoles().stream().map(Role::getCode).collect(Collectors.toUnmodifiableSet()),
                u.getCityId(), u.getCreatedAt());
    }

    /** «Айзада Касымова» → «Айзада К.»; одно слово — как есть. */
    public static String shortName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return null;
        }
        String[] parts = displayName.trim().split("\\s+");
        return parts.length == 1 ? parts[0] : parts[0] + " " + parts[1].charAt(0) + ".";
    }
}
