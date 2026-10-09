package greenecomall;

import greenecomall.common.web.RestExceptionHandler;
import greenecomall.common.web.security.ServiceSecurityConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;
import org.springframework.context.annotation.Import;

/**
 * «Грин Эко Молл» — одно приложение (модульный монолит). Модули — пакеты {@code auth}, {@code catalog},
 * {@code order}, {@code finance}, {@code courier}, {@code mlm}, {@code notification}; у каждого своя схема БД.
 * Модули общаются прямыми вызовами бинов и доменными событиями через outbox +
 * {@link greenecomall.common.events.InProcessEventBus}.
 * <p>
 * Имена бинов — полные имена классов: в модулях есть одноимённые классы ({@code OutboxRelay}, {@code SupportConfig}…).
 */
@SpringBootApplication(nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
@Import({RestExceptionHandler.class, ServiceSecurityConfiguration.class})
public class GreenEcoMallApplication {

    public static void main(String[] args) {
        SpringApplication.run(GreenEcoMallApplication.class, args);
    }
}
