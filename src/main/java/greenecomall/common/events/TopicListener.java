package greenecomall.common.events;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Подписка метода бина на топики {@link InProcessEventBus}. Сигнатура метода — {@code void m(String raw)},
 * где {@code raw} — JSON {@link EventEnvelope}. Доставка «как минимум один раз»: подписчик обязан
 * быть идемпотентным по {@code eventId}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TopicListener {

    /** Имена топиков из {@link Topics}. */
    String[] value();
}
