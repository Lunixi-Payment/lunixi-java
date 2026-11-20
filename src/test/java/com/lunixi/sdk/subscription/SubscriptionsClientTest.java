package com.lunixi.sdk.subscription;

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
 * Subscriptions: route table, keys in header and body, sub-client routes, cursor pages, money checks and local
 * refusals. Contract source: the PHP SDK's subscription clients, as the Node SDK carries them. Fixture names are
 * fictional.
 */
class SubscriptionsClientTest {

    static final String GATEWAY = "https://gateway.invalid";
    static final String BASE = GATEWAY + "/api/v1/subscriptions";
    static final String OK = "{\"success\":true,\"data\":{\"id\":\"sub_1\",\"status\":\"ACTIVE\"}}";

    RecordingTransport transport;
    SubscriptionsClient subscriptions;

    @BeforeEach
    void setUp() {
        transport = new RecordingTransport("/api/v1/auth/token", "fixture-only");
        subscriptions = LunixiClient.create(LunixiConfig.builder().baseUrl(GATEWAY).keyId("test-key")
                .privateKey(Ed25519KeyPair.generate().privateKeyPem()).transport(transport).maxRetries(0).build())
                .subscriptions();
    }

    static Map<String, Object> body(RecordingTransport.Call call) {
        return call.body == null ? null : Json.parseObject(call.body);
    }

    static Map<String, Object> map(Object... pairs) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            out.put((String) pairs[i], pairs[i + 1]);
        }
        return out;
    }

    void assertNothingSent() {
        assertTrue(transport.apiCalls().isEmpty(), "no request may be sent");
    }

    static void assertBearerOnly(RecordingTransport.Call call) {
        assertNull(call.headers.get(CanonicalRequest.HEADER_SIGNATURE), call.url);
        assertEquals("Bearer fixture-only", call.headers.get("Authorization"));
    }

    @Test
    void everySubClientIsReachable() {
        assertNotNull(subscriptions.plans());
        assertNotNull(subscriptions.customers());
        assertNotNull(subscriptions.cards());
        assertNotNull(subscriptions.invoices());
        assertNotNull(subscriptions.portal());
        assertNotNull(subscriptions.products());
        assertNotNull(subscriptions.features());
        assertNotNull(subscriptions.addons());
        assertNotNull(subscriptions.usage());
    }

    @Test
    void theRouteTableIsBearerOnlyAndKeepsTheApiPaths() {
        assertEquals(63, SubscriptionRoutes.ROUTES.size());
        SubscriptionRoutes.ROUTES.forEach((name, route) -> {
            assertFalse(route.stepUp(), name);
            assertTrue(route.path().matches("^/api/(v1|panel/v1)/subscriptions/.*"), name);
        });
        assertEquals(Route.KeyRule.REQUIRED, SubscriptionRoutes.route("create").keyRule());
        assertEquals(Route.KeyRule.OPTIONAL, SubscriptionRoutes.route("plans.create").keyRule());
        assertEquals("/api/panel/v1/subscriptions/portal/links",
                SubscriptionRoutes.route("portal.createPortalLink").path());
        assertTrue(SubscriptionRoutes.route("features.list").anyQuery());
        assertThrows(IllegalArgumentException.class, () -> SubscriptionRoutes.route("nope"));
    }

    @Test
    void valuesAndHelpers() {
        assertEquals("PAST_DUE", SubscriptionValues.Status.PAST_DUE);
        assertEquals("subscription.trial_will_end", SubscriptionEvents.TRIAL_WILL_END);
        assertEquals("invoice.payment_failed", SubscriptionEvents.INVOICE_PAYMENT_FAILED);
        assertEquals(23, SubscriptionEvents.ALL.size());
        assertTrue(SubscriptionValues.isActive(Map.of("status", "PAST_DUE")));
        assertFalse(SubscriptionValues.isActive("PAUSED"));
        assertTrue(SubscriptionValues.isTerminal("CANCELED"));
        assertEquals("https://portal.lunixi.com/?token=cp_xyz", SubscriptionValues.portalUrl(Map.of("token", "cp_xyz")));
        assertEquals("https://portal.example/?token=a%20b", SubscriptionValues.portalUrl("a b", "https://portal.example/"));
    }

    @Test
    void createSendsTheKeyInTheHeaderAndTheBodyBearerOnly() {
        transport.enqueue(201, "{\"success\":true,\"data\":{\"id\":\"sub_1\",\"status\":\"ACTIVE\","
                + "\"priceSnapshotAmount\":\"9900\",\"priceSnapshotCurrency\":\"TRY\"}}");
        Map<String, Object> sub = subscriptions.create(map("customerId", "cus_1", "planId", "plan_1",
                "paymentCardId", "card_1", "collectionMethod", SubscriptionValues.CollectionMethod.AUTO_CHARGE),
                "idem-sub-1");
        assertEquals("sub_1", sub.get("id"));
        assertEquals("9900", sub.get("priceSnapshotAmount"));
        RecordingTransport.Call call = transport.onlyApiCall();
        assertEquals("POST", call.method);
        assertEquals(BASE + "/subscriptions", call.url);
        assertEquals("idem-sub-1", call.headers.get("Idempotency-Key"));
        assertBearerOnly(call);
        assertEquals(map("customerId", "cus_1", "planId", "plan_1", "paymentCardId", "card_1",
                "collectionMethod", "AUTO_CHARGE", "idempotencyKey", "idem-sub-1"), body(call));
    }

    @Test
    void createTakesTheKeyFromTheRequest() {
        transport.enqueue(201, OK);
        subscriptions.create(map("customerId", "cus_1", "planId", "plan_1", "idempotencyKey", "idem-own"));
        assertEquals("idem-own", transport.onlyApiCall().headers.get("Idempotency-Key"));
    }

    @Test
    void createRefusesAMissingKeyAnUnknownFieldAndAnUnknownCollectionMethodBeforeSending() {
        assertThrows(ConfigurationException.class,
                () -> subscriptions.create(map("customerId", "cus_1", "planId", "plan_1")));
        ConfigurationException unknown = assertThrows(ConfigurationException.class,
                () -> subscriptions.create(map("customerId", "cus_1", "planId", "plan_1", "amount", "1"), "k1"));
        assertTrue(unknown.getMessage().contains("does not accept amount"), unknown.getMessage());
        assertThrows(ConfigurationException.class, () -> subscriptions.create(map("customerId", "cus_1",
                "planId", "plan_1", "collectionMethod", "CASH"), "k1"));
        ConfigurationException differ = assertThrows(ConfigurationException.class, () -> subscriptions.create(
                map("customerId", "cus_1", "planId", "plan_1", "idempotencyKey", "a"), "b"));
        assertTrue(differ.getMessage().contains("differ"), differ.getMessage());
        assertThrows(ConfigurationException.class, () -> subscriptions.create(map("customerId", "cus_1",
                "planId", "plan_1", "skipTrial", "yes"), "k1"));
        assertThrows(ConfigurationException.class, () -> subscriptions.get(" "));
        assertNothingSent();
    }

    @Test
    void cancelPauseResumeAndChangePlan() {
        for (int i = 0; i < 5; i++) {
            transport.enqueue(200, OK);
        }
        subscriptions.cancel("sub_1", true, "too expensive");
        subscriptions.pause("sub_1");
        subscriptions.pause("sub_1", "2026-12-01T00:00:00Z");
        subscriptions.resume("sub_1");
        subscriptions.changePlan("sub_1", map("newPlanId", "plan_2", "behavior", "next_period"), "idem-cp-1");
        List<RecordingTransport.Call> calls = transport.apiCalls();
        assertEquals(BASE + "/subscriptions/sub_1/cancel", calls.get(0).url);
        assertEquals(map("cancelAtPeriodEnd", true, "reason", "too expensive"), body(calls.get(0)));
        assertNull(calls.get(1).body);
        assertEquals(map("resumeAt", "2026-12-01T00:00:00Z"), body(calls.get(2)));
        assertEquals(BASE + "/subscriptions/sub_1/resume", calls.get(3).url);
        assertNull(calls.get(3).body);
        assertEquals(BASE + "/subscriptions/sub_1/change-plan", calls.get(4).url);
        assertEquals("idem-cp-1", calls.get(4).headers.get("Idempotency-Key"));
        assertEquals(map("newPlanId", "plan_2", "behavior", "NEXT_PERIOD", "idempotencyKey", "idem-cp-1"),
                body(calls.get(4)));
        assertThrows(ConfigurationException.class,
                () -> subscriptions.changePlan("sub_1", map("newPlanId", "plan_2", "behavior", "LATER")));
        assertThrows(ConfigurationException.class, () -> subscriptions.resume(null));
        assertEquals(5, transport.apiCalls().size());
    }

    @Test
    void planListsReadTheCursorEnvelopeAndSendBooleansAsText() {
        transport.enqueue(200, "{\"success\":true,\"data\":[{\"id\":\"plan_1\",\"name\":\"Pro\",\"price\":\"9900\","
                + "\"currency\":\"TRY\"}],\"nextPageToken\":\"tok_next\",\"pageSize\":20}");
        SubscriptionPage page = subscriptions.plans().list(map("includeArchived", false, "productId", "prod_1"));
        assertEquals(1, page.items().size());
        assertEquals("9900", page.items().get(0).get("price"));
        assertEquals("tok_next", page.nextPageToken().orElseThrow());
        assertTrue(page.hasMore());
        assertEquals(20, page.pageSize());
        String url = transport.onlyApiCall().url;
        assertTrue(url.contains("includeArchived=false"), url);
        assertTrue(url.contains("productId=prod_1"), url);
        ConfigurationException unknown = assertThrows(ConfigurationException.class,
                () -> subscriptions.plans().list(map("archived", true)));
        assertTrue(unknown.getMessage().contains("does not accept the filter"), unknown.getMessage());
        assertThrows(ConfigurationException.class,
                () -> subscriptions.list(map("status", List.of("ACTIVE", "PAUSED"))));
        assertEquals(1, transport.apiCalls().size());
    }

    @Test
    void theLastPageHasNoToken() {
        transport.enqueue(200, "{\"success\":true,\"data\":[{\"id\":\"inv_1\",\"totalAmount\":\"0\"}]}");
        SubscriptionPage page = subscriptions.invoices().list(map("subscriptionId", "sub_1"));
        assertTrue(page.nextPageToken().isEmpty());
        assertFalse(page.hasMore());
        assertEquals(1, page.pageSize());
    }

    @Test
    void plansCreateChecksPriceCurrencyAndTheBillingRule() {
        transport.enqueue(201, "{\"success\":true,\"data\":{\"id\":\"plan_1\",\"price\":\"0\"}}");
        Map<String, Object> plan = map("productId", "prod_1", "code", "free", "name", "Free", "price", "0",
                "currency", "try", "billing", map("frequency", "monthly"));
        subscriptions.plans().create(plan, "idem-plan-1");
        RecordingTransport.Call call = transport.onlyApiCall();
        assertEquals(BASE + "/plans", call.url);
        assertEquals("idem-plan-1", call.headers.get("Idempotency-Key"));
        assertEquals(map("frequency", "MONTHLY", "interval", 1L), body(call).get("billing"));
        assertEquals("TRY", body(call).get("currency"));
        assertEquals("0", body(call).get("price"));

        assertThrows(ConfigurationException.class, () -> subscriptions.plans().create(with(plan, "price", 9900)));
        assertThrows(ConfigurationException.class, () -> subscriptions.plans().create(with(plan, "price", "99.00")));
        assertThrows(ConfigurationException.class, () -> subscriptions.plans().create(with(plan, "price", null)));
        assertThrows(ConfigurationException.class, () -> subscriptions.plans().create(with(plan, "currency", "XXX")));
        assertThrows(ConfigurationException.class, () -> subscriptions.plans().create(with(plan, "billing",
                map("frequency", "HOURLY"))));
        assertThrows(ConfigurationException.class, () -> subscriptions.plans().create(with(plan, "billing",
                map("frequency", "DAILY", "interval", 0))));
        assertThrows(ConfigurationException.class, () -> subscriptions.plans().create(with(plan, "billing",
                map("frequency", "DAILY", "byMonthDay", List.of(32)))));
        assertThrows(ConfigurationException.class, () -> subscriptions.plans().create(with(plan, "billing",
                map("frequency", "DAILY", "every", 2))));
        assertThrows(ConfigurationException.class, () -> subscriptions.plans().create(with(plan,
                "trialPeriodDays", -1)));
        assertEquals(1, transport.apiCalls().size());
    }

    static Map<String, Object> with(Map<String, Object> base, String field, Object value) {
        Map<String, Object> out = new LinkedHashMap<>(base);
        out.put(field, value);
        return out;
    }

    @Test
    void anAnswerWithANumericAmountIsRefused() {
        transport.enqueue(200, "{\"success\":true,\"data\":{\"id\":\"inv_1\",\"totalAmount\":9900}}");
        LunixiApiException error = assertThrows(LunixiApiException.class, () -> subscriptions.invoices().get("inv_1"));
        assertTrue(error.getMessage().contains("totalAmount must be a canonical string"), error.getMessage());
    }

    @Test
    void cardsAddSendsTheTokenAndRefusesCardNumbersWithoutEchoingThem() {
        transport.enqueue(201, "{\"success\":true,\"data\":{\"id\":\"card_1\",\"last4\":\"4242\",\"isDefault\":true}}");
        Map<String, Object> card = subscriptions.cards().add(map("customerId", "cus_1", "paymentMethodToken",
                "tok_abc", "brand", "VISA", "last4", "4242", "expireMonth", 12, "expireYear", 2030, "isDefault", true,
                "supportedCurrencies", List.of("try")));
        assertEquals("4242", card.get("last4"));
        assertEquals("tok_abc", body(transport.onlyApiCall()).get("paymentMethodToken"));
        assertEquals(List.of("TRY"), body(transport.onlyApiCall()).get("supportedCurrencies"));

        ConfigurationException error = assertThrows(ConfigurationException.class, () -> subscriptions.cards().add(
                map("customerId", "cus_1", "paymentMethodToken", "tok_abc", "cardNumber", "4111111111111111")));
        assertTrue(error.getMessage().contains("cardNumber"), error.getMessage());
        assertFalse(error.getMessage().contains("4111"), error.getMessage());
        assertThrows(ConfigurationException.class, () -> subscriptions.cards().add(map("customerId", "cus_1",
                "paymentMethodToken", "tok_abc", "verificationMode", "ALWAYS")));
        assertEquals(1, transport.apiCalls().size());
    }

    @Test
    void cardAndInvoiceOperations() {
        for (int i = 0; i < 6; i++) {
            transport.enqueue(200, "{\"success\":true,\"data\":[]}");
        }
        subscriptions.cards().list("cus_1", map("pageSize", 10));
        subscriptions.cards().setDefault("card_1");
        subscriptions.cards().deactivate("card_1");
        subscriptions.invoices().list(map("subscriptionId", "sub_1", "status", "OPEN"));
        subscriptions.invoices().voidInvoice("inv_1", "duplicate");
        subscriptions.invoices().retryCharge("inv_1", "idem-retry-1");
        List<RecordingTransport.Call> calls = transport.apiCalls();
        assertEquals(BASE + "/cards?customerId=cus_1&includeInactive=false&pageSize=10", calls.get(0).url);
        assertEquals(BASE + "/cards/card_1/set-default", calls.get(1).url);
        assertEquals("DELETE", calls.get(2).method);
        assertNull(calls.get(2).body);
        assertTrue(calls.get(3).url.endsWith("?subscriptionId=sub_1&status=OPEN"), calls.get(3).url);
        assertEquals(BASE + "/invoices/inv_1/void", calls.get(4).url);
        assertEquals(map("reason", "duplicate"), body(calls.get(4)));
        assertEquals(BASE + "/invoices/inv_1/retry-charge", calls.get(5).url);
        assertEquals("idem-retry-1", calls.get(5).headers.get("Idempotency-Key"));
        calls.forEach(SubscriptionsClientTest::assertBearerOnly);
    }

    @Test
    void customersUpsertByExternalIdAndLookUpByExternalId() {
        for (int i = 0; i < 3; i++) {
            transport.enqueue(200, "{\"success\":true,\"data\":{\"id\":\"cus_1\",\"externalId\":\"u-42\"}}");
        }
        // Renewals take the buyer from this record: identity and billing details are required.
        assertThrows(ConfigurationException.class,
                () -> subscriptions.customers().upsert(map("externalId", "u-42", "email", "ada@example.com"), "idem-cus-0"));
        subscriptions.customers().upsert(map("externalId", "u-42", "name", "Ada", "surname", "Yilmaz",
                "email", "ada@example.com", "gsmNumber", "+905350000000", "identityNumber", "11111111111",
                "billingAddress", "Example Street 1", "billingCity", "Istanbul", "billingCountry", "Turkey",
                "billingZipCode", "34000"), "idem-cus-1");
        subscriptions.customers().getByExternalId("u-42");
        subscriptions.customers().adjustCredit("cus_1", map("amountMinor", "500", "currency", "TRY", "type",
                "GRANT"));
        List<RecordingTransport.Call> calls = transport.apiCalls();
        assertEquals("PUT", calls.get(0).method);
        assertEquals(BASE + "/customers", calls.get(0).url);
        assertEquals("idem-cus-1", calls.get(0).headers.get("Idempotency-Key"));
        assertEquals(BASE + "/customers/by-external/u-42", calls.get(1).url);
        assertEquals(BASE + "/customers/cus_1/credit-balance/adjust", calls.get(2).url);
        ConfigurationException error = assertThrows(ConfigurationException.class,
                () -> subscriptions.customers().adjustCredit("cus_1", map("amountMinor", 5)));
        assertTrue(error.getMessage().contains("amountMinor"), error.getMessage());
        assertThrows(ConfigurationException.class, () -> subscriptions.customers().upsert(map("email", "x@y.z")));
        assertThrows(ConfigurationException.class, () -> subscriptions.customers().archive(null));
        assertEquals(3, transport.apiCalls().size());
    }

    @Test
    void portalSdkSessionReadsTheProofAtTheEnvelopeRoot() {
        transport.enqueue(200, "{\"success\":true,\"code\":\"OK\",\"portalToken\":\"cp_abc\",\"sessionProof\":"
                + "\"proof_xyz\",\"nonce\":\"nonce_1\",\"expiresAt\":\"2026-06-16T03:00:00Z\"}");
        Map<String, Object> session = subscriptions.portal().createSdkSession("cus_1", "https://shop.example",
                SubscriptionValues.PortalSdkMode.HYBRID, null);
        assertEquals("cp_abc", session.get("portalToken"));
        assertEquals("proof_xyz", session.get("sessionProof"));
        assertEquals("nonce_1", session.get("proofNonce"));
        assertEquals("2026-06-16T03:00:00Z", session.get("proofExpiresAt"));
        RecordingTransport.Call call = transport.onlyApiCall();
        assertEquals(BASE + "/portal/sdk-session", call.url);
        assertEquals(map("customerId", "cus_1", "origin", "https://shop.example", "sdkMode", "HYBRID"), body(call));
        assertThrows(ConfigurationException.class, () -> subscriptions.portal().createSdkSession("cus_1",
                "https://shop.example", "EMBED", null));
        assertThrows(ConfigurationException.class, () -> subscriptions.portal().createSdkSession("cus_1", " "));
        assertEquals(1, transport.apiCalls().size());
    }

    @Test
    void portalLinksUseThePanelRouteAndGiveTheHostedUrl() {
        transport.enqueue(201, "{\"success\":true,\"data\":{\"id\":\"lnk_1\",\"customerId\":\"cus_1\",\"token\":"
                + "\"cp_xyz\",\"status\":\"ACTIVE\"}}");
        transport.enqueue(200, "{\"success\":true,\"data\":{\"id\":\"lnk_1\",\"status\":\"REVOKED\"}}");
        transport.enqueue(200, "{\"success\":true,\"data\":{\"items\":[{\"id\":\"lnk_1\"}],\"pageInfo\":"
                + "{\"hasMore\":true,\"nextCursor\":\"c2\"}}}");
        Map<String, Object> link = subscriptions.portal().createPortalLink("cus_1",
                map("allowedPlanIds", List.of("plan_1")));
        assertEquals("https://portal.lunixi.com/?token=cp_xyz", SubscriptionValues.portalUrl(link));
        subscriptions.portal().revokeLink("lnk_1");
        RoutePage links = subscriptions.portal().listLinks(map("customerId", "cus_1", "limit", 1));
        List<RecordingTransport.Call> calls = transport.apiCalls();
        assertEquals(GATEWAY + "/api/panel/v1/subscriptions/portal/links", calls.get(0).url);
        assertEquals(map("allowedPlanIds", List.of("plan_1"), "customerId", "cus_1"), body(calls.get(0)));
        assertEquals("DELETE", calls.get(1).method);
        assertNull(calls.get(1).body);
        assertEquals(GATEWAY + "/api/panel/v1/subscriptions/portal/links?customerId=cus_1&limit=1", calls.get(2).url);
        assertEquals("c2", links.nextCursor().orElseThrow());
        assertTrue(links.hasMore());
    }

    @Test
    void catalogFeatureAddonAndUsageRoutes() {
        for (int i = 0; i < 7; i++) {
            transport.enqueue(200, "{\"success\":true,\"data\":{\"items\":[],\"pageInfo\":{\"hasMore\":false,"
                    + "\"nextCursor\":null}}}");
        }
        RoutePage products = subscriptions.products().list(map("limit", 5, "active", true));
        subscriptions.products().upsertTranslation("prod_1", "en", map("name", "Pro"));
        subscriptions.features().list(map("productId", "prod_1", "anyFilter", "x"));
        subscriptions.features().delete("feat_1");
        subscriptions.addons().deactivate("addon_1");
        subscriptions.usage().record(map("subscriptionId", "sub_1", "featureKey", "api_calls", "quantity", 3));
        subscriptions.usage().checkEntitlement(map("subscriptionId", "sub_1", "featureKey", "api_calls"));
        List<RecordingTransport.Call> calls = transport.apiCalls();
        assertEquals(BASE + "/products?limit=5&active=true", calls.get(0).url);
        assertTrue(products.nextCursor().isEmpty());
        assertFalse(products.hasMore());
        assertEquals("PATCH", calls.get(1).method);
        assertEquals(BASE + "/products/prod_1/translations/en", calls.get(1).url);
        assertEquals(BASE + "/features?productId=prod_1&anyFilter=x", calls.get(2).url);
        assertEquals("DELETE", calls.get(3).method);
        assertEquals(BASE + "/features/feat_1", calls.get(3).url);
        assertEquals(BASE + "/addons/addon_1", calls.get(4).url);
        assertEquals(BASE + "/usage/record", calls.get(5).url);
        assertEquals(map("subscriptionId", "sub_1", "featureKey", "api_calls", "quantity", 3L), body(calls.get(5)));
        assertEquals(BASE + "/usage/check-entitlement", calls.get(6).url);
        assertThrows(ConfigurationException.class, () -> subscriptions.products().create(null));
        assertThrows(ConfigurationException.class, () -> subscriptions.products().list(map("search", "x")));
        assertEquals(7, transport.apiCalls().size());
    }
}
