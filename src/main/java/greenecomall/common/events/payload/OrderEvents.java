package greenecomall.common.events.payload;

import java.util.List;
import java.util.UUID;

/**
 * Payload-контракты топика {@code orders}.
 */
public final class OrderEvents {

    /**
     * @param parcelNo         номер посылки в заказе (1..N)
     * @param discountMinor    доля скидки промокода, приходящаяся на эту посылку (0 — без скидки)
     * @param discountFundedBy {@code PLATFORM} — скидку несёт площадка (уменьшает её доход), {@code SHOP} — магазин
     *                         (уменьшает его выплату); {@code null}, если скидки нет
     */
    public record SuborderLine(UUID suborderId, UUID shopId, int parcelNo, long goodsAmountMinor, long costAmountMinor,
                               long discountMinor, String discountFundedBy) {

        public SuborderLine(UUID suborderId, UUID shopId, long goodsAmountMinor, long costAmountMinor) {
            this(suborderId, shopId, 1, goodsAmountMinor, costAmountMinor, 0, null);
        }
    }

    /** @param orderNumber «24-08317» — для уведомлений и экранов */
    public record OrderCreated(
            UUID orderId,
            String orderNumber,
            UUID clientUserId,
            UUID cityId,
            List<SuborderLine> suborders,
            long totalAmountMinor,
            String currency) {
    }

    public record OrderCancelled(UUID orderId, String reason, List<UUID> suborderIds) {
    }

    public record SuborderStatusChanged(
            UUID suborderId,
            UUID orderId,
            UUID shopId,
            String fromStatus,
            String toStatus) {
    }

    public record OrderCompleted(UUID orderId, UUID clientUserId, long totalAmountMinor, String currency) {
    }

    private OrderEvents() {
    }
}
