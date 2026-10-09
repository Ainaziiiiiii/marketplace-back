package greenecomall.common.security;

/**
 * Коды ролей RBAC. Хранятся в модуле auth, попадают в claim {@code roles} access-токена.
 */
public final class Roles {

    public static final String GUEST = "GUEST";
    public static final String CLIENT_EXTERNAL = "CLIENT_EXTERNAL";
    public static final String CLIENT_MLM = "CLIENT_MLM";
    public static final String SHOP = "SHOP";
    public static final String COURIER = "COURIER";
    public static final String ADMIN = "ADMIN";
    public static final String SUPER_ADMIN = "SUPER_ADMIN";

    public static final String AUTHORITY_PREFIX = "ROLE_";

    private Roles() {
    }
}
