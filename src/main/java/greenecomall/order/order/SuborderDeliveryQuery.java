package greenecomall.order.order;

import greenecomall.order.domain.DeliveryAddress;
import greenecomall.order.domain.Order;
import greenecomall.order.domain.Suborder;
import greenecomall.order.web.dto.SuborderDeliveryResponse;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

/** Для приложения курьера: адрес доставки, состав и сумма посылки. */
@Component
public class SuborderDeliveryQuery {

    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    public SuborderDeliveryQuery(OrderService orderService, ObjectMapper objectMapper) {
        this.orderService = orderService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public SuborderDeliveryResponse delivery(UUID suborderId) {
        Suborder suborder = orderService.requireSuborder(suborderId);
        Order order = orderService.get(suborder.getOrderId());
        return SuborderDeliveryResponse.of(suborder, order, orderService.suborders(order.getId()).size(),
                orderService.suborderItems(suborderId), address(order));
    }

    /** Адрес заказа из jsonb; адреса старого свободного формата читаются в те поля, что совпали. */
    public DeliveryAddress address(Order order) {
        return objectMapper.readValue(order.getDeliveryAddress(), DeliveryAddress.class);
    }
}
