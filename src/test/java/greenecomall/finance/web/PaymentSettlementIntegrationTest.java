package greenecomall.finance.web;

import greenecomall.common.events.payload.OrderEvents;
import greenecomall.common.security.Roles;
import greenecomall.common.test.TestJwts;
import greenecomall.common.test.TestSecurityConfig;
import greenecomall.finance.domain.WalletOwnerType;
import greenecomall.finance.domain.Payment;
import greenecomall.finance.payment.PaymentService;
import greenecomall.finance.wallet.SettlementService;
import greenecomall.finance.wallet.WalletService;
import greenecomall.finance.web.dto.PaymentResponse;
import greenecomall.finance.web.dto.WalletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Денежный путь модуля finance против настоящего Postgres: план из OrderCreated → платёж →
 * mock-оплата (подписанный webhook) → распределение по кошелькам магазина и комиссии.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "finance.payout.auto-enabled=false")
@AutoConfigureTestRestTemplate
@Import(TestSecurityConfig.class)
class PaymentSettlementIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private SettlementService settlementService;

    @Autowired
    private WalletService walletService;

    @Autowired
    private PaymentService paymentService;

    @Test
    void paidOrder_isSettledIntoShopAndCommissionWallets() {
        UUID orderId = UUID.randomUUID();
        UUID client = UUID.randomUUID();
        UUID shop = UUID.randomUUID();
        UUID shopOwner = UUID.randomUUID();
        walletService.registerOwner(WalletOwnerType.SHOP, shop.toString(), shopOwner);
        settlementService.capturePlan(new OrderEvents.OrderCreated(orderId, "26-00001", client, UUID.randomUUID(),
                List.of(new OrderEvents.SuborderLine(UUID.randomUUID(), shop, 24_000, 20_000)), 24_000, "KGS"));

        Payment created = paymentService.createOrderPayment(orderId, client, 24_000, "KGS");
        assertThat(created.getStatus().name()).isEqualTo("PENDING");

        ResponseEntity<Void> paid = rest.postForEntity(
                "/mock-acquiring/pay/" + created.getProviderPaymentId() + "?outcome=succeeded", null, Void.class);
        assertThat(paid.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        PaymentResponse after = rest.exchange("/payments/by-order/" + orderId, HttpMethod.GET,
                as(client, Roles.CLIENT_EXTERNAL), PaymentResponse.class).getBody();
        assertThat(after.status()).isEqualTo("SUCCEEDED");
        assertThat(rest.exchange("/payments/" + created.getId(), HttpMethod.GET,
                as(UUID.randomUUID(), Roles.CLIENT_EXTERNAL), String.class).getStatusCode())
                .as("чужой платёж").isEqualTo(HttpStatus.FORBIDDEN);

        List<WalletResponse> shopWallets = rest.exchange("/wallet", HttpMethod.GET, as(shopOwner, Roles.SHOP),
                new ParameterizedTypeReference<List<WalletResponse>>() { }).getBody();
        assertThat(shopWallets).singleElement().satisfies(w -> assertThat(w.balanceMinor()).isEqualTo(20_000));

        WalletResponse commission = rest.exchange(
                "/admin/wallets?ownerType=PLATFORM&ownerRef=platform.commission&currency=KGS", HttpMethod.GET,
                as(UUID.randomUUID(), Roles.ADMIN), WalletResponse.class).getBody();
        assertThat(commission.balanceMinor()).isEqualTo(4_000);

        // повторное создание платежа по тому же заказу — идемпотентно
        assertThat(paymentService.createOrderPayment(orderId, client, 24_000, "KGS").getId()).isEqualTo(created.getId());
    }

    private static HttpEntity<Void> as(UUID userId, String role) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, TestJwts.bearer(userId, role));
        return new HttpEntity<>(headers);
    }
}
