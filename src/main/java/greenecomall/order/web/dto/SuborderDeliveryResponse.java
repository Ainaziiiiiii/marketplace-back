package greenecomall.order.web.dto;

import greenecomall.order.domain.DeliveryAddress;
import greenecomall.order.domain.Order;
import greenecomall.order.domain.Suborder;
import greenecomall.order.domain.SuborderItem;

import java.util.List;
import java.util.UUID;

/**
 * Посылка глазами курьера: куда везти и что внутри.
 *
 * @param orderNumber «24-08290»
 * @param parcelNo    номер посылки в заказе (1..parcelsTotal)
 */
public record SuborderDeliveryResponse(
        UUID suborderId, UUID orderId, String orderNumber, int parcelNo, int parcelsTotal, UUID shopId,
        UUID cityId, UUID clientUserId, String status, DeliveryAddress deliveryAddress, long amountMinor,
        String currency, String orderStatus, List<Item> items) {

    public record Item(String name, int qty) {
    }

    public static SuborderDeliveryResponse of(Suborder suborder, Order order, int parcelsTotal,
                                              List<SuborderItem> items, DeliveryAddress deliveryAddress) {
        return new SuborderDeliveryResponse(suborder.getId(), order.getId(), order.getNumber(),
                suborder.getParcelNo(), parcelsTotal, suborder.getShopId(), order.getCityId(),
                order.getClientUserId(), suborder.getStatus().name(), deliveryAddress,
                suborder.getGoodsAmountMinor(), order.getCurrency(), order.getStatus().name(),
                items.stream().map(i -> new Item(i.getNameSnapshot(), i.getQty())).toList());
    }
}
