package greenecomall.finance.mlm;

import greenecomall.common.domain.DomainException;
import greenecomall.finance.FinanceErrors;
import greenecomall.mlm.core.AccountService;
import greenecomall.mlm.domain.MlmTariff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

/**
 * Цена доступа к MLM-программе из модуля MLM. Сумма платежа за доступ берётся только отсюда —
 * клиент не может сам назначить себе цену. Отдельная read-only транзакция: отказ MLM-модуля не
 * помечает транзакцию платежа rollback-only.
 */
@Component
public class MlmTariffClient {

    private final AccountService accounts;
    private final TransactionTemplate tx;

    public MlmTariffClient(AccountService accounts, PlatformTransactionManager transactionManager) {
        this.accounts = accounts;
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.tx.setReadOnly(true);
    }

    public Tariff payableTariff(UUID tariffId) {
        try {
            return tx.execute(status -> {
                MlmTariff t = accounts.payableTariff(tariffId);
                return new Tariff(t.getId(), t.getAccessPriceMinor(), t.getCurrency());
            });
        } catch (DomainException e) {
            throw new DomainException(FinanceErrors.MLM_TARIFF_NOT_FOUND, "mlm tariff not payable: " + tariffId, e);
        } catch (RuntimeException e) {
            throw new DomainException(FinanceErrors.MLM_UNAVAILABLE, "mlm tariff lookup failed for " + tariffId, e);
        }
    }

    public record Tariff(UUID id, long accessPriceMinor, String currency) {
    }
}
