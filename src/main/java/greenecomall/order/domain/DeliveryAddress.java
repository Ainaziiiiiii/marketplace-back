package greenecomall.order.domain;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Адрес доставки из формы оформления; хранится в {@code orders.delivery_address} (jsonb) как есть.
 * Город не входит — он задан корзиной. Его же видят магазин (кому отдаём), курьер и админка.
 *
 * @param phone         телефон для курьера, +996XXXXXXXXX (нормализуется при оформлении)
 * @param recipientName кому вручить («Эрлан Т.»); по умолчанию — имя покупателя
 * @param comment       комментарий курьеру
 * @param lat           координаты точки на карте, если покупатель её отметил
 */
public record DeliveryAddress(
        @NotBlank @Size(max = 200) String street,
        @NotBlank @Size(max = 30) String house,
        @Size(max = 20) String apartment,
        @Size(max = 10) String entrance,
        @Size(max = 10) String floor,
        @Size(max = 20) String intercom,
        @Size(max = 300) String comment,
        @NotBlank @Size(max = 32) String phone,
        @Size(max = 120) String recipientName,
        @DecimalMin("-90") @DecimalMax("90") Double lat,
        @DecimalMin("-180") @DecimalMax("180") Double lon) {

    public DeliveryAddress withPhone(String normalizedPhone) {
        return new DeliveryAddress(street, house, apartment, entrance, floor, intercom, comment, normalizedPhone,
                recipientName, lat, lon);
    }

    /** «ул. Токтогула, 125/1, кв. 48 · подъезд 2 · этаж 7» — одной строкой для списков и уведомлений. */
    public String oneLine() {
        StringBuilder sb = new StringBuilder(street).append(", ").append(house);
        if (apartment != null && !apartment.isBlank()) {
            sb.append(", кв. ").append(apartment);
        }
        if (entrance != null && !entrance.isBlank()) {
            sb.append(" · подъезд ").append(entrance);
        }
        if (floor != null && !floor.isBlank()) {
            sb.append(" · этаж ").append(floor);
        }
        return sb.toString();
    }
}
