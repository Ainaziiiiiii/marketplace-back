package greenecomall.finance.config;

import greenecomall.finance.finik.FinikProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({FinanceProperties.class, FinikProperties.class})
public class SupportConfig {
}
