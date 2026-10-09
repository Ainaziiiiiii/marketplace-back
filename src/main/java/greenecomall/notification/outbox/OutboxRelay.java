package greenecomall.notification.outbox;

import greenecomall.common.events.InProcessEventBus;
import greenecomall.notification.domain.OutboxMessage;
import greenecomall.notification.repo.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/**
 * Досылает неотправленные строки outbox в шину событий приложения. Порядок — по времени создания.
 * Ошибка отправки одной строки не блокирует остальные: она будет повторена на следующем тике.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository outbox;
    private final InProcessEventBus bus;
    private final Clock clock;

    public OutboxRelay(OutboxRepository outbox, InProcessEventBus bus, Clock clock) {
        this.outbox = outbox;
        this.bus = bus;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${notification.outbox-poll-interval:2s}")
    @Transactional
    public void flush() {
        List<OutboxMessage> batch = outbox.findTop100BySentAtIsNullOrderByCreatedAtAsc();
        for (OutboxMessage message : batch) {
            try {
                bus.send(message.getTopic(), message.getId().toString(), message.getPayload());
                message.markSent(clock.instant());
            } catch (RuntimeException e) {
                // подробности (со стеком) уже залогировала шина
                log.warn("outbox: {} id={} not delivered yet, will retry: {}",
                        message.getEventType(), message.getId(), e.getMessage());
            }
        }
    }
}
