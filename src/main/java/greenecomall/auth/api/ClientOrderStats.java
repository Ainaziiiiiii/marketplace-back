package greenecomall.auth.api;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Число заказов покупателей для таблицы «Покупатели» в админке. Реализует модуль заказов;
 * если реализации нет, колонка остаётся пустой.
 */
public interface ClientOrderStats {

    Map<UUID, Long> ordersCount(Collection<UUID> clientUserIds);
}
