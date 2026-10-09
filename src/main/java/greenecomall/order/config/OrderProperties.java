package greenecomall.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Настройки модуля заказов.
 *
 * @param outboxPollInterval период опроса таблицы outbox публикатором
 * @param reservationTimeout сколько ждать оплату, прежде чем отменить заказ и снять резерв остатков
 */
@ConfigurationProperties(prefix = "order")
public record OrderProperties(
        Duration outboxPollInterval,
        Duration reservationTimeout) {

    public OrderProperties {
        if (outboxPollInterval == null) {
            outboxPollInterval = Duration.ofSeconds(2);
        }
        if (reservationTimeout == null) {
            reservationTimeout = Duration.ofMinutes(15);
        }
    }
}
