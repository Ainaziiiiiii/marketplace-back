package greenecomall.catalog.config;

import greenecomall.catalog.photo.PhotoProperties;
import greenecomall.catalog.text.ProofreadProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({PhotoProperties.class, ProofreadProperties.class})
public class SupportConfig {
}
