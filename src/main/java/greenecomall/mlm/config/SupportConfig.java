package greenecomall.mlm.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(MlmProperties.class)
public class SupportConfig {

    /** RestClient к внешнему MLM-бэку (обратная синхронизация). */
    @Bean
    public RestClient mlmBackendRestClient(MlmProperties properties) {
        RestClient.Builder builder = RestClient.builder();
        if (properties.sync().enabled()) {
            builder.baseUrl(properties.sync().baseUrl());
        }
        return builder.build();
    }
}
