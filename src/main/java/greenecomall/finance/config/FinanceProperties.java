package greenecomall.finance.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param platformOwnerRef ownerRef кошелька платформы (константа — платформа одна)
 * @param commissionOwnerRef ownerRef кошелька комиссии платформы
 * @param mockWebhookSecret общий секрет для проверки подписи webhook mock-эквайринга
 */
@ConfigurationProperties(prefix = "finance")
public record FinanceProperties(
        String platformOwnerRef,
        String commissionOwnerRef,
        String mockWebhookSecret,
        String defaultCurrency) {

    public FinanceProperties {
        if (platformOwnerRef == null || platformOwnerRef.isBlank()) {
            platformOwnerRef = "platform.incoming";
        }
        if (commissionOwnerRef == null || commissionOwnerRef.isBlank()) {
            commissionOwnerRef = "platform.commission";
        }
        if (mockWebhookSecret == null || mockWebhookSecret.isBlank()) {
            mockWebhookSecret = "mock-secret";
        }
        if (defaultCurrency == null || defaultCurrency.isBlank()) {
            defaultCurrency = "KGS";
        }
    }
}
