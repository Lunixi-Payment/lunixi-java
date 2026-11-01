package com.lunixi.sdk.physicalpos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.lunixi.sdk.LunixiClient;
import com.lunixi.sdk.LunixiConfig;
import com.lunixi.sdk.auth.CanonicalRequest;
import com.lunixi.sdk.auth.Ed25519KeyPair;
import com.lunixi.sdk.exception.ConfigurationException;
import com.lunixi.sdk.exception.LunixiApiException;
import com.lunixi.sdk.exception.PhysicalPosCardDataRejectedException;
import com.lunixi.sdk.exception.PhysicalPosOutcomeUnknownException;
import com.lunixi.sdk.exception.PhysicalPosRateLimitException;
import com.lunixi.sdk.exception.PhysicalPosVoidAfterSettlementException;
import com.lunixi.sdk.exception.ValidationException;
import com.lunixi.sdk.exception.WebhookVerificationException;
import com.lunixi.sdk.http.HttpResult;
import com.lunixi.sdk.http.HttpTransport;
import com.lunixi.sdk.json.Json;
import com.lunixi.sdk.support.RecordingTransport;
import com.lunixi.sdk.webhook.WebhookEvent;
import com.lunixi.sdk.webhook.WebhookVerifier;

/*
 * Contract source: nest-payment-gateway apps/api-gateway/src/physical-pos/payments/** and transactions/**
 * (api controllers, DTOs, mappers), libs/proto/src/physical-pos-card-data.ts, physical-pos-webhook.interface.ts.
 * Ids and values below are synthetic.
 */
class PhysicalPosClientTest {

    static final String BASE = "https://gateway.invalid/api/v1/physical-pos";
    static final String PAYMENT_ID = "0192f3a1-0000-7000-8000-000000000001";
    static final String DEVICE_ID = "5b0c6a1e-2f8d-4c55-9a51-3f0e8d1c2a90";
    static final String KEY = "7b0c6a1e-2f8d-4c55-9a51-3f0e8d1c2a90";

    RecordingTransport transport;
    LunixiClient client;
    PhysicalPosClient pos;

    @BeforeEach
    void setup() {
        transport = new RecordingTransport("/api/v1/auth/token", "fixture-only");
        client = LunixiClient.create(LunixiConfig.builder().baseUrl("https://gateway.invalid").keyId("test-key")
                .privateKey(Ed25519KeyPair.generate().privateKeyPem()).transport(transport).maxRetries(0).build());
        pos = client.physicalPos();
    }

    static String payment(String status) {
        return "{\"paymentId\":\"" + PAYMENT_ID + "\",\"rootPaymentId\":\"" + PAYMENT_ID + "\",\"originalPaymentId\":null,"
                + "\"origin\":\"API\",\"operation\":\"SALE\",\"paymentStatus\":\"" + status + "\",\"statusReason\":null,"
                + "\"outcomeConflict\":false,\"resolvable\":false,\"deviceId\":\"" + DEVICE_ID + "\","
                + "\"amountMinor\":\"4500\",\"approvedAmountMinor\":null,\"currency\":\"TRY\",\"installmentCount\":null,"
                + "\"historySeq\":1,\"rowVersion\":1,\"tender\":null,\"items\":[],\"refundItems\":[]}";
    }

    static Map<String, Object> sale() {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("lineNo", 1);
        item.put("name", "Çay");
        item.put("quantityMilli", 2000);
        item.put("unitCode", "C62");
        item.put("unitPriceMinor", "1500");
        item.put("taxGroupCode", "KDV10");
        item.put("lineTotalMinor", "3000");
        item.put("barcode", "8691234567012");
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("operation", "SALE");
        request.put("target", Map.of("vendorCode", "PAVO", "serialNumber", "PAV00012345"));
        request.put("amountMinor", "3000");
        request.put("currency", "TRY");
        request.put("merchantReference", "ORD-2026-000123");
        request.put("items", List.of(item));
        return request;
    }

    // ── createPayment ────────────────────────────────────────────────────────────────────────────────────────

    @Test
    void createPaymentSignsSendsTheKeyAndReads202() {
        transport.enqueue(202, "{\"status\":\"success\",\"data\":{\"payment\":" + payment("NOT_STARTED")
                + ",\"replayed\":false},\"statusCode\":202}");
        PhysicalPosWriteResult result = pos.createPayment(sale(), KEY);

        RecordingTransport.Call call = transport.onlyApiCall();
        assertEquals("POST", call.method);
        assertEquals(BASE + "/payments", call.url);
        assertEquals(KEY, call.headers.get("Idempotency-Key"));
        assertNotNull(call.headers.get(CanonicalRequest.HEADER_SIGNATURE));
        Map<String, Object> body = Json.parseObject(call.body);
        assertEquals("3000", body.get("amountMinor"));
        assertEquals("PAVO", ((Map<?, ?>) body.get("target")).get("vendorCode"));
        assertFalse(body.containsKey("environment"));

        assertEquals(202, result.statusCode());
        assertTrue(result.accepted());
        assertFalse(result.replayed());
        assertEquals(KEY, result.idempotencyKey());
        assertEquals("NOT_STARTED", result.payment().get("paymentStatus"));
    }

    @Test
    void replayIs200AndFallsBackWhenTheEnvelopeHasNoStatusCode() {
        transport.enqueue(200, "{\"data\":{\"payment\":" + payment("APPROVED") + ",\"replayed\":true}}");
        PhysicalPosWriteResult result = pos.createPayment(sale(), KEY);
        assertTrue(result.replayed());
        assertEquals(200, result.statusCode());
        assertFalse(result.accepted());
    }

    @Test
    void startAndReversalRequireAKeyAndNothingIsSentWithoutOne() {
        ConfigurationException missing = assertThrows(ConfigurationException.class,
                () -> pos.createPayment(sale(), null));
        assertTrue(missing.getMessage().contains("newIdempotencyKey"), missing.getMessage());
        assertThrows(ConfigurationException.class,
                () -> pos.createReversal(PAYMENT_ID, Map.of("operation", "VOID", "currency", "TRY"), null));
        assertThrows(ConfigurationException.class, () -> pos.createPayment(sale(), "short"));
        assertThrows(ConfigurationException.class, () -> pos.createPayment(sale(), "has a space-0001"));
        assertThrows(ConfigurationException.class, () -> pos.createPayment(sale(), "x".repeat(201)));
        assertTrue(transport.calls().isEmpty());

        String generated = PhysicalPosClient.newIdempotencyKey();
        assertTrue(generated.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"), generated);
        assertNotEquals(generated, PhysicalPosClient.newIdempotencyKey());
        transport.enqueue(202, "{\"data\":{\"payment\":" + payment("NOT_STARTED") + ",\"replayed\":false},\"statusCode\":202}");
        assertEquals(generated, pos.createPayment(sale(), generated).idempotencyKey());
        assertEquals(generated, transport.onlyApiCall().headers.get("Idempotency-Key"));
    }

    @Test
    void retriesAServerErrorWithTheSameKey() {
        LunixiClient retrying = LunixiClient.create(LunixiConfig.builder().baseUrl("https://gateway.invalid")
                .keyId("test-key").privateKey(Ed25519KeyPair.generate().privateKeyPem()).transport(transport)
                .maxRetries(1).build());
        transport.enqueue(502, "{\"status\":\"failure\",\"code\":\"BAD_GATEWAY\"}");
        transport.enqueue(202, "{\"data\":{\"payment\":" + payment("NOT_STARTED") + ",\"replayed\":false},\"statusCode\":202}");
        retrying.physicalPos().createPayment(sale(), KEY);
        List<RecordingTransport.Call> calls = transport.apiCalls();
        assertEquals(2, calls.size());
        assertEquals(calls.get(0).headers.get("Idempotency-Key"), calls.get(1).headers.get("Idempotency-Key"));
    }

    @Test
    void refusesUnknownFieldsTypesAndEmptyItemsBeforeSending() {
        Map<String, Object> unknown = sale();
        unknown.put("environment", "LIVE");
        assertThrows(ConfigurationException.class, () -> pos.createPayment(unknown, KEY));

        Map<String, Object> stringAmount = sale();
        stringAmount.put("amountMinor", 3000);
        assertThrows(ConfigurationException.class, () -> pos.createPayment(stringAmount, KEY));

        Map<String, Object> fraction = sale();
        fraction.put("amountMinor", 30.5);
        assertThrows(ConfigurationException.class, () -> pos.createPayment(fraction, KEY));

        Map<String, Object> emptyItems = sale();
        emptyItems.put("items", List.of());
        assertThrows(ConfigurationException.class, () -> pos.createPayment(emptyItems, KEY));

        Map<String, Object> noTarget = sale();
        noTarget.remove("target");
        assertThrows(ConfigurationException.class, () -> pos.createPayment(noTarget, KEY));

        Map<String, Object> badTarget = sale();
        badTarget.put("target", Map.of("terminalId", "x"));
        assertThrows(ConfigurationException.class, () -> pos.createPayment(badTarget, KEY));

        assertTrue(transport.calls().isEmpty());
    }

    // ── Card data ────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    void refusesCardDataKeysAtAnyDepthWithTheFieldPathOnly() {
        Map<String, Object> top = sale();
        top.put("cardNumber", "4543600000001234");
        PhysicalPosCardDataRejectedException rejected = assertThrows(PhysicalPosCardDataRejectedException.class,
                () -> pos.createPayment(top, KEY));
        assertEquals("cardNumber", rejected.field().orElseThrow());
        assertEquals(0, rejected.statusCode());
        assertEquals(PhysicalPosCardDataRejectedException.CODE, rejected.code().orElseThrow());
        assertFalse(rejected.getMessage().contains("4543600000001234"));
        assertTrue(rejected instanceof ValidationException);

        Map<String, Object> nested = sale();
        nested.put("target", Map.of("deviceId", DEVICE_ID, "pan", "4543600000001234"));
        assertEquals("target.pan", assertThrows(PhysicalPosCardDataRejectedException.class,
                () -> pos.createPayment(nested, KEY)).field().orElseThrow());

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("lineNo", 1);
        item.put("quantityMilli", 1000);
        item.put("CVV2", "123");
        Map<String, Object> reversal = new LinkedHashMap<>();
        reversal.put("operation", "REFUND");
        reversal.put("currency", "TRY");
        reversal.put("items", List.of(item));
        assertEquals("items[0].CVV2", assertThrows(PhysicalPosCardDataRejectedException.class,
                () -> pos.createReversal(PAYMENT_ID, reversal, KEY)).field().orElseThrow());

        Map<String, Object> digitKey = sale();
        digitKey.put("4543 6000 0000 1234", "x");
        assertEquals("(body)", assertThrows(PhysicalPosCardDataRejectedException.class,
                () -> pos.createPayment(digitKey, KEY)).field().orElseThrow());

        assertThrows(PhysicalPosCardDataRejectedException.class,
                () -> pos.listPayments(Map.of("track2", "x")));
        assertTrue(transport.calls().isEmpty());
    }

    @Test
    void cardDataKeyRuleMatchesTheGatewayTokenRule() {
        for (String key : new String[]{"pan", "cardNumber", "card_no", "CVVCode", "cvc2", "track2Data", "expiry",
                "expMonth", "pinBlock", "securityCode", "customercardnumber", "vendorTrack2", "PAN", "csc",
                "maskedCvv"}) {
            assertTrue(PhysicalPosSupport.isCardDataKey(key), key);
        }
        for (String key : new String[]{"panel", "company", "span", "japan", "expiresAt", "expectedAmount",
                "trackingNo", "pinKeyKcv", "tokenExpiry", "trackUrl", "maskedPan", "cardNumberMasked",
                "merchantReference", "amountMinor", "paymentId", "taxGroupCode", "unitPriceMinor"}) {
            assertFalse(PhysicalPosSupport.isCardDataKey(key), key);
        }
    }

    @Test
    void serverCardDataRejectionMapsToTheTypedException() {
        transport.enqueue(400, "{\"status\":\"failure\",\"code\":\"physical_pos.payment.card_data_rejected\","
                + "\"details\":{\"field\":\"items[1].name\"},\"statusCode\":400}");
        PhysicalPosCardDataRejectedException rejected = assertThrows(PhysicalPosCardDataRejectedException.class,
                () -> pos.createPayment(sale(), KEY));
        assertEquals(400, rejected.statusCode());
        assertEquals("items[1].name", rejected.field().orElseThrow());
    }

    // ── Typed errors ─────────────────────────────────────────────────────────────────────────────────────────

    @Test
    void rateLimitCarriesRetryAfterFromTheHeaderOrDetails() {
        transport.enqueue(429, Map.of("retry-after", "17"), "{\"status\":\"failure\",\"code\":\"RATE_LIMITED\"}");
        PhysicalPosRateLimitException fromHeader = assertThrows(PhysicalPosRateLimitException.class,
                () -> pos.createPayment(sale(), KEY));
        assertEquals(Duration.ofSeconds(17), fromHeader.retryAfter().orElseThrow());

        transport.enqueue(429, Map.of(), "{\"status\":\"failure\",\"code\":\"RATE_LIMITED\","
                + "\"details\":{\"retryAfterSeconds\":42}}");
        PhysicalPosRateLimitException fromDetails = assertThrows(PhysicalPosRateLimitException.class,
                () -> pos.cancelPayment(PAYMENT_ID, KEY));
        assertEquals(Duration.ofSeconds(42), fromDetails.retryAfter().orElseThrow());
    }

    @Test
    void serverErrorOnAPaymentWriteIsAnUnknownOutcomeWithTheKey() {
        transport.enqueue(504, "{\"status\":\"failure\",\"code\":\"GATEWAY_TIMEOUT\"}");
        PhysicalPosOutcomeUnknownException unknown = assertThrows(PhysicalPosOutcomeUnknownException.class,
                () -> pos.createPayment(sale(), KEY));
        assertEquals(KEY, unknown.idempotencyKey());
        assertEquals(504, unknown.statusCode());
        assertEquals("GATEWAY_TIMEOUT", unknown.code().orElseThrow());
        assertNotNull(unknown.getCause());
    }

    @Test
    void aTypedNotStartedFiveHundredIsADefiniteFailure() {
        transport.enqueue(503, "{\"status\":\"failure\",\"code\":\"physical_pos.service.busy\","
                + "\"details\":{\"retryable\":true}}");
        com.lunixi.sdk.exception.ServiceUnavailableException busy = assertThrows(
                com.lunixi.sdk.exception.ServiceUnavailableException.class, () -> pos.createPayment(sale(), KEY));
        assertEquals(com.lunixi.sdk.exception.ServiceUnavailableException.class, busy.getClass());
        assertEquals("physical_pos.service.busy", busy.code().orElseThrow());
    }

    @Test
    void transportFailureOnAPaymentWriteIsAnUnknownOutcome() {
        HttpTransport failing = new HttpTransport() {
            @Override
            public HttpResult send(String method, String url, Map<String, String> headers, String body,
                                   Duration timeout) {
                if (url.endsWith("/api/v1/auth/token")) {
                    return new HttpResult(200, Map.of(), "{\"status\":\"success\",\"data\":{\"access_token\":"
                            + "\"fixture-only\",\"token_type\":\"Bearer\",\"expires_in\":1800}}");
                }
                throw new LunixiApiException("connection reset", new java.io.IOException("reset"));
            }
        };
        LunixiClient offline = LunixiClient.create(LunixiConfig.builder().baseUrl("https://gateway.invalid")
                .keyId("test-key").privateKey(Ed25519KeyPair.generate().privateKeyPem()).transport(failing)
                .maxRetries(0).build());
        PhysicalPosOutcomeUnknownException unknown = assertThrows(PhysicalPosOutcomeUnknownException.class,
                () -> offline.physicalPos().createReversal(PAYMENT_ID, Map.of("operation", "VOID", "currency", "TRY"),
                        KEY));
        assertEquals(0, unknown.statusCode());
        assertEquals(KEY, unknown.idempotencyKey());

        // A read is not a write: no unknown-outcome wrapping.
        LunixiApiException read = assertThrows(LunixiApiException.class,
                () -> offline.physicalPos().getPayment(PAYMENT_ID));
        assertFalse(read instanceof PhysicalPosOutcomeUnknownException);
    }

    @Test
    void definiteRejectionsPassThrough() {
        transport.enqueue(409, "{\"status\":\"failure\",\"code\":\"physical_pos.payment.device_busy\","
                + "\"details\":{\"reason\":\"payment_in_progress\",\"retryAfterSeconds\":30}}");
        // A 409 is a definite rejection (nothing started): the plain API exception, not an unknown outcome.
        LunixiApiException busy = assertThrows(LunixiApiException.class, () -> pos.createPayment(sale(), KEY));
        assertEquals(LunixiApiException.class, busy.getClass());
        assertEquals(409, busy.statusCode());
        assertEquals("physical_pos.payment.device_busy", busy.code().orElseThrow());
    }

    @Test
    void aVoidOfASettledPaymentIsTyped() {
        transport.enqueue(409, "{\"status\":\"failure\",\"code\":\"physical_pos.payment.void_after_settlement\","
                + "\"details\":{\"allowedOperation\":\"REFUND\"}}");
        PhysicalPosVoidAfterSettlementException settled = assertThrows(PhysicalPosVoidAfterSettlementException.class,
                () -> pos.createReversal(PAYMENT_ID, Map.of("operation", "VOID", "currency", "TRY"), KEY));
        assertFalse(LunixiApiException.class.equals(settled.getClass()));
        assertEquals(409, settled.statusCode());
        assertEquals(PhysicalPosVoidAfterSettlementException.CODE, settled.code().orElseThrow());
        assertEquals("REFUND", settled.allowedOperation().orElseThrow());
        assertEquals(KEY, settled.idempotencyKey().orElseThrow());
        assertEquals(1, transport.apiCalls().size(), "a definite 409 is not retried");

        transport.enqueue(409, "{\"status\":\"failure\",\"code\":\"physical_pos.payment.void_after_settlement\"}");
        PhysicalPosVoidAfterSettlementException bare = assertThrows(PhysicalPosVoidAfterSettlementException.class,
                () -> pos.createReversal(PAYMENT_ID, Map.of("operation", "VOID", "currency", "TRY"), KEY));
        assertTrue(bare.allowedOperation().isEmpty());
    }

    // ── Reversal, cancel, reads ──────────────────────────────────────────────────────────────────────────────

    @Test
    void itemRefundGoesToTheReversalsRoute() {
        transport.enqueue(202, "{\"data\":{\"payment\":" + payment("NOT_STARTED") + ",\"replayed\":false},\"statusCode\":202}");
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("operation", "REFUND");
        request.put("currency", "TRY");
        request.put("items", List.of(Map.of("lineNo", 1, "quantityMilli", 1000)));
        PhysicalPosWriteResult result = pos.createReversal("a/b", request, KEY);
        RecordingTransport.Call call = transport.onlyApiCall();
        assertEquals(BASE + "/payments/a%2Fb/reversals", call.url);
        Map<String, Object> body = Json.parseObject(call.body);
        assertFalse(body.containsKey("amountMinor"));
        assertEquals(202, result.statusCode());
    }

    @Test
    void cancelSendsAnEmptyBodyWithAKeyAndIs200() {
        transport.enqueue(200, "{\"data\":{\"payment\":" + payment("EXPIRED") + ",\"replayed\":false},\"statusCode\":200}");
        PhysicalPosWriteResult result = pos.cancelPayment(PAYMENT_ID);
        RecordingTransport.Call call = transport.onlyApiCall();
        assertTrue(call.headers.get("Idempotency-Key").matches("[0-9a-f-]{36}"));
        assertEquals(BASE + "/payments/" + PAYMENT_ID + "/cancel", call.url);
        assertEquals("{}", call.body);
        assertNotNull(call.headers.get("Idempotency-Key"));
        assertEquals(200, result.statusCode());
    }

    @Test
    void getPaymentUnwrapsAndRefusesBadIds() {
        transport.enqueue(200, "{\"data\":{\"payment\":" + payment("APPROVED") + "}}");
        Map<String, Object> read = pos.getPayment(PAYMENT_ID);
        assertEquals("APPROVED", read.get("paymentStatus"));
        assertNotNull(transport.onlyApiCall().headers.get(CanonicalRequest.HEADER_SIGNATURE));
        assertThrows(ConfigurationException.class, () -> pos.getPayment(" "));
        assertThrows(ConfigurationException.class, () -> pos.getPayment(".."));
    }

    @Test
    void listPaymentsBuildsTheQueryAndReadsTopLevelPageInfo() {
        transport.enqueue(200, "{\"data\":{\"items\":[" + payment("APPROVED") + "],\"pageInfo\":{\"hasMore\":true,"
                + "\"nextCursor\":\"c1\",\"totalCount\":null}},\"pageInfo\":{\"hasMore\":true,\"nextCursor\":\"c1\","
                + "\"totalCount\":null}}");
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("limit", 10);
        filters.put("merchantReference", "ORD-2026-000123");
        filters.put("paymentStatus", null);
        PhysicalPosPage page = pos.listPayments(filters);
        assertEquals(BASE + "/payments?limit=10&merchantReference=ORD-2026-000123", transport.onlyApiCall().url);
        assertEquals(1, page.size());
        assertTrue(page.hasMore());
        assertEquals("c1", page.nextCursor().orElseThrow());
        assertTrue(page.totalCount().isEmpty());

        assertThrows(ConfigurationException.class, () -> pos.listPayments(Map.of("environment", "LIVE")));
        assertThrows(ConfigurationException.class, () -> pos.listPayments(Map.of("limit", 101)));
        assertThrows(ConfigurationException.class, () -> pos.listPayments(Map.of("limit", "10")));
    }

    @Test
    void iteratorFollowsTheCursorAndStopsWithoutOne() {
        transport.enqueue(200, "{\"data\":{\"items\":[{\"root\":{}},{\"root\":{}}]},\"pageInfo\":{\"hasMore\":true,"
                + "\"nextCursor\":\"c2\"}}");
        transport.enqueue(200, "{\"data\":{\"items\":[{\"root\":{}}]},\"pageInfo\":{\"hasMore\":true,"
                + "\"nextCursor\":null}}");
        List<Map<String, Object>> rows = new ArrayList<>();
        pos.iterateTransactions(Map.of("storeId", "s-1")).forEach(rows::add);
        assertEquals(3, rows.size());
        List<RecordingTransport.Call> calls = transport.apiCalls();
        assertEquals(2, calls.size());
        assertEquals(BASE + "/transactions?storeId=s-1", calls.get(0).url);
        assertEquals(BASE + "/transactions?cursor=c2&storeId=s-1", calls.get(1).url);
    }

    @Test
    void transactionsReportsAndSettlementsUseTheirRoutes() {
        transport.enqueue(200, "{\"data\":{\"transaction\":{\"requestedPaymentId\":\"" + PAYMENT_ID + "\"}}}");
        assertEquals(PAYMENT_ID, pos.getTransaction(PAYMENT_ID).get("requestedPaymentId"));
        transport.enqueue(200, "{\"data\":{\"items\":[]},\"pageInfo\":{\"hasMore\":false}}");
        pos.listAmbiguousPayments(Map.of("paymentStatus", "MANUAL_REVIEW"));
        transport.enqueue(200, "{\"data\":{\"environment\":\"LIVE\",\"groupBy\":\"DAY\",\"rows\":[]}}");
        assertEquals("DAY", pos.getSalesSummary(Map.of("groupBy", "DAY", "dateFrom", "2026-10-01")).get("groupBy"));
        transport.enqueue(200, "{\"data\":{\"environment\":\"LIVE\",\"rows\":[],\"amountTotals\":[]}}");
        pos.getTaxSummary(null);
        transport.enqueue(200, "{\"data\":{\"items\":[]},\"pageInfo\":{\"hasMore\":false}}");
        pos.listSettlements(Map.of("lineStatus", "MISMATCH"));
        transport.enqueue(200, "{\"data\":{\"items\":[]},\"pageInfo\":{\"hasMore\":false}}");
        pos.listTransactions(Map.of("amountFromMinor", "100", "origin", "DEVICE_DIRECT"));

        List<RecordingTransport.Call> calls = transport.apiCalls();
        assertEquals(BASE + "/transactions/" + PAYMENT_ID, calls.get(0).url);
        assertEquals(BASE + "/transactions/ambiguous?paymentStatus=MANUAL_REVIEW", calls.get(1).url);
        assertEquals(BASE + "/reports/sales-summary?dateFrom=2026-10-01&groupBy=DAY", calls.get(2).url);
        assertEquals(BASE + "/reports/tax-summary", calls.get(3).url);
        assertEquals(BASE + "/settlements?lineStatus=MISMATCH", calls.get(4).url);
        assertEquals(BASE + "/transactions?amountFromMinor=100&origin=DEVICE_DIRECT", calls.get(5).url);
        for (RecordingTransport.Call call : calls) {
            assertNotNull(call.headers.get(CanonicalRequest.HEADER_SIGNATURE), call.url);
        }
        assertThrows(ConfigurationException.class, () -> pos.getTaxSummary(Map.of("groupBy", "DAY")));
    }

    // ── Webhooks ─────────────────────────────────────────────────────────────────────────────────────────────

    static final String SECRET = "whsec_physical_pos_fixture";

    static Map<String, String> signedHeaders(String id, String type, String body) throws Exception {
        String timestamp = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = HexFormat.of().formatHex(
                mac.doFinal((id + "." + timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
        return Map.of(WebhookVerifier.HEADER_EVENT_ID, id, WebhookVerifier.HEADER_EVENT_TYPE, type,
                WebhookVerifier.HEADER_TIMESTAMP, timestamp, WebhookVerifier.HEADER_SIGNATURE, "v2=" + signature);
    }

    @Test
    void parsesAVerifiedPaymentWebhook() throws Exception {
        String body = "{\"id\":\"evt_1\",\"type\":\"physical_pos.payment.approved\",\"sourceService\":"
                + "\"physical-pos-service\",\"organizationId\":\"org\",\"productCode\":\"PHYSICAL_POS\",\"environment\":"
                + "\"LIVE\",\"eventVersion\":1,\"schemaVersion\":\"v1\",\"createdAt\":\"2026-10-30T09:00:00.000Z\","
                + "\"data\":{\"paymentId\":\"" + PAYMENT_ID + "\",\"paymentStatus\":\"APPROVED\",\"amountMinor\":\"4500\","
                + "\"approvedAmountMinor\":\"4500\",\"historySeq\":4,\"resolvedBy\":null}}";
        Map<String, String> headers = signedHeaders("evt_1", PhysicalPosEvents.PAYMENT_APPROVED, body);
        PhysicalPosWebhookEvent event = PhysicalPosClient.parseWebhookEvent(body, headers, SECRET);
        assertEquals("evt_1", event.id());
        assertEquals(PhysicalPosEvents.PAYMENT_APPROVED, event.type());
        assertEquals(PhysicalPosWebhookEvent.CATEGORY_PAYMENT, event.category());
        assertEquals("LIVE", event.environment().orElseThrow());
        assertEquals("2026-10-30T09:00:00.000Z", event.createdAt().orElseThrow());
        assertEquals(4L, event.historySeq().orElseThrow());
        assertEquals("4500", event.data().get("amountMinor"));
        assertTrue(PhysicalPosEvents.ALL.contains(event.type()));

        // The verifier's own result type is accepted too (it cannot be built outside the verifier).
        WebhookEvent verified = client.webhooks().verify(body, headers, SECRET);
        assertEquals("evt_1", PhysicalPosClient.parseWebhookEvent(verified).id());
    }

    @Test
    void readsADeviceSaleAndItsRefund() throws Exception {
        String saleBody = "{\"id\":\"evt_ds\",\"type\":\"physical_pos.payment.device_sale\",\"data\":{\"paymentId\":\""
                + PAYMENT_ID + "\",\"rootPaymentId\":\"" + PAYMENT_ID + "\",\"original\":null,\"operation\":\"SALE\","
                + "\"paymentStatus\":\"APPROVED\",\"statusReason\":null,\"amountMinor\":\"4500\",\"historySeq\":1,"
                + "\"origin\":\"DEVICE_DIRECT\",\"deviceOccurredAt\":\"2026-11-01T18:42:10.123Z\"}}";
        PhysicalPosWebhookEvent sale = PhysicalPosClient.parseWebhookEvent(saleBody,
                signedHeaders("evt_ds", PhysicalPosEvents.PAYMENT_DEVICE_SALE, saleBody), SECRET);
        assertTrue(sale.isDeviceSale());
        assertEquals(PhysicalPosWebhookEvent.CATEGORY_PAYMENT, sale.category());
        assertTrue(sale.original().isEmpty());
        assertEquals(1L, sale.historySeq().orElseThrow());
        assertEquals("DEVICE_DIRECT", sale.data().get("origin"));
        assertEquals("2026-11-01T18:42:10.123Z", sale.data().get("deviceOccurredAt"));

        String refundBody = "{\"id\":\"evt_rf\",\"type\":\"physical_pos.payment.approved\",\"data\":{\"paymentId\":"
                + "\"child-1\",\"rootPaymentId\":\"" + PAYMENT_ID + "\",\"original\":{\"paymentId\":\"" + PAYMENT_ID
                + "\",\"origin\":\"DEVICE_DIRECT\"},\"operation\":\"REFUND\",\"paymentStatus\":\"APPROVED\","
                + "\"historySeq\":3}}";
        PhysicalPosWebhookEvent refund = PhysicalPosClient.parseWebhookEvent(refundBody,
                signedHeaders("evt_rf", PhysicalPosEvents.PAYMENT_APPROVED, refundBody), SECRET);
        assertFalse(refund.isDeviceSale());
        assertEquals(Map.of("paymentId", PAYMENT_ID, "origin", "DEVICE_DIRECT"), refund.original().orElseThrow());
    }

    @Test
    void readsASignedPayloadJsonEnvelopeAsObjectOrString() throws Exception {
        String objectBody = "{\"eventId\":\"evt_2\",\"eventType\":\"physical_pos.fiscal_document.issued\","
                + "\"occurredAt\":\"2026-10-30T09:00:00.000Z\",\"payloadJson\":{\"documentId\":\"d-1\",\"historySeq\":2}}";
        PhysicalPosWebhookEvent fiscal = PhysicalPosClient.parseWebhookEvent(objectBody,
                signedHeaders("evt_2", "physical_pos.fiscal_document.issued", objectBody), SECRET);
        assertEquals("evt_2", fiscal.id());
        assertEquals(PhysicalPosWebhookEvent.CATEGORY_FISCAL_DOCUMENT, fiscal.category());
        assertEquals("d-1", fiscal.data().get("documentId"));
        assertEquals("2026-10-30T09:00:00.000Z", fiscal.createdAt().orElseThrow());

        String stringBody = "{\"id\":\"evt_3\",\"type\":\"physical_pos.payment.unknown\",\"data\":{},"
                + "\"payloadJson\":\"{\\\"paymentId\\\":\\\"p\\\",\\\"historySeq\\\":7}\"}";
        PhysicalPosWebhookEvent unknown = PhysicalPosClient.parseWebhookEvent(stringBody,
                signedHeaders("evt_3", "physical_pos.payment.unknown", stringBody), SECRET);
        assertEquals(7L, unknown.historySeq().orElseThrow());
        assertEquals(PhysicalPosWebhookEvent.CATEGORY_PAYMENT, unknown.category());
    }

    @Test
    void refusesUnverifiedForgedOrNonPhysicalPosEvents() throws Exception {
        String body = "{\"id\":\"evt_9\",\"type\":\"physical_pos.payment.approved\",\"data\":{\"paymentId\":\"p\","
                + "\"historySeq\":1}}";
        // No signature headers at all: a forged envelope is never read.
        assertThrows(WebhookVerificationException.class, () -> PhysicalPosClient.parseWebhookEvent(body, Map.of(),
                SECRET));
        Map<String, String> headers = signedHeaders("evt_9", PhysicalPosEvents.PAYMENT_APPROVED, body);
        // Tampered body, wrong secret, missing secret.
        assertThrows(WebhookVerificationException.class, () -> PhysicalPosClient.parseWebhookEvent(
                body.replace("\"p\"", "\"q\""), headers, SECRET));
        assertThrows(WebhookVerificationException.class, () -> PhysicalPosClient.parseWebhookEvent(body, headers,
                "whsec_other"));
        assertThrows(WebhookVerificationException.class, () -> PhysicalPosClient.parseWebhookEvent(body, headers,
                null));

        String fraud = "{\"id\":\"e\",\"type\":\"fraud.alert.triggered\",\"data\":{\"x\":1}}";
        assertThrows(WebhookVerificationException.class, () -> PhysicalPosClient.parseWebhookEvent(fraud,
                signedHeaders("e", "fraud.alert.triggered", fraud), SECRET));
        // The unsigned x-lunixi-event-type header never supplies the type.
        String untyped = "{\"id\":\"e\",\"data\":{\"x\":1}}";
        assertThrows(WebhookVerificationException.class, () -> PhysicalPosClient.parseWebhookEvent(untyped,
                signedHeaders("e", PhysicalPosEvents.PAYMENT_APPROVED, untyped), SECRET));
        String empty = "{\"id\":\"e\",\"type\":\"physical_pos.payment.approved\",\"data\":{}}";
        assertThrows(WebhookVerificationException.class, () -> PhysicalPosClient.parseWebhookEvent(empty,
                signedHeaders("e", PhysicalPosEvents.PAYMENT_APPROVED, empty), SECRET));
        String notJson = "{\"id\":\"e\",\"type\":\"physical_pos.payment.approved\",\"payloadJson\":\"not json\"}";
        assertThrows(WebhookVerificationException.class, () -> PhysicalPosClient.parseWebhookEvent(notJson,
                signedHeaders("e", PhysicalPosEvents.PAYMENT_APPROVED, notJson), SECRET));
    }
    @Test
    void canonicalMoneyRejectsNumbersNullAndNestedMoneyBeforeHttp() {
        for (Object invalid : java.util.Arrays.asList(4500, null, true, "", "01", "1e3", "1.0", " 1", "1\n", "0", "-1", "9223372036854775808")) {
            Map<String, Object> request = sale();
            request.put("amountMinor", invalid);
            assertThrows(ConfigurationException.class, () -> pos.createPayment(request, KEY));
            Map<String, Object> pricedItem = new LinkedHashMap<>(Map.of("lineNo", 1, "name", "Tea", "quantityMilli", 1000,
                    "unitCode", "C62", "taxGroupCode", "KDV0", "lineTotalMinor", "4500"));
            pricedItem.put("unitPriceMinor", invalid);
            Map<String, Object> itemRequest = sale();
            itemRequest.put("items", List.of(pricedItem));
            assertThrows(ConfigurationException.class, () -> pos.createPayment(itemRequest, KEY));
            Map<String, Object> reversal = new LinkedHashMap<>();
            reversal.put("operation", "REFUND"); reversal.put("currency", "TRY"); reversal.put("amountMinor", invalid);
            assertThrows(ConfigurationException.class, () -> pos.createReversal(PAYMENT_ID, reversal, KEY));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("lineNo", 1); item.put("quantityMilli", 1000); item.put("amountMinor", invalid);
            assertThrows(ConfigurationException.class, () -> pos.createReversal(PAYMENT_ID, Map.of("operation", "REFUND", "currency", "TRY", "items", List.of(item)), KEY));
            Map<String, Object> filter = new LinkedHashMap<>(); filter.put("amountFromMinor", invalid);
            assertThrows(ConfigurationException.class, () -> pos.listTransactions(filter));
        }
        assertEquals(0, transport.apiCalls().size());
    }

    @Test
    void largeMinorStringSurvivesTransportWithoutChangingCounts() {
        String exact = "9007199254740993";
        transport.enqueue(202, "{\"data\":{\"payment\":" + payment("NOT_STARTED").replace("4500", exact) + ",\"replayed\":false}}");
        Map<String, Object> request = sale();
        request.put("amountMinor", exact);
        request.put("currency", "KWD");
        request.put("items", List.of(Map.of("lineNo", 1, "name", "Exact", "quantityMilli", 1000, "unitCode", "C62", "unitPriceMinor", exact, "taxGroupCode", "KDV0", "lineTotalMinor", exact)));
        pos.createPayment(request, KEY);
        Map<String, Object> body = Json.parseObject(transport.apiCalls().get(0).body);
        assertEquals(exact, body.get("amountMinor"));
        Map<?, ?> item = (Map<?, ?>) ((List<?>) body.get("items")).get(0);
        assertEquals(exact, item.get("unitPriceMinor"));
        assertEquals(1000L, item.get("quantityMilli"));
    }

    @Test
    void numericMoneyInAcceptedResponseIsUnknown() {
        transport.enqueue(202, "{\"data\":{\"payment\":{\"paymentId\":\"" + PAYMENT_ID + "\",\"amountMinor\":4500}}}");
        assertThrows(PhysicalPosOutcomeUnknownException.class, () -> pos.createPayment(sale(), KEY));
        assertEquals(1, transport.apiCalls().size());
    }

}
