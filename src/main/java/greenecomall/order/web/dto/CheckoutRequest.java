package greenecomall.order.web.dto;

import greenecomall.order.domain.DeliveryAddress;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Клиент — пользователь из access-JWT, город — из корзины.
 *
 * @param deliveryAddress адрес по полям формы оформления (улица, дом, квартира, подъезд, этаж, комментарий, телефон)
 * @param promoCode       необязательный промокод
 */
public record CheckoutRequest(
        @NotNull @Valid DeliveryAddress deliveryAddress,
        String promoCode) {
}
