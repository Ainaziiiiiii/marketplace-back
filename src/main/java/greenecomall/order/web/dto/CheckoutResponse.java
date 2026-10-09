package greenecomall.order.web.dto;

import greenecomall.order.order.OrderService;

import java.util.UUID;

/** @param orderNumber «24-08317» */
public record CheckoutResponse(UUID orderId, String orderNumber, long totalAmountMinor, String currency) {

    public static CheckoutResponse from(OrderService.CheckoutResult r) {
        return new CheckoutResponse(r.orderId(), r.orderNumber(), r.totalAmountMinor(), r.currency());
    }
}
