package greenecomall.courier.delivery;

import greenecomall.catalog.domain.Shop;
import greenecomall.catalog.shop.ShopService;
import greenecomall.common.domain.DomainException;
import greenecomall.courier.CourierErrors;
import greenecomall.order.order.SuborderDeliveryQuery;
import greenecomall.order.web.dto.SuborderDeliveryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import greenecomall.order.domain.DeliveryAddress;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Данные для приложения курьера: куда везти (модуль заказов) и откуда забрать (каталог).
 * Отказ соседнего модуля не должен прятать саму доставку, поэтому адрес забора необязателен —
 * вернём, что есть. Чтения — в отдельной read-only транзакции.
 */
@Component
public class DeliveryDetailsClient {

    private static final Logger log = LoggerFactory.getLogger(DeliveryDetailsClient.class);

    private final SuborderDeliveryQuery orders;
    private final ShopService shops;
    private final TransactionTemplate tx;

    public DeliveryDetailsClient(SuborderDeliveryQuery orders, ShopService shops,
                                 PlatformTransactionManager transactionManager) {
        this.orders = orders;
        this.shops = shops;
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.tx.setReadOnly(true);
    }

    /** Адрес клиента, состав и сумма посылки. */
    public Dropoff dropoff(UUID suborderId) {
        try {
            SuborderDeliveryResponse d = tx.execute(status -> orders.delivery(suborderId));
            List<Dropoff.Item> items = d.items().stream().map(i -> new Dropoff.Item(i.name(), i.qty())).toList();
            return new Dropoff(d.deliveryAddress(), d.amountMinor(),
                    d.currency() != null ? d.currency() : "KGS", d.orderStatus(), items);
        } catch (RuntimeException e) {
            throw new DomainException(CourierErrors.ORDER_UNAVAILABLE, "cannot load delivery details", e);
        }
    }

    /** Магазин, откуда забирать: название, адрес, телефон. Пусто, если магазин не найден. */
    public Optional<Pickup> pickup(UUID shopId) {
        try {
            return Optional.ofNullable(tx.execute(status -> {
                Shop shop = shops.get(shopId);
                return new Pickup(shop.getName(), shop.getAddress(), shop.getPhone());
            }));
        } catch (RuntimeException e) {
            log.warn("cannot load pickup address for shop {}: {}", shopId, e.toString());
            return Optional.empty();
        }
    }

    public record Dropoff(DeliveryAddress address, long amountMinor, String currency, String orderStatus, List<Item> items) {
        public record Item(String name, int qty) {
        }
    }

    public record Pickup(String shopName, String address, String phone) {
    }
}
