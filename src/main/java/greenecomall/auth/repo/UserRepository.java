package greenecomall.auth.repo;

import greenecomall.auth.domain.User;
import greenecomall.auth.domain.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    Optional<User> findByPhone(String phone);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    /** Вход сотрудника по логину, который может быть либо email, либо телефоном. */
    default Optional<User> findByLogin(String login) {
        return findByEmail(login).or(() -> findByPhone(login));
    }

    /**
     * Список для админки. {@code q} — подстрока имени, телефона или email (в нижнем регистре, с {@code %}).
     * {@code role} — код роли; {@code null} в любом фильтре — без ограничения.
     */
    @Query("""
            select distinct u from User u left join u.roles r
            where (:role is null or r.code = :role)
              and (:status is null or u.status = :status)
              and (:q is null or lower(coalesce(u.displayName, '')) like :q
                   or coalesce(u.phone, '') like :q or lower(coalesce(u.email, '')) like :q)
            order by u.createdAt desc
            """)
    Page<User> adminSearch(@Param("role") String role, @Param("status") UserStatus status,
                           @Param("q") String q, Pageable pageable);
}
