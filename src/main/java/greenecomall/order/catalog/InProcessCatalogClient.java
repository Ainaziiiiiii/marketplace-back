package greenecomall.order.catalog;

import greenecomall.catalog.domain.Product;
import greenecomall.catalog.pricing.MarkupService;
import greenecomall.catalog.pricing.PriceResolution;
import greenecomall.catalog.product.ProductService;
import greenecomall.catalog.shop.ShopService;
import greenecomall.catalog.stock.StockService;
import greenecomall.catalog.web.CatalogExceptionHandler;
import greenecomall.common.domain.DomainException;
import greenecomall.order.OrderErrors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Реализация {@link CatalogClient} прямыми вызовами модуля каталога. Каждый вызов — в отдельной
 * транзакции (как раньше по HTTP): отказ каталога не помечает транзакцию заказа rollback-only.
 * Ошибки каталога переводятся в коды заказа: «не найдено» → свой код, конфликт остатка на резерве →
 * {@link OrderErrors#STOCK_INSUFFICIENT}, прочее → {@link OrderErrors#CATALOG_UNAVAILABLE}.
 */
@Component
public class InProcessCatalogClient implements CatalogClient {

    private final ProductService productService;
    private final MarkupService markupService;
    private final StockService stockService;
    private final ShopService shopService;
    private final TransactionTemplate tx;

    public InProcessCatalogClient(ProductService productService, MarkupService markupService,
                                  StockService stockService, ShopService shopService,
                                  PlatformTransactionManager transactionManager) {
        this.productService = productService;
        this.markupService = markupService;
        this.stockService = stockService;
        this.shopService = shopService;
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public StorefrontProduct storefrontProduct(UUID productId, UUID cityId) {
        return call("catalog product lookup failed for " + productId,
                status -> status == HttpStatus.NOT_FOUND ? new DomainException(OrderErrors.CART_ITEM_NOT_FOUND,
                        "product not found in catalog: " + productId) : null,
                () -> {
                    Product product = productService.requireSellable(productId, cityId);
                    UUID effectiveCityId = cityId != null ? cityId : product.getCity().getId();
                    PriceResolution price = markupService.resolve(product, effectiveCityId);
                    return new StorefrontProduct(product.getId(), product.getShop().getId(), product.getName(),
                            price.salePriceMinor(), price.currency());
                });
    }

    @Override
    public PriceView price(UUID productId, UUID cityId) {
        return call("catalog price resolve failed for product " + productId,
                status -> status == HttpStatus.NOT_FOUND ? new DomainException(OrderErrors.PRODUCT_UNAVAILABLE,
                        "product is no longer on sale in this city: " + productId) : null,
                () -> {
                    // checkout: товар могли снять с продажи/архивировать после добавления в корзину
                    PriceResolution r = markupService.resolve(productService.requireSellable(productId, cityId), cityId);
                    return new PriceView(r.productId(), r.costPriceMinor(), r.salePriceMinor(), r.markupPercent(),
                            r.currency());
                });
    }

    @Override
    public ShopView shop(UUID shopId) {
        return call("catalog shop lookup failed for " + shopId,
                status -> status == HttpStatus.NOT_FOUND
                        ? new DomainException(OrderErrors.SHOP_NOT_FOUND, "shop not found: " + shopId) : null,
                () -> {
                    var shop = shopService.get(shopId);
                    return new ShopView(shop.getId(), shop.getOwnerUserId(), shop.getStatus().name());
                });
    }

    @Override
    public void reserve(UUID productId, int qty) {
        call("catalog stock reserve failed for product " + productId,
                status -> status == HttpStatus.CONFLICT ? new DomainException(OrderErrors.STOCK_INSUFFICIENT,
                        "not enough stock for product " + productId) : null,
                () -> {
                    stockService.reserve(productId, qty);
                    return null;
                });
    }

    @Override
    public void release(UUID productId, int qty) {
        call("catalog stock release failed for product " + productId, status -> null, () -> {
            stockService.release(productId, qty);
            return null;
        });
    }

    @Override
    public void commit(UUID productId, int qty) {
        call("catalog stock commit failed for product " + productId, status -> null, () -> {
            stockService.commit(productId, qty);
            return null;
        });
    }

    /**
     * @param translate ошибка каталога (по её HTTP-статусу) → ошибка заказа; {@code null} — недоступность каталога
     */
    private <T> T call(String failure, Function<HttpStatus, DomainException> translate, Supplier<T> action) {
        try {
            return tx.execute(status -> action.get());
        } catch (DomainException e) {
            DomainException translated = translate.apply(CatalogExceptionHandler.statusOf(e.getCode()));
            if (translated != null) {
                throw translated;
            }
            throw new DomainException(OrderErrors.CATALOG_UNAVAILABLE, failure + ": " + e.getCode(), e);
        } catch (RuntimeException e) {
            throw new DomainException(OrderErrors.CATALOG_UNAVAILABLE, failure, e);
        }
    }
}
