package com.lunixi.sdk.payment;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.lunixi.sdk.LunixiClient;
import com.lunixi.sdk.LunixiConfig;
import com.lunixi.sdk.auth.CanonicalRequest;
import com.lunixi.sdk.auth.Ed25519KeyPair;
import com.lunixi.sdk.exception.ConfigurationException;
import com.lunixi.sdk.json.Json;
import com.lunixi.sdk.support.OrderFixture;
import com.lunixi.sdk.support.RecordingTransport;

/** BIN and installment lookups, stored cards, marketplace intents, analytics and the payment list. */
class PaymentCardsAndMarketplaceTest {
    static final String KEY = "persisted-operation-key-1";
    static final String GATEWAY = "https://gateway.invalid";
    static final String BASE = GATEWAY + "/api/v1/payments";
    static final String OK = "{\"status\":\"success\",\"code\":\"SUCCESS\",\"data\":{}}";
    static final String PAN = "4543600299100712";
    static final String CVV = "715";
    RecordingTransport transport;
    LunixiClient client;

    @BeforeEach void setup() {
        transport = new RecordingTransport("/api/v1/auth/token", "fixture-only");
        client = LunixiClient.create(LunixiConfig.builder().baseUrl(GATEWAY).keyId("test-key")
                .privateKey(Ed25519KeyPair.generate().privateKeyPem()).transport(transport).maxRetries(0).build());
    }

    static void assertSigned(RecordingTransport.Call call) {
        assertNotNull(call.headers.get(CanonicalRequest.HEADER_SIGNATURE), call.url);
        assertEquals("test-key", call.headers.get(CanonicalRequest.HEADER_KEY_ID));
    }

    static void assertBearerOnly(RecordingTransport.Call call) {
        assertEquals("Bearer fixture-only", call.headers.get("Authorization"));
        assertNull(call.headers.get(CanonicalRequest.HEADER_SIGNATURE), call.url);
    }

    void assertNothingSent() { assertTrue(transport.apiCalls().isEmpty(), "no request may be sent"); }

    static Map<String, Object> card() {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("cardHolderName", "Test Buyer");
        card.put("cardNumber", PAN);
        card.put("expireMonth", "12");
        card.put("expireYear", "2030");
        card.put("cvc", CVV);
        return card;
    }

    // ── BIN and installment table ─────────────────────────────────────────────────────────────────────────────

    @Test void binInfoPostsTheBinAndNeverEchoesACardNumber() {
        transport.enqueue(200, OK);
        client.payments().binInfo("454360");
        RecordingTransport.Call call = transport.onlyApiCall();
        assertEquals("POST", call.method);
        assertEquals(GATEWAY + "/api/v1/bin/info", call.url);
        assertEquals("{\"binOrPan\":\"454360\"}", call.body);
        assertBearerOnly(call);

        for (String invalid : new String[]{"4543", "4543 6002 9910 0712", "45436002991007120000", "abcdef"}) {
            ConfigurationException error = assertThrows(ConfigurationException.class,
                    () -> client.payments().binInfo(invalid));
            assertFalse(error.getMessage().contains(invalid), "the value must not be in the message");
        }
        assertThrows(ConfigurationException.class, () -> client.payments().binInfo(null));
        assertEquals(1, transport.apiCalls().size());
    }

    @Test void installmentOptionsSendsTheTypedBodyWithSortedEnabledInstallments() {
        transport.enqueue(200, OK);
        InstallmentOptionsRequest request = InstallmentOptionsRequest.create("10000", "try")
                .withBinOrPan(PAN).withInstallment(3).withCardBrand("EXAMPLE_BRAND")
                .withEnabledInstallments(List.of(3, 1, 2));
        client.payments().installmentOptions(request);
        RecordingTransport.Call call = transport.onlyApiCall();
        assertEquals("POST", call.method);
        assertEquals(GATEWAY + "/api/v1/bin/installments", call.url);
        assertEquals("{\"amount\":\"10000\",\"currency\":\"TRY\",\"binOrPan\":\"" + PAN + "\",\"installment\":3,"
                + "\"cardBrand\":\"EXAMPLE_BRAND\",\"enabledInstallments\":[1,2,3]}", call.body);
        assertBearerOnly(call);
        assertFalse(request.toString().contains(PAN), "toString must not show the card number");
    }

    @Test void enabledInstallmentsFollowTheIntentRule() {
        InstallmentOptionsRequest request = InstallmentOptionsRequest.create("10000", "TRY");
        List<List<Integer>> invalid = List.of(List.of(), List.of(2, 3), List.of(1, 1, 2), List.of(1, 37),
                List.of(0, 1));
        for (List<Integer> counts : invalid) {
            assertThrows(ConfigurationException.class, () -> request.withEnabledInstallments(counts), counts.toString());
        }
        List<Integer> withNull = new ArrayList<>(List.of(1));
        withNull.add(null);
        assertThrows(ConfigurationException.class, () -> request.withEnabledInstallments(withNull));
        assertEquals(List.of(1), EnabledInstallments.require(List.of(1), "enabledInstallments"));

        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("amount", "10000");
        raw.put("currency", "TRY");
        raw.put("enabledInstallments", List.of(2, 3));
        assertThrows(ConfigurationException.class, () -> client.payments().installmentOptions(raw));
        assertThrows(ConfigurationException.class, () -> client.payments().installmentOptions(
                Map.of("amount", 10000, "currency", "TRY")), "numeric amount");
        assertThrows(ConfigurationException.class, () -> InstallmentOptionsRequest.create("0", "TRY"));
        assertThrows(ConfigurationException.class, () -> request.withInstallment(0));
        assertNothingSent();

        transport.enqueue(200, OK);
        raw.put("enabledInstallments", List.of(6, 1));
        client.payments().installmentOptions(raw);
        assertEquals("{\"amount\":\"10000\",\"currency\":\"TRY\",\"enabledInstallments\":[1,6]}",
                transport.onlyApiCall().body);
    }

    @Test void cardTaxonomyIsAPlainRead() {
        transport.enqueue(200, OK);
        client.payments().cardTaxonomy();
        RecordingTransport.Call call = transport.onlyApiCall();
        assertEquals("GET", call.method);
        assertEquals(GATEWAY + "/api/v1/bin/card-taxonomy", call.url);
        assertNull(call.body);
        assertBearerOnly(call);
    }

    // ── Stored cards ──────────────────────────────────────────────────────────────────────────────────────────

    @Test void storeCardIsSignedWithTheRequiredKey() {
        transport.enqueue(200, OK);
        StoreCardRequest request = StoreCardRequest.create(card(), "cuk_1", "https://merchant.example/cards/return")
                .withCurrency("TRY").withBuyer(OrderFixture.BUYER).withBillingAddress(OrderFixture.ADDRESS);
        client.payments().storeCard(request, KEY);
        RecordingTransport.Call call = transport.onlyApiCall();
        assertEquals("POST", call.method);
        assertEquals(BASE + "/cards", call.url);
        assertEquals(KEY, call.headers.get("Idempotency-Key"));
        assertSigned(call);
        assertEquals("{\"card\":{\"cardHolderName\":\"Test Buyer\",\"cardNumber\":\"" + PAN + "\",\"expireMonth\":\"12\","
                + "\"expireYear\":\"2030\",\"cvc\":\"" + CVV + "\"},\"cardUserKey\":\"cuk_1\","
                + "\"callbackUrl\":\"https://merchant.example/cards/return\",\"currency\":\"TRY\","
                + "\"buyer\":" + Json.stringify(OrderFixture.BUYER) + ",\"billingAddress\":"
                + Json.stringify(OrderFixture.ADDRESS) + "}", call.body);
        assertFalse(request.toString().contains(PAN));
        assertFalse(request.toString().contains(CVV));
        assertFalse(request.toString().contains("ada@example.com"));
    }

    @Test void storeCardWithoutAKeyOrCardSendsNothingAndShowsNoCardData() {
        StoreCardRequest request = StoreCardRequest.create(card(), null, null)
                .withBuyer(OrderFixture.BUYER).withBillingAddress(OrderFixture.ADDRESS);
        for (String key : new String[]{null, "", "short"}) {
            ConfigurationException error = assertThrows(ConfigurationException.class,
                    () -> client.payments().storeCard(request, key));
            assertFalse(error.getMessage().contains(PAN));
            assertFalse(error.getMessage().contains(CVV));
        }
        assertThrows(ConfigurationException.class, () -> StoreCardRequest.create(Map.of(), null, null));
        assertThrows(ConfigurationException.class, () -> StoreCardRequest.create(card(), null, "http://merchant.example"));
        assertThrows(ConfigurationException.class, () -> client.payments().storeCard(Map.of("cardUserKey", "cuk_1"), KEY));
        assertNothingSent();
    }

    @Test void storingACardRequiresTheBuyerAndTheBillingAddressButNoBasket() {
        ConfigurationException refused = assertThrows(ConfigurationException.class,
                () -> client.payments().storeCard(StoreCardRequest.create(card(), null, null), KEY));
        assertEquals("Storing a card requires the order details: buyer, billingAddress.", refused.getMessage());
        Map<String, Object> noIp = new LinkedHashMap<>(OrderFixture.BUYER);
        noIp.remove("ip");
        refused = assertThrows(ConfigurationException.class, () -> client.payments().storeCard(
                StoreCardRequest.create(card(), null, null).withBuyer(noIp).withBillingAddress(OrderFixture.ADDRESS), KEY));
        assertEquals("Storing a card requires the order details: buyer.ip.", refused.getMessage());
        assertFalse(refused.getMessage().contains(PAN));
        assertNothingSent();
    }

    @Test void storedCardReadsAndDeactivation() {
        transport.enqueue(200, OK).enqueue(200, OK).enqueue(200, OK);
        client.payments().listStoredCards("cuk 1");
        client.payments().getStoredCard("sct/1");
        client.payments().deactivateStoredCard("sct_1", KEY);
        List<RecordingTransport.Call> calls = transport.apiCalls();
        assertEquals("GET", calls.get(0).method);
        assertEquals(BASE + "/cards?cardUserKey=cuk+1", calls.get(0).url);
        assertBearerOnly(calls.get(0));
        assertEquals(BASE + "/cards/sct%2F1", calls.get(1).url);
        assertBearerOnly(calls.get(1));
        assertEquals("DELETE", calls.get(2).method);
        assertEquals(BASE + "/cards/sct_1", calls.get(2).url);
        assertEquals(KEY, calls.get(2).headers.get("Idempotency-Key"));
        assertSigned(calls.get(2));
    }

    @Test void storedCardRulesSendNothing() {
        assertThrows(ConfigurationException.class, () -> client.payments().deactivateStoredCard("sct_1", null));
        assertThrows(ConfigurationException.class, () -> client.payments().deactivateStoredCard("", KEY));
        assertThrows(ConfigurationException.class, () -> client.payments().getStoredCard(" "));
        assertThrows(ConfigurationException.class, () -> client.payments().listStoredCards(null));
        assertNothingSent();
    }

    // ── Marketplace intent ────────────────────────────────────────────────────────────────────────────────────

    static Map<String, Object> marketplace(String... sellerAmounts) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", "10000");
        body.put("currency", "TRY");
        body.put("orderId", "order-1");
        List<Map<String, Object>> sellers = new ArrayList<>();
        for (int i = 0; i < sellerAmounts.length; i++) {
            Map<String, Object> seller = new LinkedHashMap<>();
            seller.put("subDealerId", "seller-" + (i + 1));
            seller.put("amountMinor", sellerAmounts[i]);
            sellers.add(seller);
        }
        body.put("sellers", sellers);
        body.put("multiSellerPolicy", "ALL_OR_NOTHING");
        return OrderFixture.withOrderDetails(body, "10000");
    }

    @Test void marketplaceIntentIsSignedAndSentAsGiven() {
        transport.enqueue(200, OK).enqueue(200, OK);
        client.payments().createMarketplaceIntent(marketplace("6000", "4000"), KEY);
        RecordingTransport.Call call = transport.apiCalls().get(0);
        assertEquals("POST", call.method);
        assertEquals(BASE + "/marketplace/intents", call.url);
        assertEquals(KEY, call.headers.get("Idempotency-Key"));
        assertSigned(call);
        assertEquals("{\"amount\":\"10000\",\"currency\":\"TRY\",\"orderId\":\"order-1\",\"sellers\":["
                + "{\"subDealerId\":\"seller-1\",\"amountMinor\":\"6000\"},{\"subDealerId\":\"seller-2\",\"amountMinor\":\"4000\"}],"
                + "\"multiSellerPolicy\":\"ALL_OR_NOTHING\"," + OrderFixture.json("10000") + "}", call.body);

        client.payments().createMarketplaceIntent(marketplace("10000"));
        RecordingTransport.Call withoutKey = transport.apiCalls().get(1);
        assertNull(withoutKey.headers.get("Idempotency-Key"));
        assertSigned(withoutKey);
    }

    @Test void marketplaceSellerTotalMustEqualTheAmount() {
        ConfigurationException mismatch = assertThrows(ConfigurationException.class,
                () -> client.payments().createMarketplaceIntent(marketplace("6000", "3000"), KEY));
        assertTrue(mismatch.getMessage().contains("9000"), mismatch.getMessage());
        assertThrows(ConfigurationException.class, () -> client.payments().createMarketplaceIntent(marketplace(), KEY));
        assertThrows(ConfigurationException.class, () -> client.payments().createMarketplaceIntent(
                marketplace("10000.00"), KEY), "decimal amountMinor");

        Map<String, Object> numeric = marketplace("10000");
        @SuppressWarnings("unchecked")
        Map<String, Object> seller = ((List<Map<String, Object>>) numeric.get("sellers")).get(0);
        seller.put("amountMinor", 10000);
        assertThrows(ConfigurationException.class, () -> client.payments().createMarketplaceIntent(numeric, KEY));

        Map<String, Object> withPrice = marketplace("10000");
        withPrice.put("price", "10000");
        assertThrows(ConfigurationException.class, () -> client.payments().createMarketplaceIntent(withPrice, KEY));
        Map<String, Object> noSubDealer = marketplace("10000");
        @SuppressWarnings("unchecked")
        Map<String, Object> first = ((List<Map<String, Object>>) noSubDealer.get("sellers")).get(0);
        first.remove("subDealerId");
        assertThrows(ConfigurationException.class, () -> client.payments().createMarketplaceIntent(noSubDealer, KEY));
        assertThrows(ConfigurationException.class, () -> client.payments().createMarketplaceIntent(
                marketplace("10000"), "short"), "malformed key");
        Map<String, Object> noBasket = marketplace("10000");
        noBasket.remove("basketItems");
        ConfigurationException refused = assertThrows(ConfigurationException.class,
                () -> client.payments().createMarketplaceIntent(noBasket, KEY));
        assertEquals("A marketplace payment intent requires the order details: basketItems.", refused.getMessage());
        assertNothingSent();
    }

    // ── Analytics and the payment list ────────────────────────────────────────────────────────────────────────

    @Test void analyticsSendTheFiltersAsQuery() {
        transport.enqueue(200, OK).enqueue(200, OK);
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("from", Instant.parse("2026-11-01T00:00:00Z"));
        filters.put("range", "30d");
        filters.put("empty", null);
        client.payments().failoverRecoveryAnalytics(filters);
        client.payments().providerCredentialAnalytics("cred 1", Map.of("limit", 10));
        List<RecordingTransport.Call> calls = transport.apiCalls();
        assertEquals(BASE + "/analytics/failover-recovery?from=2026-11-01T00%3A00%3A00Z&range=30d", calls.get(0).url);
        assertBearerOnly(calls.get(0));
        assertEquals(BASE + "/analytics/provider-credentials/cred%201?limit=10", calls.get(1).url);
        assertThrows(ConfigurationException.class, () -> client.payments().providerCredentialAnalytics("", Map.of()));
        assertThrows(ConfigurationException.class, () -> client.payments().failoverRecoveryAnalytics(
                Map.of("range", List.of("a"))));
        assertEquals(2, transport.apiCalls().size());
    }

    @Test void paymentListFilters() {
        transport.enqueue(200, OK).enqueue(200, OK);
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("includeLegs", true);
        filters.put("status", "CAPTURED");
        filters.put("limit", 20);
        client.payments().list(filters);
        client.payments().list();
        List<RecordingTransport.Call> calls = transport.apiCalls();
        assertEquals(BASE + "?limit=20&status=CAPTURED&includeLegs=true", calls.get(0).url);
        assertBearerOnly(calls.get(0));
        assertEquals(BASE, calls.get(1).url);
        assertThrows(ConfigurationException.class, () -> client.payments().list(Map.of("includeLegs", "true")));
        assertThrows(ConfigurationException.class, () -> client.payments().list(Map.of("orderId", "o-1")));
        assertEquals(2, transport.apiCalls().size());
    }
}
