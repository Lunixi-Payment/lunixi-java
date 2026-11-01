package com.lunixi.sdk.physicalpos;

import static com.lunixi.sdk.physicalpos.PhysicalPosClientTest.KEY;
import static com.lunixi.sdk.physicalpos.PhysicalPosClientTest.PAYMENT_ID;
import static com.lunixi.sdk.physicalpos.PhysicalPosClientTest.payment;
import static com.lunixi.sdk.physicalpos.PhysicalPosClientTest.sale;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.lunixi.sdk.LunixiClient;
import com.lunixi.sdk.LunixiConfig;
import com.lunixi.sdk.auth.Ed25519KeyPair;
import com.lunixi.sdk.exception.ConfigurationException;
import com.lunixi.sdk.exception.LunixiApiException;
import com.lunixi.sdk.exception.PhysicalPosCardDataRejectedException;
import com.lunixi.sdk.exception.PhysicalPosOutcomeUnknownException;
import com.lunixi.sdk.exception.RateLimitException;
import com.lunixi.sdk.exception.ValidationException;
import com.lunixi.sdk.http.HttpResult;
import com.lunixi.sdk.http.HttpTransport;
import com.lunixi.sdk.json.Json;
import com.lunixi.sdk.support.RecordingTransport;

/**
 * Money-path behaviour of payment writes: what counts as an unknown outcome (D-02 review Y-2, O-1, D-1), the
 * required key (Y-1, O-2), card numbers in text (O-4) and filter values (D-3, D-4).
 */
class PhysicalPosOutcomeTest {

    static final String ACCEPTED = "{\"data\":{\"payment\":" + payment("NOT_STARTED")
            + ",\"replayed\":false},\"statusCode\":202}";
    static final String REPLAYED = "{\"data\":{\"payment\":" + payment("NOT_STARTED")
            + ",\"replayed\":true},\"statusCode\":200}";

    final RecordingTransport transport = new RecordingTransport("/api/v1/auth/token", "fixture-only");

    PhysicalPosClient pos(int maxRetries) {
        return client(transport, maxRetries).physicalPos();
    }

    static LunixiClient client(HttpTransport transport, int maxRetries) {
        return LunixiClient.create(LunixiConfig.builder().baseUrl("https://gateway.invalid").keyId("test-key")
                .privateKey(Ed25519KeyPair.generate().privateKeyPem()).transport(transport).maxRetries(maxRetries)
                .build());
    }

    /** A transport that answers the token call and then plays the given steps; a null step throws. */
    static final class ScriptedTransport implements HttpTransport {
        final List<Object> steps;
        final List<Map<String, String>> sent = new ArrayList<>();

        ScriptedTransport(Object... steps) {
            this.steps = new ArrayList<>(List.of(steps));
        }

        @Override
        public HttpResult send(String method, String url, Map<String, String> headers, String body, Duration timeout) {
            if (url.endsWith("/api/v1/auth/token")) {
                return new HttpResult(200, Map.of(), "{\"status\":\"success\",\"data\":{\"access_token\":"
                        + "\"fixture-only\",\"token_type\":\"Bearer\",\"expires_in\":1800}}");
            }
            sent.add(headers);
            Object step = steps.remove(0);
            if (step instanceof RuntimeException failure) {
                throw failure;
            }
            return (HttpResult) step;
        }
    }

    // ── Y-2: a 2xx is a success only with a readable payment ──────────────────────────────────────────────────

    @Test
    void aNonJsonTwoHundredIsAnUnknownOutcome() {
        transport.enqueue(202, "<html>accepted</html>");
        PhysicalPosOutcomeUnknownException unknown = assertThrows(PhysicalPosOutcomeUnknownException.class,
                () -> pos(0).createPayment(sale(), KEY));
        assertEquals(KEY, unknown.idempotencyKey());
        assertEquals(202, unknown.statusCode());
    }

    @Test
    void aTwoHundredWithoutAPaymentIsAnUnknownOutcome() {
        transport.enqueue(202, "{\"data\":{\"replayed\":false},\"statusCode\":202}");
        assertEquals(KEY, assertThrows(PhysicalPosOutcomeUnknownException.class,
                () -> pos(0).createPayment(sale(), KEY)).idempotencyKey());
        transport.enqueue(200, "{\"data\":{\"payment\":{\"paymentStatus\":\"EXPIRED\"}}}");
        assertThrows(PhysicalPosOutcomeUnknownException.class, () -> pos(0).cancelPayment(PAYMENT_ID, KEY));
    }

    @Test
    void aBodyReadFailureOrConnectionDropIsAnUnknownOutcome() {
        ScriptedTransport timeout = new ScriptedTransport(
                new LunixiApiException("body read timed out", new java.net.http.HttpTimeoutException("read")));
        PhysicalPosOutcomeUnknownException unknown = assertThrows(PhysicalPosOutcomeUnknownException.class,
                () -> client(timeout, 0).physicalPos().createPayment(sale(), KEY));
        assertEquals(KEY, unknown.idempotencyKey());
        assertEquals(0, unknown.statusCode());
        assertTrue(unknown.getCause() instanceof LunixiApiException);

        ScriptedTransport dropped = new ScriptedTransport(new java.io.UncheckedIOException(
                new java.io.IOException("connection reset while reading the body")));
        assertEquals(KEY, assertThrows(PhysicalPosOutcomeUnknownException.class,
                () -> client(dropped, 0).physicalPos().createReversal(PAYMENT_ID,
                        Map.of("operation", "VOID", "currency", "TRY"), KEY)).idempotencyKey());
    }

    @Test
    void anUnreadableTwoHundredFollowedByTheReplayIsASuccess() {
        transport.enqueue(202, "<html>accepted</html>");
        transport.enqueue(200, REPLAYED);
        PhysicalPosWriteResult result = pos(1).createPayment(sale(), KEY);
        assertTrue(result.replayed());
        assertEquals(200, result.statusCode());
        assertEquals(2, transport.apiCalls().size());
    }

    // ── O-1: once any attempt was ambiguous, every final failure is an unknown outcome ─────────────────────────

    @Test
    void anAmbiguousAttemptThenRateLimitIsAnUnknownOutcomeWithTheKeyAndCause() {
        transport.enqueue(502, "{\"status\":\"failure\",\"code\":\"BAD_GATEWAY\"}");
        transport.enqueue(429, Map.of("retry-after", "17"), "{\"status\":\"failure\",\"code\":\"RATE_LIMITED\"}");
        PhysicalPosOutcomeUnknownException unknown = assertThrows(PhysicalPosOutcomeUnknownException.class,
                () -> pos(1).createPayment(sale(), KEY));
        assertEquals(KEY, unknown.idempotencyKey());
        assertEquals(429, unknown.statusCode());
        assertTrue(unknown.getCause() instanceof RateLimitException);
        assertEquals(Duration.ofSeconds(17), unknown.retryAfter().orElseThrow());
        List<RecordingTransport.Call> calls = transport.apiCalls();
        assertEquals(2, calls.size());
        assertEquals(KEY, calls.get(0).headers.get("Idempotency-Key"));
        assertEquals(KEY, calls.get(1).headers.get("Idempotency-Key"));
        assertEquals(calls.get(0).body, calls.get(1).body);
    }

    @Test
    void anAmbiguousAttemptThenConflictIsAnUnknownOutcome() {
        ScriptedTransport script = new ScriptedTransport(
                new LunixiApiException("connection reset", new java.io.IOException("reset")),
                new HttpResult(409, Map.of(), "{\"status\":\"failure\",\"code\":\"physical_pos.idempotency_mismatch\"}"));
        PhysicalPosOutcomeUnknownException unknown = assertThrows(PhysicalPosOutcomeUnknownException.class,
                () -> client(script, 1).physicalPos().createReversal(PAYMENT_ID,
                        Map.of("operation", "VOID", "currency", "TRY"), KEY));
        assertEquals("physical_pos.idempotency_mismatch", unknown.code().orElseThrow());
        assertEquals(KEY, unknown.idempotencyKey());
    }

    @Test
    void aNotStartedFiveHundredThenARejectionIsThatRejection() {
        transport.enqueue(503, "{\"status\":\"failure\",\"code\":\"physical_pos.presence.unavailable\"}");
        transport.enqueue(400, "{\"status\":\"failure\",\"code\":\"physical_pos.payment.amount_invalid\"}");
        ValidationException invalid = assertThrows(ValidationException.class, () -> pos(1).createPayment(sale(), KEY));
        assertEquals(ValidationException.class, invalid.getClass());
        assertEquals("physical_pos.payment.amount_invalid", invalid.code().orElseThrow());
        assertEquals(2, transport.apiCalls().size());
    }

    @Test
    void aNotStartedFiveHundredThenSuccessIsASuccess() {
        transport.enqueue(503, "{\"status\":\"failure\",\"code\":\"physical_pos.signing.unavailable\"}");
        transport.enqueue(202, ACCEPTED);
        assertEquals(202, pos(1).createPayment(sale(), KEY).statusCode());
        assertEquals(2, transport.apiCalls().size());
    }

    @Test
    void paymentWritesAreNotRetriedTwiceOverAndReadsKeepTheTransportRetry() {
        transport.enqueue(502, "{\"status\":\"failure\"}");
        transport.enqueue(502, "{\"status\":\"failure\"}");
        assertThrows(PhysicalPosOutcomeUnknownException.class, () -> pos(1).createPayment(sale(), KEY));
        assertEquals(2, transport.apiCalls().size(), "one SDK loop, no nested transport retries");

        transport.enqueue(502, "{\"status\":\"failure\"}");
        transport.enqueue(200, "{\"data\":{\"payment\":" + payment("APPROVED") + "}}");
        assertEquals("APPROVED", pos(1).getPayment(PAYMENT_ID).get("paymentStatus"));
        assertEquals(4, transport.apiCalls().size());
    }

    // ── Y-1 / O-2: the key ────────────────────────────────────────────────────────────────────────────────────

    @Test
    void emptyAndBlankKeysAreRefusedAndNeverReplaced() {
        PhysicalPosClient pos = pos(0);
        for (String key : new String[]{"", "   ", "        ", " pos-key-01", "pos-key-01 "}) {
            assertThrows(ConfigurationException.class, () -> pos.createPayment(sale(), key));
            assertThrows(ConfigurationException.class, () -> pos.cancelPayment(PAYMENT_ID, key));
            assertThrows(ConfigurationException.class, () -> client(transport, 0).physicalPosManagement()
                    .createStore(Map.of("code", "IST-01", "name", "Kadıköy"), key));
        }
        assertTrue(transport.calls().isEmpty());
    }

    // ── O-4: card numbers in text ─────────────────────────────────────────────────────────────────────────────

    @Test
    void cardNumbersInReferencesItemTextAndFiltersAreRefusedBeforeSending() {
        PhysicalPosClient pos = pos(0);
        Map<String, Object> reference = sale();
        reference.put("merchantReference", "4111 1111 1111 1111");
        PhysicalPosCardDataRejectedException rejected = assertThrows(PhysicalPosCardDataRejectedException.class,
                () -> pos.createPayment(reference, KEY));
        assertEquals("merchantReference", rejected.field().orElseThrow());
        assertEquals(0, rejected.statusCode());
        assertFalse(rejected.getMessage().contains("4111"));

        Map<String, Object> name = sale();
        @SuppressWarnings("unchecked")
        Map<String, Object> item = new LinkedHashMap<>(((List<Map<String, Object>>) name.get("items")).get(0));
        item.put("name", "Card 5500-0000-0000-0004");
        name.put("items", List.of(item));
        assertEquals("items[0].name", assertThrows(PhysicalPosCardDataRejectedException.class,
                () -> pos.createPayment(name, KEY)).field().orElseThrow());

        assertEquals("merchantReference", assertThrows(PhysicalPosCardDataRejectedException.class,
                () -> pos.createReversal(PAYMENT_ID, Map.of("operation", "VOID", "currency", "TRY",
                        "merchantReference", "4111111111111111"), KEY)).field().orElseThrow());
        assertEquals("merchantReference", assertThrows(PhysicalPosCardDataRejectedException.class,
                () -> pos.listPayments(Map.of("merchantReference", "4111111111111111"))).field().orElseThrow());
        assertEquals("q", assertThrows(PhysicalPosCardDataRejectedException.class,
                () -> client(transport, 0).physicalPosManagement().listStores(Map.of("q", "4111 1111 1111 1111")))
                .field().orElseThrow());
        assertTrue(transport.calls().isEmpty());

        // A barcode in an item name is not a card number.
        Map<String, Object> barcode = sale();
        Map<String, Object> barcodeItem = new LinkedHashMap<>(item);
        barcodeItem.put("name", "Simit 8691234567012");
        barcode.put("items", List.of(barcodeItem));
        transport.enqueue(202, ACCEPTED);
        pos.createPayment(barcode, KEY);
        assertEquals(1, transport.apiCalls().size());
    }

    // ── D-3 / D-4: filter values and empty strings ────────────────────────────────────────────────────────────

    @Test
    void datesAreSentAsGivenAndEmptyValuesAreLeftOut() {
        PhysicalPosClient pos = pos(0);
        transport.enqueue(200, "{\"data\":{\"rows\":[]}}");
        pos.getSalesSummary(Map.of("dateFrom", LocalDate.of(2026, 10, 1), "dateTo", "2026-10-07"));
        transport.enqueue(200, "{\"data\":{\"items\":[]},\"pageInfo\":{\"hasMore\":false}}");
        pos.listPayments(Map.of("createdFrom", Instant.parse("2026-10-01T00:00:00Z"), "merchantReference", ""));
        transport.enqueue(202, ACCEPTED);
        Map<String, Object> request = sale();
        request.put("instrument", "");
        request.put("allowDuplicateReference", null);
        pos.createPayment(request, KEY);

        List<RecordingTransport.Call> calls = transport.apiCalls();
        assertEquals(PhysicalPosClientTest.BASE + "/reports/sales-summary?dateFrom=2026-10-01&dateTo=2026-10-07",
                calls.get(0).url);
        assertEquals(PhysicalPosClientTest.BASE + "/payments?createdFrom=2026-10-01T00%3A00%3A00.000Z",
                calls.get(1).url);
        Map<String, Object> body = Json.parseObject(calls.get(2).body);
        assertFalse(body.containsKey("instrument"));
        assertFalse(body.containsKey("allowDuplicateReference"));

        Map<String, Object> noCurrency = sale();
        noCurrency.put("currency", "");
        assertThrows(ConfigurationException.class, () -> pos.createPayment(noCurrency, KEY));
        assertThrows(ConfigurationException.class,
                () -> pos.getSalesSummary(Map.of("dateFrom", Instant.parse("2026-10-01T00:00:00Z"))));
        assertEquals(3, transport.apiCalls().size());
    }
}
