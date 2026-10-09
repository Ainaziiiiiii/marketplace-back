package greenecomall.auth.web;

import greenecomall.auth.account.ProfileService;
import greenecomall.auth.api.ClientOrderStats;
import greenecomall.auth.api.UserDirectory;
import greenecomall.auth.domain.ClientType;
import greenecomall.auth.domain.User;
import greenecomall.auth.domain.UserStatus;
import greenecomall.auth.repo.UserRepository;
import greenecomall.common.security.Roles;
import greenecomall.common.web.PageResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Админка «Покупатели»: список с поиском, карточка, блокировка. Доступ — {@code /admin/**} (ADMIN). */
@RestController
@RequestMapping("/admin/users")
public class AdminUserController {

    private final UserRepository users;
    private final ProfileService profiles;
    private final ObjectProvider<ClientOrderStats> orderStats;

    public AdminUserController(UserRepository users, ProfileService profiles,
                               ObjectProvider<ClientOrderStats> orderStats) {
        this.users = users;
        this.profiles = profiles;
        this.orderStats = orderStats;
    }

    /**
     * @param ordersCount {@code null}, если модуль заказов не подключён
     * @param mlm         участник MLM (вошёл из MLM-системы)
     */
    public record AdminUserResponse(UUID id, String displayName, String phone, String email, UUID cityId,
                                    String status, Set<String> roles, boolean mlm, Long ordersCount,
                                    Instant createdAt) {
    }

    /**
     * @param role   по умолчанию покупатели ({@code CLIENT_EXTERNAL}); {@code all} — все
     * @param q      имя, телефон или email
     * @param status {@code ACTIVE} / {@code BLOCKED}
     */
    @GetMapping
    public PageResponse<AdminUserResponse> list(@RequestParam(defaultValue = Roles.CLIENT_EXTERNAL) String role,
                                                @RequestParam(required = false) String q,
                                                @RequestParam(required = false) UserStatus status,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        String roleFilter = "all".equalsIgnoreCase(role) ? null : role.toUpperCase();
        String like = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase().replace("%", "") + "%";
        Page<User> result = users.adminSearch(roleFilter, status, like, PageRequest.of(page, Math.min(size, 100)));
        Map<UUID, Long> counts = counts(result.getContent().stream().map(User::getId).toList());
        return PageResponse.of(result.getContent().stream().map(u -> toResponse(u, counts)).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @GetMapping("/{id}")
    public AdminUserResponse get(@PathVariable UUID id) {
        User user = profiles.get(id);
        return toResponse(user, counts(List.of(id)));
    }

    @PostMapping("/{id}/block")
    public AdminUserResponse block(@PathVariable UUID id) {
        return toResponse(profiles.block(id), counts(List.of(id)));
    }

    @PostMapping("/{id}/unblock")
    public AdminUserResponse unblock(@PathVariable UUID id) {
        return toResponse(profiles.unblock(id), counts(List.of(id)));
    }

    private Map<UUID, Long> counts(List<UUID> ids) {
        ClientOrderStats stats = orderStats.getIfAvailable();
        return stats == null ? null : stats.ordersCount(ids);
    }

    private static AdminUserResponse toResponse(User u, Map<UUID, Long> counts) {
        UserDirectory.UserView v = UserDirectory.view(u);
        return new AdminUserResponse(v.id(), v.displayName(), v.phone(), v.email(), v.cityId(), v.status(),
                v.roles(), u.getClientType() == ClientType.INTERNAL_MLM,
                counts == null ? null : counts.getOrDefault(u.getId(), 0L), v.createdAt());
    }
}
