package com.lunixi.sdk.insurance;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
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
import com.lunixi.sdk.exception.WebhookVerificationException;
import com.lunixi.sdk.json.Json;
import com.lunixi.sdk.support.RecordingTransport;
import com.lunixi.sdk.support.SignedWebhooks;

/** Insurance I1–I22: signing, keys, basket source, bodies, binary files, export trailer, helpers and webhooks. */
class InsuranceClientTest {

    static final String GATEWAY = "https://gateway.invalid";
    static final String BASE = GATEWAY + "/api/v1/insurance";
    static final String ORDER = "{\"status\":\"success\",\"data\":{\"orderId\":\"inso_1\",\"premiumAmount\":\"4900\"}}";
    static final String KEY = "ins-key-0001";
    static final String PAYMENT = "3f2504e0-4f89-11d3-9a0c-0305e82c3301";

    RecordingTransport transport;
    InsuranceClient insurance;

    @BeforeEach
    void setUp() {
        transport = new RecordingTransport("/api/v1/auth/token", "fixture-only");
        insurance = LunixiClient.create(LunixiConfig.builder().baseUrl(GATEWAY).keyId("test-key")
                .privateKey(Ed25519KeyPair.generate().privateKeyPem()).transport(transport).maxRetries(0).build())
                .insurance();
    }

    static Map<String, Object> map(Object... pairs) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            out.put((String) pairs[i], pairs[i + 1]);
        }
        return out;
    }

    static void assertSigned(RecordingTransport.Call call) {
        assertNotNull(call.headers.get(CanonicalRequest.HEADER_SIGNATURE), call.url);
    }

    void assertNothingSent() {
        assertTrue(transport.apiCalls().isEmpty(), "no request may be sent");
    }

    static Map<String, Object> item() {
        return map("id", "item-1", "name", "Bus ticket", "category1", "Travel", "itemType", "VIRTUAL", "price",
                "45000", "insuranceCategory", "TICKET");
    }

    // ── Products, reference codes, quotes ─────────────────────────────────────────────────────────────────────

    @Test
    void listProductsNeedsTheCurrencyAndIsSigned() {
        assertThrows(ConfigurationException.class, () -> insurance.listProducts(map("paymentIntentId", PAYMENT)));
        assertThrows(ConfigurationException.class, () -> insurance.listProducts(map("currency", "TRY",
                "merchantOrderId", "o1")));
        assertThrows(ConfigurationException.class, () -> insurance.listProducts(map("currency", "TRY",
                "paymentIntentId", "not-a-uuid")));
        assertNothingSent();
        transport.enqueue(200, "{\"status\":\"success\",\"data\":{\"products\":[]}}");
        insurance.listProducts(map("currency", "try", "paymentIntentId", PAYMENT));
        RecordingTransport.Call call = transport.onlyApiCall();
        assertEquals(BASE + "/products?currency=TRY&paymentIntentId=" + PAYMENT, call.url);
        assertSigned(call);
    }

    @Test
    void searchProductsAndReferenceValues() {
        transport.enqueue(200, "{\"status\":\"success\",\"data\":{\"products\":[]}}");
        transport.enqueue(200, "{\"status\":\"success\",\"data\":{\"items\":[{\"code\":\"34\"}]},\"pageInfo\":"
                + "{\"hasMore\":false,\"nextCursor\":null}}");
        insurance.searchProducts(map("currency", "TRY", "context", map("items", List.of(item()))));
        InsurancePage cities = insurance.listReferenceValues(InsuranceValues.ReferenceKind.DISTRICT,
                map("parentCode", "34", "limit", 100));
        assertEquals(1, cities.items().size());
        List<RecordingTransport.Call> calls = transport.apiCalls();
        assertEquals(BASE + "/products", calls.get(0).url);
        assertEquals("POST", calls.get(0).method);
        assertEquals(map("currency", "TRY", "context", map("items", List.of(item()))),
                Json.parseObject(calls.get(0).body));
        assertEquals(BASE + "/reference/DISTRICT?parentCode=34&limit=100", calls.get(1).url);
        assertThrows(ConfigurationException.class, () -> insurance.listReferenceValues("ADDRESS_STREET"));
        assertThrows(ConfigurationException.class, () -> insurance.searchProducts(map("currency", "TRY")));
    }

    @Test
    void aQuoteHasOneBasketSourceAtMost() {
        assertThrows(ConfigurationException.class, () -> insurance.createQuote(map("insuranceType",
                "TICKET_CANCELLATION", "currency", "TRY", "paymentIntentId", PAYMENT, "context",
                map("items", List.of(item())))));
        assertThrows(ConfigurationException.class, () -> insurance.createQuote(map("insuranceType",
                "TICKET_CANCELLATION", "currency", "TRY", "providerCode", "X")));
        assertThrows(ConfigurationException.class, () -> insurance.createQuote(map("insuranceType",
                "TICKET_CANCELLATION", "currency", "TRY", "context", map("items", List.of()))));
        assertThrows(ConfigurationException.class, () -> insurance.createQuote(map("insuranceType",
                "TICKET_CANCELLATION", "currency", "TRY", "context", map("items",
                        Collections.nCopies(101, item())))));
        assertThrows(ConfigurationException.class, () -> insurance.createQuote(map("insuranceType",
                "TICKET_CANCELLATION", "currency", "TRY"), "short"));
        assertNothingSent();

        transport.enqueue(201, "{\"status\":\"success\",\"data\":{\"options\":[],\"excludedOptions\":[]}}");
        transport.enqueue(201, "{\"status\":\"success\",\"data\":{\"options\":[],\"excludedOptions\":[]}}");
        insurance.createQuote(map("insuranceType", "TICKET_CANCELLATION", "currency", "TRY", "paymentIntentId",
                PAYMENT, "merchantOrderId", "", "tickets", List.of(map("basketItemId", "item-1"))), KEY);
        insurance.createQuote(map("insuranceType", "PERSONAL_ACCIDENT", "currency", "TRY", "accident",
                map("startDate", "2026-12-01")));
        List<RecordingTransport.Call> calls = transport.apiCalls();
        assertEquals(BASE + "/quotes", calls.get(0).url);
        assertEquals(KEY, calls.get(0).headers.get("Idempotency-Key"));
        Map<String, Object> first = Json.parseObject(calls.get(0).body);
        assertFalse(first.containsKey("merchantOrderId"), "an empty value is left out");
        assertEquals(PAYMENT, first.get("paymentIntentId"));
        assertNull(calls.get(1).headers.get("Idempotency-Key"), "the quote key is optional");
        assertFalse(Json.parseObject(calls.get(1).body).containsKey("context"), "a quote without a basket");
    }

    // ── Orders ────────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    void createOrderNeedsTheKeyAndSendsTheConsent() {
        Map<String, Object> option = map("quoteId", "insq_1", "terms", map("version", "v3", "sha256", "abc"));
        Map<String, Object> consent = InsuranceClient.consentFromQuote(option, Instant.parse("2026-11-15T10:00:00Z"));
        assertEquals(map("termsVersion", "v3", "termsSha256", "abc", "acceptedAt", "2026-11-15T10:00:00Z"), consent);
        Map<String, Object> request = map("quoteId", "insq_1", "consent", consent);
        assertThrows(ConfigurationException.class, () -> insurance.createOrder(request, null));
        assertThrows(ConfigurationException.class, () -> insurance.createOrder(request, "has space key"));
        assertThrows(ConfigurationException.class, () -> insurance.createOrder(map("quoteId", "insq_1", "consent",
                map("termsVersion", "v3", "termsSha256", "abc", "acceptedAt", "2026-11-15T10:00:00Z", "ip", "x")),
                KEY));
        assertThrows(ConfigurationException.class, () -> InsuranceClient.consentFromQuote(map("quoteId", "q"),
                Instant.now()));
        assertNothingSent();

        transport.enqueue(201, ORDER);
        assertEquals("inso_1", insurance.createOrder(request, KEY).get("orderId"));
        RecordingTransport.Call call = transport.onlyApiCall();
        assertEquals(BASE + "/orders", call.url);
        assertEquals(KEY, call.headers.get("Idempotency-Key"));
        assertSigned(call);
        assertEquals(request, Json.parseObject(call.body));
    }

    @Test
    void orderActionsUseTheirKeyRules() {
        for (int i = 0; i < 6; i++) {
            transport.enqueue(200, ORDER);
        }
        insurance.bindPayment("inso_1", PAYMENT);
        insurance.confirmPayment("inso_1", map("externalPaymentId", "ext-1", "paidAmount", "4900", "paidCurrency",
                "try", "paidAt", "2026-11-15T10:00:00+03:00"), KEY);
        insurance.issuePolicy("inso_1", KEY);
        insurance.cancelOrder("inso_1", KEY);
        insurance.refundPremium("inso_1", KEY);
        insurance.refundPremium("inso_1", map("externalRefund", map("externalRefundId", "r-1", "refundedAt",
                "2026-11-16T10:00:00Z")), KEY);
        List<RecordingTransport.Call> calls = transport.apiCalls();
        assertEquals(BASE + "/orders/inso_1/bind-payment", calls.get(0).url);
        assertNull(calls.get(0).headers.get("Idempotency-Key"));
        assertEquals(map("paymentIntentId", PAYMENT), Json.parseObject(calls.get(0).body));
        assertEquals(map("payment", map("externalPaymentId", "ext-1", "paidAmount", "4900", "paidCurrency", "TRY",
                "paidAt", "2026-11-15T10:00:00+03:00")), Json.parseObject(calls.get(1).body));
        assertEquals(BASE + "/orders/inso_1/issue-policy", calls.get(2).url);
        assertNull(calls.get(2).body);
        assertEquals(BASE + "/orders/inso_1/cancel", calls.get(3).url);
        assertNull(calls.get(3).body);
        assertEquals("{}", calls.get(4).body, "a Lunixi payment's premium refund sends an empty object");
        assertEquals("r-1", ((Map<?, ?>) Json.parseObject(calls.get(5).body).get("externalRefund"))
                .get("externalRefundId"));
        for (RecordingTransport.Call call : calls.subList(1, 6)) {
            assertEquals(KEY, call.headers.get("Idempotency-Key"), call.url);
            assertSigned(call);
        }

        assertThrows(ConfigurationException.class, () -> insurance.issuePolicy("inso_1", null));
        assertThrows(ConfigurationException.class, () -> insurance.cancelOrder("inso_1", ""));
        assertThrows(ConfigurationException.class, () -> insurance.refundPremium("inso_1", null));
        assertThrows(ConfigurationException.class, () -> insurance.bindPayment("inso_1", "pi_1"));
        assertThrows(ConfigurationException.class, () -> insurance.confirmPayment("inso_1",
                map("externalPaymentId", "ext-1", "paidAmount", 4900L, "paidCurrency", "TRY", "paidAt",
                        "2026-11-15T10:00:00Z"), KEY));
        assertThrows(ConfigurationException.class, () -> insurance.refundPremium("inso_1",
                map("externalRefund", map("externalRefundId", "r-1")), KEY));
        assertEquals(6, transport.apiCalls().size());
    }

    // ── Leads ─────────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    void leadsCarryTheirOwnFieldsAndAnUntrimmedNote() {
        assertThrows(ConfigurationException.class, () -> insurance.createLead(map("insuranceType",
                "MOTOR_COMPREHENSIVE"), null));
        assertThrows(ConfigurationException.class, () -> insurance.createLead(map("insuranceType",
                "MOTOR_COMPREHENSIVE", "currency", "TRY"), KEY));
        assertThrows(ConfigurationException.class, () -> insurance.createLead(map("insuranceType",
                "MOTOR_COMPREHENSIVE", "note", "x".repeat(1001)), KEY));
        assertNothingSent();
        transport.enqueue(201, "{\"status\":\"success\",\"data\":{\"leadId\":\"insl_1\",\"status\":\"SUBMITTED\"}}");
        insurance.createLead(map("insuranceType", "CARGO_TRANSIT", "shipment", map("cargoDescription", "Boxes"),
                "note", "  call after 5pm "), KEY);
        RecordingTransport.Call call = transport.onlyApiCall();
        assertEquals(BASE + "/leads", call.url);
        assertEquals("  call after 5pm ", Json.parseObject(call.body).get("note"));
        assertThrows(ConfigurationException.class, () -> insurance.listLeads(map("status", "REJECTED")));
    }

    // ── Files ─────────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    void policyFilesAreBytes() {
        byte[] pdf = "%PDF-1.7 policy".getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < 3; i++) {
            transport.enqueueBinary(200, Map.of("content-type", "application/pdf"), pdf);
        }
        InsuranceDocument document = insurance.getPolicyDocument("insp_1");
        insurance.getPolicyReceipt("insp_1");
        insurance.getPolicyCertificate("insp_1");
        assertEquals("application/pdf", document.contentType());
        assertArrayEquals(pdf, document.bytes());
        List<RecordingTransport.Call> calls = transport.apiCalls();
        assertEquals(BASE + "/policies/insp_1/document", calls.get(0).url);
        assertEquals(BASE + "/policies/insp_1/receipt", calls.get(1).url);
        assertEquals(BASE + "/policies/insp_1/certificate", calls.get(2).url);
        for (RecordingTransport.Call call : calls) {
            assertTrue(call.binary);
            assertEquals("application/pdf, application/json", call.headers.get("Accept"));
            assertSigned(call);
        }
    }

    @Test
    void aFileErrorIsTheJsonEnvelope() {
        transport.enqueue(412, Map.of("content-type", "application/json"), "{\"status\":\"failure\",\"code\":"
                + "\"insurance.document.not_supported\",\"statusCode\":412}");
        LunixiApiException error = assertThrows(LunixiApiException.class, () -> insurance.getPolicyReceipt("insp_1"));
        assertEquals(412, error.statusCode());
        assertEquals("insurance.document.not_supported", error.code().orElseThrow());
    }

    @Test
    void reconciliationExportReadsTheTrailer() {
        assertThrows(ConfigurationException.class, () -> insurance.exportReconciliation(map("from",
                "2026-11-01T00:00:00Z")));
        assertThrows(ConfigurationException.class, () -> insurance.getReconciliationSummary(map("from",
                "2026-11-01T00:00:00Z", "to", "2026-11-15T00:00:00Z", "currency", "LIRA")));
        assertNothingSent();
        String cut = "﻿orderId,policyId\ninso_1,insp_1\n# truncated=true orderCap=20000 nextCursor=abc123\n";
        transport.enqueueBinary(200, Map.of("content-type", "text/csv; charset=utf-8"),
                cut.getBytes(StandardCharsets.UTF_8));
        transport.enqueueBinary(200, Map.of("content-type", "text/csv; charset=utf-8"),
                "orderId\ninso_1\n# export-aborted=true\n".getBytes(StandardCharsets.UTF_8));
        InsuranceReconciliationExport first = insurance.exportReconciliation(map("from",
                Instant.parse("2026-11-01T00:00:00Z"), "to", "2026-11-15T00:00:00Z"));
        assertTrue(first.truncated());
        assertEquals("abc123", first.nextCursor().orElseThrow());
        assertFalse(first.aborted());
        InsuranceReconciliationExport second = insurance.exportReconciliation(map("from", "2026-11-01T00:00:00Z",
                "to", "2026-11-15T00:00:00Z", "cursor", "abc123"));
        assertTrue(second.aborted());
        assertFalse(second.truncated());
        List<RecordingTransport.Call> calls = transport.apiCalls();
        assertEquals(BASE + "/reconciliation/export?from=2026-11-01T00%3A00%3A00Z&to=2026-11-15T00%3A00%3A00Z",
                calls.get(0).url);
        assertEquals("text/csv, application/json", calls.get(0).headers.get("Accept"));
        assertTrue(calls.get(1).url.endsWith("&cursor=abc123"));
    }

    // ── Helpers and webhooks ──────────────────────────────────────────────────────────────────────────────────

    @Test
    void premiumBasketItemOnlyForMerchantCollection() {
        Map<String, Object> order = map("orderId", "inso_1", "premiumCollectedBy", "MERCHANT", "premiumAmount",
                "4900");
        assertEquals(map("id", "inso_1", "price", "4900", "name", "Ticket insurance", "category1", "Insurance",
                "itemType", "VIRTUAL"), InsuranceClient.premiumBasketItem(order, "Ticket insurance", "Insurance"));
        order.put("premiumCollectedBy", "PROVIDER");
        assertThrows(ConfigurationException.class, () -> InsuranceClient.premiumBasketItem(order, "n", "c"));
    }

    @Test
    void readsTheOrderAndLeadWebhooks() {
        InsuranceWebhookData issued = InsuranceWebhookData.fromEvent(SignedWebhooks.verified("evt_i1",
                InsuranceEvents.POLICY_ISSUED, "{\"eventId\":\"evt_i1\",\"eventType\":\"insurance.policy.issued\","
                        + "\"insuranceOrderId\":\"inso_1\",\"status\":\"ACTIVE\",\"premiumAmount\":\"4900\","
                        + "\"currency\":\"TRY\",\"policies\":[{\"policyId\":\"insp_1\",\"documentUrl\":\"\"}],"
                        + "\"secret\":\"dropped\"}"));
        assertEquals("evt_i1", issued.eventId());
        assertEquals("inso_1", issued.insuranceOrderId());
        assertEquals("4900", issued.premiumAmount());
        assertEquals(1, issued.policies().size());
        assertFalse(issued.data().containsKey("secret"));
        assertTrue(issued.errorCode().isEmpty());

        InsuranceLeadWebhookData lead = InsuranceLeadWebhookData.fromEvent(SignedWebhooks.verified("evt_l1",
                InsuranceEvents.LEAD_SUBMITTED, "{\"eventId\":\"evt_l1\",\"lead\":{\"leadId\":\"insl_1\","
                        + "\"status\":\"SUBMITTED\"}}"));
        assertEquals("insl_1", lead.leadId());
        assertEquals("SUBMITTED", lead.status());

        assertThrows(WebhookVerificationException.class, () -> InsuranceWebhookData.fromEvent(SignedWebhooks
                .verified("evt_l2", InsuranceEvents.LEAD_SUBMITTED, "{\"lead\":{}}")));
        assertThrows(WebhookVerificationException.class, () -> InsuranceLeadWebhookData.fromEvent(SignedWebhooks
                .verified("evt_i2", InsuranceEvents.POLICY_FAILED, "{}")));
        List<String> all = new ArrayList<>(InsuranceEvents.ORDER_EVENTS);
        all.addAll(InsuranceEvents.LEAD_EVENTS);
        assertEquals(InsuranceEvents.ALL, all);
    }
}
