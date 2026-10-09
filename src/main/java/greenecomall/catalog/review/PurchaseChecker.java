package greenecomall.catalog.review;

import greenecomall.catalog.CatalogErrors;
import greenecomall.common.domain.DomainException;
import greenecomall.order.order.OrderService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

/** Спрашивает модуль заказов, доставлен ли товар этому покупателю. */
@Component
public class PurchaseChecker {

    private final OrderService orders;
    private final TransactionTemplate tx;

    public PurchaseChecker(OrderService orders, PlatformTransactionManager transactionManager) {
        this.orders = orders;
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.tx.setReadOnly(true);
    }

    public boolean hasReceived(UUID clientUserId, UUID productId) {
        try {
            return Boolean.TRUE.equals(tx.execute(status -> orders.hasReceived(clientUserId, productId)));
        } catch (RuntimeException e) {
            throw new DomainException(CatalogErrors.ORDER_UNAVAILABLE, "purchase check failed", e);
        }
    }
}
