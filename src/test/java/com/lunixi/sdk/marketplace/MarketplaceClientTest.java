package com.lunixi.sdk.marketplace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import com.lunixi.sdk.LunixiClient;
import com.lunixi.sdk.LunixiConfig;
import com.lunixi.sdk.auth.CanonicalRequest;
import com.lunixi.sdk.auth.Ed25519KeyPair;
import com.lunixi.sdk.exception.ConfigurationException;
import com.lunixi.sdk.exception.LunixiApiException;
import com.lunixi.sdk.http.Route;
import com.lunixi.sdk.http.RoutePage;
import com.lunixi.sdk.json.Json;
import com.lunixi.sdk.support.RecordingTransport;

/**
 * Marketplace: route table and signing, split charges (legs total, policy, commercial context), card data kept out of
 * error messages, dealer lifecycle, reporting, payouts and the audit log. Names and card data are fictional test
 * values.
 */
class MarketplaceClientTest {

    static final String GATEWAY = "https://gateway.invalid";
    static final String MP = GATEWAY + "/api/v1/marketplace";
    static final String PAY = GATEWAY + "/api/v1/payments/marketplace";
    static final String CARD_NUMBER = "5400000000000004";

    RecordingTransport transport;
    MarketplaceClient marketplace;

    @BeforeEach
    void setUp() {
        transport = new RecordingTransport("/api/v1/auth/token", "fixture-only");
        marketplace = LunixiClient.create(LunixiConfig.builder().baseUrl(GATEWAY).keyId("test-key")
                .privateKey(Ed25519KeyPair.generate().privateKeyPem()).transport(transport).maxRetries(0).build())
                .marketplace();
    }

    static Map<String, Object> map(Object... pairs) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            out.put((String) pairs[i], pairs[i + 1]);
        }
        return out;
    }

    static Map<String, Object> body(RecordingTransport.Call call) {
        return call.body == null ? null : Json.parseObject(call.body);
    }

    static List<Map<String, Object>> legs() {
        return List.of(map("subDealerId", "d_alice", "amountMinor", "60000"),
                map("subDealerId", "d_bob", "amountMinor", "40000", "scenario",
                        map("type", "DYNAMIC_RATE", "ratePercent", "5")));
    }

    static Map<String, Object> chargeRequest(String amount) {
        return map("amount", amount, "currency", "TRY", "orderId", "ORD-MP-1",
                "card", map("cardHolderName", "Ada Yilmaz", "cardNumber", CARD_NUMBER, "expireMonth", "12",
                        "expireYear", "28", "cvcNumber", "123"),
                "buyer", map("name", "Ada", "surname", "Yilmaz"),
                "billingAddress", map("city", "Istanbul"),
                "basketItems", List.of(map("id", "SKU-1", "price", amount, "name", "Demo", "category1", "Demo",
                        "itemType", "PHYSICAL")));
    }

    static Map<String, Object> with(Map<String, Object> base, String field, Object value) {
        Map<String, Object> out = new LinkedHashMap<>(base);
        if (value == null) {
            out.remove(field);
        } else {
            out.put(field, value);
        }
        return out;
    }

    void assertNothingSent() {
        assertTrue(transport.apiCalls().isEmpty(), "no request may be sent");
    }

    @Test
    void chargesAreSignedAndDealerRoutesBearerOnly() {
        assertEquals(26, MarketplaceRoutes.ROUTES.size());
        MarketplaceRoutes.ROUTES.forEach((name, route) -> {
            boolean charge = route.path().startsWith("/api/v1/payments/marketplace/");
            assertEquals(charge, route.stepUp(), name);
            if (charge) {
                assertEquals(Route.KeyRule.OPTIONAL, route.keyRule(), name);
                assertTrue(route.free(), name);
            } else {
                assertTrue(route.path().startsWith("/api/v1/marketplace/"), name);
                assertEquals(Route.KeyRule.NONE, route.keyRule(), name);
            }
        });
    }

    @Test
    void valuesAndOnboardingUrl() {
        assertEquals(List.of("DRAFT", "PENDING_KYC", "ACTIVE", "SUSPENDED", "HOLD", "CLOSED"),
                MarketplaceValues.DealerStatus.ALL);
        assertEquals("https://altbayi.lunixi.com/m/mp_xyz", MarketplaceValues.onboardingUrl(Map.of("token", "mp_xyz")));
        assertEquals("https://onboard.example/m/mp_xyz", MarketplaceValues.onboardingUrl(
                Map.of("token", "mp_xyz", "url", "https://onboard.example/m/mp_xyz")));
    }

    @Test
    void createCheckoutIntentSendsTheSplitSignedWithTheKey() {
        transport.enqueue(201, "{\"success\":true,\"paymentId\":\"pi_1\",\"token\":\"tok_1\","
                + "\"checkoutFormContent\":\"<script></script>\"}");
        Map<String, Object> intent = marketplace.createCheckoutIntent(map("amount", "100000", "currency", "TRY",
                "orderId", "WC-7"), legs(), MarketplaceValues.MultiSellerPolicy.ALL_OR_NOTHING, "idem-mp-1");
        assertEquals("tok_1", intent.get("token"));
        RecordingTransport.Call call = transport.onlyApiCall();
        assertEquals(PAY + "/intents", call.url);
        assertNotNull(call.headers.get(CanonicalRequest.HEADER_SIGNATURE), "the split checkout is signed");
        assertEquals("idem-mp-1", call.headers.get("Idempotency-Key"));
        Map<String, Object> sent = body(call);
        assertEquals("100000", sent.get("amount"));
        assertEquals("ALL_OR_NOTHING", sent.get("multiSellerPolicy"));
        List<?> sellers = (List<?>) sent.get("sellers");
        assertEquals(map("subDealerId", "d_alice", "amountMinor", "60000"), sellers.get(0));
        assertEquals("DYNAMIC_RATE", ((Map<?, ?>) ((Map<?, ?>) sellers.get(1)).get("scenario")).get("type"));
    }

    @Test
    void createCheckoutIntentThrowsWhenTheAnswerHasNoToken() {
        transport.enqueue(200, "{\"success\":false,\"code\":\"marketplace.payment.seller_not_eligible\","
                + "\"message\":\"seller not eligible\"}");
        LunixiApiException error = assertThrows(LunixiApiException.class, () -> marketplace.createCheckoutIntent(
                map("amount", "1000", "currency", "TRY", "orderId", "o1"),
                List.of(map("subDealerId", "d_1", "amountMinor", "1000"))));
        assertEquals("marketplace.payment.seller_not_eligible", error.code().orElseThrow());
        assertEquals(200, error.statusCode());
    }

    @Test
    void sellerLegsAreCheckedBeforeSending() {
        Map<String, Object> intent = map("amount", "1000", "currency", "TRY", "orderId", "o1");
        ConfigurationException empty = assertThrows(ConfigurationException.class,
                () -> marketplace.createCheckoutIntent(intent, List.of()));
        assertTrue(empty.getMessage().contains("at least one seller"), empty.getMessage());
        assertThrows(ConfigurationException.class, () -> marketplace.createCheckoutIntent(intent,
                List.of(map("subDealerId", "d_1", "amountMinor", "0"))));
        assertThrows(ConfigurationException.class, () -> marketplace.createCheckoutIntent(intent,
                List.of(map("subDealerId", "d_1", "amountMinor", 1000L))));
        assertThrows(ConfigurationException.class, () -> marketplace.createCheckoutIntent(intent,
                List.of(map("subDealerId", "d_1", "amountMinor", "1000", "commission", "1"))));
        ConfigurationException total = assertThrows(ConfigurationException.class,
                () -> marketplace.createCheckoutIntent(intent, List.of(map("subDealerId", "d_1",
                        "amountMinor", "999"))));
        assertTrue(total.getMessage().contains("add up to 999"), total.getMessage());
        ConfigurationException policy = assertThrows(ConfigurationException.class,
                () -> marketplace.createCheckoutIntent(intent, List.of(map("subDealerId", "d_1",
                        "amountMinor", "1000")), "SOME", null));
        assertTrue(policy.getMessage().contains("multiSellerPolicy"), policy.getMessage());
        assertThrows(ConfigurationException.class, () -> marketplace.createCheckoutIntent(with(intent, "sellers",
                List.of()), List.of(map("subDealerId", "d_1", "amountMinor", "1000"))));
        assertThrows(ConfigurationException.class, () -> marketplace.createCheckoutIntent(intent,
                List.of(map("subDealerId", "d_1", "amountMinor", "1000")), "ALL_OR_NOTHING", " "));
        assertNothingSent();
    }

    @Test
    void cardChargesSendTheCardBodyWithTheLegsToTheRightRoute() {
        transport.enqueue(200, "{\"status\":\"success\",\"data\":{\"paymentId\":\"pay_1\",\"status\":\"SUCCEEDED\"}}");
        transport.enqueue(200, "{\"status\":\"success\",\"data\":{\"paymentId\":\"pay_2\",\"status\":\"PENDING\"}}");
        marketplace.chargeMarketplaceCard2d(chargeRequest("100000"), legs(),
                MarketplaceValues.MultiSellerPolicy.PARTIAL_ALLOWED, "idem-2d");
        marketplace.chargeMarketplaceCard3d(with(chargeRequest("100000"), "callbackUrl", "https://shop.example/3ds"),
                legs());
        List<RecordingTransport.Call> calls = transport.apiCalls();
        assertEquals(PAY + "/2d", calls.get(0).url);
        assertEquals(PAY + "/3d", calls.get(1).url);
        assertNotNull(calls.get(0).headers.get(CanonicalRequest.HEADER_SIGNATURE));
        assertNotNull(calls.get(1).headers.get(CanonicalRequest.HEADER_SIGNATURE));
        assertEquals("idem-2d", calls.get(0).headers.get("Idempotency-Key"));
        assertNull(calls.get(1).headers.get("Idempotency-Key"));
        Map<String, Object> sent = body(calls.get(0));
        assertEquals("PARTIAL_ALLOWED", sent.get("multiSellerPolicy"));
        assertEquals(2, ((List<?>) sent.get("sellers")).size());
        assertEquals(CARD_NUMBER, ((Map<?, ?>) sent.get("card")).get("cardNumber"));
    }

    @Test
    void aChargeWithoutItsContextWithPriceOrWithAStringPreauthIsRefusedWithoutEchoingTheCard() {
        List<Executable> calls = List.of(
                () -> marketplace.chargeMarketplaceCard2d(with(chargeRequest("100000"), "buyer", null), legs()),
                () -> marketplace.chargeMarketplaceCard2d(with(chargeRequest("100000"), "price", "100000"), legs()),
                () -> marketplace.chargeMarketplaceCard2d(with(chargeRequest("100000"), "preauth", "false"), legs()),
                () -> marketplace.chargeMarketplaceCard2d(with(chargeRequest("100000"), "basketItems", List.of()),
                        legs()),
                () -> marketplace.chargeMarketplaceCard2d(chargeRequest("100001"), legs()),
                () -> marketplace.chargeMarketplaceCard(with(chargeRequest("100000"), "amount", 100000L)),
                () -> marketplace.chargeMarketplaceCard(with(chargeRequest("100000"), "basketItems",
                        List.of(map("id", "SKU-1", "price", 100000L)))));
        List<String> messages = new java.util.ArrayList<>();
        for (Executable call : calls) {
            ConfigurationException error = assertThrows(ConfigurationException.class, call);
            assertFalse(error.getMessage().contains(CARD_NUMBER), error.getMessage());
            assertFalse(error.getMessage().matches("(?s).*\\b123\\b.*"), error.getMessage());
            messages.add(error.getMessage());
        }
        assertTrue(messages.get(0).contains("buyer"), messages.get(0));
        assertTrue(messages.get(4).contains("add up to"), messages.get(4));
        assertNothingSent();
    }

    @Test
    void dealerLifecycleRoutesAreBearerOnly() {
        for (int i = 0; i < 4; i++) {
            transport.enqueue(201, "{\"success\":true,\"data\":{\"id\":\"d_1\",\"legalName\":\"Acme\",\"status\":"
                    + "\"DRAFT\"}}");
        }
        Map<String, Object> dealer = marketplace.createDealer(map("legalName", "Acme", "taxId", "123",
                "externalRef", "42"));
        assertEquals("d_1", dealer.get("id"));
        marketplace.updateDealerStatus("d_1", MarketplaceValues.DealerStatus.SUSPENDED);
        marketplace.activateDealer("d_1");
        marketplace.setDealerTerms("d_1", map("commissionBps", 250));
        List<RecordingTransport.Call> calls = transport.apiCalls();
        assertEquals(MP + "/dealers", calls.get(0).url);
        assertEquals(map("legalName", "Acme", "taxId", "123", "externalRef", "42"), body(calls.get(0)));
        assertEquals("PUT", calls.get(1).method);
        assertEquals(map("status", "SUSPENDED"), body(calls.get(1)));
        assertEquals(MP + "/dealers/d_1/activate", calls.get(2).url);
        assertNull(calls.get(2).body);
        assertEquals(MP + "/dealers/d_1/terms", calls.get(3).url);
        calls.forEach(call -> assertNull(call.headers.get(CanonicalRequest.HEADER_SIGNATURE)));

        assertThrows(ConfigurationException.class, () -> marketplace.updateDealerStatus("d_1", "PAUSED"));
        assertThrows(ConfigurationException.class, () -> marketplace.createDealer(map("legalName", "Acme",
                "dealerType", "COMPANY")));
        ConfigurationException missing = assertThrows(ConfigurationException.class,
                () -> marketplace.createDealer(map("taxId", "123")));
        assertTrue(missing.getMessage().contains("requires legalName"), missing.getMessage());
        assertThrows(ConfigurationException.class, () -> marketplace.bulkCreateDealers(List.of()));
        assertEquals(4, transport.apiCalls().size());
    }

    @Test
    void listDealersReadsTheItemsEnvelope() {
        transport.enqueue(200, "{\"status\":\"success\",\"data\":{\"items\":[{\"id\":\"d_1\",\"status\":\"ACTIVE\"},"
                + "{\"id\":\"d_2\",\"status\":\"DRAFT\"}],\"total\":2,\"pageInfo\":{\"hasMore\":false,"
                + "\"nextCursor\":null,\"totalCount\":2}}}");
        RoutePage page = marketplace.listDealers(map("status", "ACTIVE"));
        assertEquals(2, page.items().size());
        assertEquals(2L, page.totalCount().orElseThrow());
        assertFalse(page.hasMore());
        assertEquals(MP + "/dealers?status=ACTIVE", transport.onlyApiCall().url);
    }

    @Test
    void onboardingLinkIsReadFromTheEnvelopeRoot() {
        transport.enqueue(201, "{\"success\":true,\"token\":\"mp_xyz\"}");
        Map<String, Object> link = marketplace.onboardingToken("d_1");
        assertEquals(MP + "/dealers/d_1/onboarding-token", transport.onlyApiCall().url);
        assertEquals("mp_xyz", link.get("token"));
        assertEquals("https://altbayi.lunixi.com/m/mp_xyz", MarketplaceValues.onboardingUrl(link));
    }

    @Test
    void payoutsCommissionsAndTheAuditLog() {
        for (int i = 0; i < 5; i++) {
            transport.enqueue(200, "{\"success\":true,\"data\":{\"items\":[]}}");
        }
        marketplace.createPayout(map("subDealerId", "d_1", "amountMinor", "5000", "currency", "TRY"));
        marketplace.resolveCommission("d_1", "ELECTRONICS", "100000");
        marketplace.bulkMarkPayoutsPaid(List.of("po_1", "po_2"), map("reference", "R-1"));
        marketplace.auditLog(map("limit", 20));
        marketplace.verifyAuditLog();
        List<RecordingTransport.Call> calls = transport.apiCalls();
        assertEquals(MP + "/payouts", calls.get(0).url);
        assertEquals(MP + "/commission/resolve?subDealerId=d_1&categoryCode=ELECTRONICS&mtdVolumeMinor=100000",
                calls.get(1).url);
        assertEquals(map("reference", "R-1", "payoutIds", List.of("po_1", "po_2")), body(calls.get(2)));
        assertEquals(MP + "/audit-log?limit=20", calls.get(3).url);
        assertEquals(MP + "/audit-log/verify", calls.get(4).url);

        assertThrows(ConfigurationException.class, () -> marketplace.createPayout(map("subDealerId", "d_1",
                "amountMinor", 5000L)));
        ConfigurationException volume = assertThrows(ConfigurationException.class,
                () -> marketplace.resolveCommission("d_1", null, "1000.50"));
        assertTrue(volume.getMessage().contains("mtdVolumeMinor"), volume.getMessage());
        ConfigurationException filter = assertThrows(ConfigurationException.class,
                () -> marketplace.commissionRules(map("includeArchived", true)));
        assertTrue(filter.getMessage().contains("does not accept the filter"), filter.getMessage());
        assertThrows(ConfigurationException.class, () -> marketplace.bulkMarkPayoutsPaid(List.of()));
        assertEquals(5, transport.apiCalls().size());
    }

    @Test
    void aMalformedAmountInADealerAnswerIsRefused() {
        transport.enqueue(200, "{\"success\":true,\"data\":{\"items\":[{\"payoutAmountMinor\":12.5}]}}");
        assertThrows(LunixiApiException.class, () -> marketplace.payoutLedger());
    }
}
