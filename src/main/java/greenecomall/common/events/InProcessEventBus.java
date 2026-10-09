package greenecomall.common.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Шина доменных событий внутри приложения — замена Kafka. Строки outbox модулей досылаются сюда
 * их {@code OutboxRelay}; шина вызывает методы с {@link TopicListener} синхронно, каждого подписчика —
 * в своей транзакции ({@code REQUIRES_NEW}), так что сбой одного не откатывает других и не трогает relay.
 * <p>
 * Сбой подписчика → {@link DeliveryFailedException}, relay оставит строку неотправленной и повторит на
 * следующем тике (успевшие подписчики отсеют повтор по {@code eventId}). После {@link #MAX_ATTEMPTS}
 * неудач подряд событие для этого подписчика пропускается с ошибкой в логе — как DefaultErrorHandler Kafka.
 */
@Component
public class InProcessEventBus implements BeanPostProcessor {

    static final int MAX_ATTEMPTS = 10;

    private static final Logger log = LoggerFactory.getLogger(InProcessEventBus.class);

    private final Map<String, List<Subscriber>> subscribers = new ConcurrentHashMap<>();
    private final Map<String, Integer> failures = new ConcurrentHashMap<>();
    private final ObjectProvider<PlatformTransactionManager> transactionManager;
    private volatile TransactionTemplate tx;

    /** Менеджер транзакций берётся лениво: BeanPostProcessor создаётся раньше JPA-бинов. */
    public InProcessEventBus(ObjectProvider<PlatformTransactionManager> transactionManager) {
        this.transactionManager = transactionManager;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        Class<?> type = AopUtils.getTargetClass(bean);
        Map<Method, TopicListener> methods = MethodIntrospector.selectMethods(type,
                (MethodIntrospector.MetadataLookup<TopicListener>) m ->
                        AnnotatedElementUtils.findMergedAnnotation(m, TopicListener.class));
        methods.forEach((method, annotation) -> {
            if (method.getParameterCount() != 1 || method.getParameterTypes()[0] != String.class) {
                throw new IllegalStateException("@TopicListener method must take a single String: " + method);
            }
            Method invocable = AopUtils.selectInvocableMethod(method, bean.getClass());
            ReflectionUtils.makeAccessible(invocable);
            Subscriber subscriber = new Subscriber(beanName + "#" + method.getName(), bean, invocable);
            for (String topic : annotation.value()) {
                subscribers.computeIfAbsent(topic, t -> new CopyOnWriteArrayList<>()).add(subscriber);
            }
        });
        return bean;
    }

    /**
     * Доставляет событие всем подписчикам топика.
     *
     * @param messageId id строки outbox — для учёта повторов
     * @throws DeliveryFailedException если хотя бы один подписчик упал и попытки ещё не исчерпаны
     */
    public void send(String topic, String messageId, String payload) {
        int failed = 0;
        for (Subscriber subscriber : subscribers.getOrDefault(topic, List.of())) {
            String attemptKey = subscriber.name() + "|" + messageId;
            try {
                tx().executeWithoutResult(status -> subscriber.invoke(payload));
                failures.remove(attemptKey);
            } catch (RuntimeException e) {
                int attempts = failures.merge(attemptKey, 1, Integer::sum);
                if (attempts >= MAX_ATTEMPTS) {
                    failures.remove(attemptKey);
                    log.error("event bus: {} failed {} times on {} message {}, skipping",
                            subscriber.name(), attempts, topic, messageId, e);
                } else {
                    failed++;
                    log.warn("event bus: {} failed on {} message {} (attempt {}), will retry",
                            subscriber.name(), topic, messageId, attempts, e);
                }
            }
        }
        if (failed > 0) {
            throw new DeliveryFailedException(topic + " message " + messageId + ": " + failed + " subscriber(s) failed");
        }
    }

    private TransactionTemplate tx() {
        TransactionTemplate template = tx;
        if (template == null) {
            template = new TransactionTemplate(transactionManager.getObject());
            template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            tx = template;
        }
        return template;
    }

    private record Subscriber(String name, Object bean, Method method) {

        void invoke(String payload) {
            try {
                method.invoke(bean, payload);
            } catch (InvocationTargetException e) {
                if (e.getCause() instanceof RuntimeException re) {
                    throw re;
                }
                throw new IllegalStateException(e.getCause());
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    public static class DeliveryFailedException extends RuntimeException {

        public DeliveryFailedException(String message) {
            super(message);
        }
    }
}
