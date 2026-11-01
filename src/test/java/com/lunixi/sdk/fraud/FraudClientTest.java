package com.lunixi.sdk.fraud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.lunixi.sdk.LunixiClient;
import com.lunixi.sdk.LunixiConfig;
import com.lunixi.sdk.auth.CanonicalRequest;
import com.lunixi.sdk.auth.Ed25519KeyPair;
import com.lunixi.sdk.auth.Ed25519Signer;
import com.lunixi.sdk.exception.ConfigurationException;
import com.lunixi.sdk.exception.LunixiApiException;
import com.lunixi.sdk.exception.RateLimitException;
import com.lunixi.sdk.exception.ServiceUnavailableException;
import com.lunixi.sdk.exception.ValidationException;
import com.lunixi.sdk.support.RecordingTransport;

/**
 * Wiring tests for the fraud surface. Nothing here touches the network: the
 * transport records what the SDK built and answers with a canned envelope, so
 * the assertions are about the request on the wire — path, headers, canonical
 * string, digest, query string — and about how an answer is read.
 */
class FraudClientTest {

    private static final String BASE_URL = "https://gateway.invalid";
    private static final String AUTH_PATH = "/api/v1/auth/token";

    private Ed25519KeyPair keyPair;
    private RecordingTransport transport;
    private LunixiClient client;

    @BeforeEach
    void setUp() {
        // The key is generated per test; no key material is committed anywhere.
        keyPair = Ed25519KeyPair.generate();
        transport = new RecordingTransport(AUTH_PATH, "at_test_0123456789");
        client = LunixiClient.create(LunixiConfig.builder()
                .baseUrl(BASE_URL)
                .keyId("kid_test_1")
                .privateKey(keyPair.privateKeyPem())
                .environment(LunixiConfig.Environment.TEST)
                .transport(transport)
                .maxRetries(1)
                .build());
    }

    @Test
    @DisplayName("evaluate: /api/v1 önekli yol, Bearer kimlik, imza başlığı YOK")
    void evaluateUsesThePrefixedPathAndBearerOnly() {
        transport.enqueue(200, "{\"status\":\"success\",\"code\":\"SUCCESS\",\"data\":{\"decision\":\"allow\","
                + "\"score\":12.5,\"traceId\":\"trc_1\",\"reasonCodes\":[\"velocity_ok\"],\"latencyMs\":31}}");

        FraudDecision decision = client.fraud().evaluate(EvaluateRequest.create()
                .channel(FraudTaxonomy.Channel.STANDALONE_API)
                .transactionRef("order-1")
                .payment(Map.of("amount", "25000")));

        RecordingTransport.Call call = transport.onlyApiCall();
        // Without the global prefix the whole surface answers 404, so the prefix
        // is asserted rather than assumed.
        assertEquals(BASE_URL + "/api/v1/fraud/decisions/evaluate", call.url);
        assertEquals("POST", call.method);
        assertEquals("Bearer at_test_0123456789", call.headers.get("Authorization"));
        assertFalse(call.headers.containsKey(CanonicalRequest.HEADER_SIGNATURE));
        assertFalse(call.headers.containsKey(CanonicalRequest.HEADER_DIGEST));

        assertEquals(FraudDecisionValue.ALLOW, decision.decision().orElseThrow());
        assertTrue(decision.isAllow());
        assertEquals(12.5d, decision.score().orElseThrow());
        assertEquals("trc_1", decision.traceId().orElseThrow());
        assertEquals(List.of("velocity_ok"), decision.reasonCodes());
        assertEquals(31L, decision.latencyMs().orElseThrow());
    }

    @Test
    @DisplayName("Jeton bir kez alınır, sonraki çağrılarda yeniden imzalanmaz")
    void fetchesTheTokenOnceAndReusesIt() {
        transport.enqueue(200, "{\"status\":\"success\",\"data\":{\"decision\":\"allow\"}}");
        transport.enqueue(200, "{\"status\":\"success\",\"data\":{\"decision\":\"block\"}}");

        client.fraud().evaluate(EvaluateRequest.create().paymentId("pay_1"));
        client.fraud().evaluate(EvaluateRequest.create().paymentId("pay_2"));

        long tokenCalls = transport.calls().stream().filter(call -> call.url.endsWith(AUTH_PATH)).count();
        assertEquals(1, tokenCalls);
    }

    @Test
    @DisplayName("Jeton isteği gövdesiz imzalanır ve açık anahtarla DOĞRULANIR")
    void tokenRequestIsSignedBodylessAndVerifies() throws Exception {
        transport.enqueue(200, "{\"status\":\"success\",\"data\":{\"decision\":\"allow\"}}");
        client.fraud().evaluate(EvaluateRequest.create().paymentId("pay_1"));

        RecordingTransport.Call tokenCall = transport.calls().get(0);
        assertEquals(BASE_URL + AUTH_PATH, tokenCall.url);
        assertFalse(tokenCall.headers.containsKey(CanonicalRequest.HEADER_DIGEST));

        String canonical = CanonicalRequest.build("POST", AUTH_PATH,
                tokenCall.headers.get(CanonicalRequest.HEADER_DATE),
                tokenCall.headers.get(CanonicalRequest.HEADER_NONCE),
                null);
        assertTrue(verify(canonical, tokenCall.headers.get(CanonicalRequest.HEADER_SIGNATURE)));
        assertEquals("kid_test_1", tokenCall.headers.get(CanonicalRequest.HEADER_KEY_ID));
    }

    @Test
    @DisplayName("Olay alımı: her istek İMZALI, Digest gönderilen KOMPAKT gövdenin özeti")
    void eventIngestSignsEveryRequestOverTheCompactBody() throws Exception {
        transport.enqueue(200, "{\"status\":\"success\",\"data\":{\"eventId\":\"evt_1\",\"id\":\"row_1\","
                + "\"deduplicated\":false,\"matchedFlowCount\":2}}");

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", "evt_1");
        event.put("eventType", "card_payment");
        event.put("subjectType", "customer");
        event.put("occurredAt", "2026-10-11T08:00:00Z");

        FraudEventResult result = client.fraud().events().record(event, "idem-1");

        RecordingTransport.Call call = transport.onlyApiCall();
        assertEquals(BASE_URL + "/api/v1/fraud/events/transactions", call.url);
        assertEquals("idem-1", call.headers.get("Idempotency-Key"));

        // The digest must be over the bytes that were actually sent: serialising
        // twice, or letting a mapper re-emit the body, is exactly how a correct
        // request turns into an unexplainable authentication failure.
        String digest = call.headers.get(CanonicalRequest.HEADER_DIGEST);
        assertEquals(CanonicalRequest.digestForBody(call.body), digest);
        assertTrue(digest.startsWith("SHA-256="));
        assertEquals("{\"eventId\":\"evt_1\",\"eventType\":\"card_payment\",\"subjectType\":\"customer\","
                + "\"occurredAt\":\"2026-10-11T08:00:00Z\"}", call.body);

        String canonical = CanonicalRequest.build("POST", "/api/v1/fraud/events/transactions",
                call.headers.get(CanonicalRequest.HEADER_DATE),
                call.headers.get(CanonicalRequest.HEADER_NONCE),
                digest);
        assertTrue(verify(canonical, call.headers.get(CanonicalRequest.HEADER_SIGNATURE)));

        assertEquals("row_1", result.rowId().orElseThrow());
        assertFalse(result.isDeduplicated());
        assertEquals(2L, result.matchedFlowCount().orElseThrow());
    }

    @Test
    @DisplayName("Her imzalı istek kendi nonce'unu taşır — tekrar reddedilir")
    void everySignedRequestCarriesItsOwnNonce() {
        transport.enqueue(200, "{\"status\":\"success\",\"data\":{}}");
        transport.enqueue(200, "{\"status\":\"success\",\"data\":{}}");

        Map<String, Object> event = Map.of("eventId", "evt_1");
        client.fraud().events().record(event);
        client.fraud().events().record(event);

        List<String> nonces = new ArrayList<>();
        for (RecordingTransport.Call call : transport.apiCalls()) {
            nonces.add(call.headers.get(CanonicalRequest.HEADER_NONCE));
        }
        assertEquals(2, nonces.size());
        assertEquals(2, java.util.Set.copyOf(nonces).size());
    }

    @Test
    @DisplayName("Parti sınırı 500: aşan parti AĞA ÇIKMADAN reddedilir")
    void rejectsAnOversizedBatchBeforeSending() {
        List<Map<String, Object>> events = new ArrayList<>();
        for (int i = 0; i <= FraudEventsClient.BATCH_LIMIT; i++) {
            events.add(Map.of("eventId", "evt_" + i));
        }

        ConfigurationException failure = assertThrows(ConfigurationException.class,
                () -> client.fraud().events().recordBatch(events));

        // The gateway rejects an oversized batch IN FULL, so failing before the
        // call is the difference between one readable error and 501 lost events.
        assertTrue(failure.getMessage().contains("500"));
        assertTrue(transport.apiCalls().isEmpty());
    }

    @Test
    @DisplayName("Boş parti reddedilir, 500 olay geçer")
    void acceptsExactlyTheLimitAndRejectsAnEmptyBatch() {
        assertThrows(ConfigurationException.class, () -> client.fraud().events().recordBatch(List.of()));

        transport.enqueue(200, "{\"status\":\"success\",\"data\":{\"total\":500,\"inserted\":500}}");
        List<Map<String, Object>> events = new ArrayList<>();
        for (int i = 0; i < FraudEventsClient.BATCH_LIMIT; i++) {
            events.add(Map.of("eventId", "evt_" + i));
        }

        assertEquals(500L, client.fraud().events().recordBatch(events).inserted());
        assertEquals(BASE_URL + "/api/v1/fraud/events/transactions/batch", transport.onlyApiCall().url);
        assertTrue(transport.onlyApiCall().body.startsWith("[{"));
    }

    @Test
    @DisplayName("Parti KISMEN başarılı olabilir — 200 tek başına parti indi demek değil")
    void readsPartialBatchFailures() {
        transport.enqueue(200, "{\"status\":\"success\",\"data\":{\"total\":3,\"inserted\":1,"
                + "\"deduplicated\":1,\"failed\":1,\"results\":["
                + "{\"eventId\":\"evt_1\",\"id\":\"row_1\"},"
                + "{\"eventId\":\"evt_2\",\"deduplicated\":true},"
                + "{\"eventId\":\"evt_3\",\"error\":\"eventType is unknown\"}]}}");

        FraudEventBatchResult result = client.fraud().events()
                .recordBatch(List.of(Map.of("eventId", "evt_1"), Map.of("eventId", "evt_2"),
                        Map.of("eventId", "evt_3")));

        assertEquals(3L, result.total());
        assertEquals(1L, result.inserted());
        assertEquals(1L, result.deduplicated());
        assertEquals(1L, result.failed());
        assertEquals(1, result.failures().size());
        assertEquals("evt_3", result.failures().get(0).eventId().orElseThrow());
        assertEquals("eventType is unknown", result.failures().get(0).error().orElseThrow());
        assertTrue(result.results().get(1).isDeduplicated());
    }

    @Test
    @DisplayName("Kayıt listesi: imleçli sayfalama, pageInfo data'nın kardeşi")
    void listsDecisionLogsWithCursorPaging() {
        transport.enqueue(200, "{\"status\":\"success\",\"data\":[{\"traceId\":\"t1\"},{\"traceId\":\"t2\"}],"
                + "\"pageInfo\":{\"hasMore\":true,\"nextCursor\":\"cur_2\"}}");

        FraudDecisionPage page = client.fraud().decisions()
                .list(DecisionLogFilter.create().paymentId("pay_1").limit(2));

        assertEquals(BASE_URL + "/api/v1/fraud/decision-logs?paymentId=pay_1&limit=2",
                transport.onlyApiCall().url);
        assertEquals("GET", transport.onlyApiCall().method);
        assertEquals(2, page.size());
        assertTrue(page.hasMore());
        assertEquals("cur_2", page.nextCursor().orElseThrow());
        assertTrue(page.totalCount().isEmpty());
    }

    @Test
    @DisplayName("iterate(): imleci izleyerek bütün sayfaları dolaşır")
    void iterateFollowsCursorsToTheEnd() {
        transport.enqueue(200, "{\"status\":\"success\",\"data\":[{\"traceId\":\"t1\"}],"
                + "\"pageInfo\":{\"hasMore\":true,\"nextCursor\":\"cur_2\"}}");
        transport.enqueue(200, "{\"status\":\"success\",\"data\":[{\"traceId\":\"t2\"}],"
                + "\"pageInfo\":{\"hasMore\":false,\"nextCursor\":null}}");

        List<String> traceIds = new ArrayList<>();
        for (FraudDecision decision : client.fraud().decisions()
                .iterate(DecisionLogFilter.create().limit(1))) {
            traceIds.add(decision.traceId().orElseThrow());
        }

        assertEquals(List.of("t1", "t2"), traceIds);
        assertEquals(2, transport.apiCalls().size());
        assertTrue(transport.apiCalls().get(1).url.contains("cursor=cur_2"));
    }

    @Test
    @DisplayName("Tek kayıt: traceId yol parçası olarak kodlanır")
    void getsOneRecordByTraceId() {
        transport.enqueue(200, "{\"status\":\"success\",\"data\":{\"traceId\":\"trc/1\",\"decision\":\"review\","
                + "\"explainability\":{\"summary\":\"iki kural eşleşti\"},\"createdAt\":\"2026-10-11T08:00:00Z\"}}");

        FraudDecision decision = client.fraud().decisions().get("trc/1");

        assertEquals(BASE_URL + "/api/v1/fraud/decision-logs/trc%2F1", transport.onlyApiCall().url);
        assertTrue(decision.isReview());
        assertEquals("iki kural eşleşti", decision.explainabilitySummary().orElseThrow());
        assertEquals("2026-10-11T08:00:00Z", decision.createdAt().orElseThrow());
    }

    @Test
    @DisplayName("latestForPayment(): kayıt yoksa boş döner")
    void latestForPaymentReturnsEmptyWhenThereIsNoRecord() {
        transport.enqueue(200, "{\"status\":\"success\",\"data\":[],\"pageInfo\":{\"hasMore\":false}}");

        assertTrue(client.fraud().decisions().latestForPayment("pay_404").isEmpty());
        assertThrows(ConfigurationException.class, () -> client.fraud().decisions().latestForPayment(" "));
    }

    @Test
    @DisplayName("400 → ValidationException, kod makine-okunur")
    void mapsFourHundredToValidationException() {
        transport.enqueue(400, "{\"status\":\"error\",\"code\":\"FRAUD_CHANNEL_NOT_DECLARABLE\","
                + "\"message\":\"channel beyan edilemez\",\"data\":null,\"statusCode\":400}");

        ValidationException failure = assertThrows(ValidationException.class,
                () -> client.fraud().evaluate(EvaluateRequest.create().paymentId("pay_1")));

        assertEquals(400, failure.statusCode());
        assertEquals("FRAUD_CHANNEL_NOT_DECLARABLE", failure.code().orElseThrow());
        assertEquals("req_test", failure.requestId().orElseThrow());
    }

    @Test
    @DisplayName("429 ve 503 → Retry-After başlıktan OKUNUR, uydurulmaz")
    void mapsQuotaAndOutageWithRetryAfter() {
        transport.enqueue(429, Map.of("retry-after", "42"),
                "{\"status\":\"error\",\"code\":\"QUOTA_EXCEEDED\",\"message\":\"kota doldu\"}");

        RateLimitException quota = assertThrows(RateLimitException.class,
                () -> client.fraud().evaluate(EvaluateRequest.create().paymentId("pay_1")));
        assertEquals(42L, quota.retryAfter().orElseThrow().getSeconds());

        transport.enqueue(503, Map.of(),
                "{\"status\":\"error\",\"code\":\"SERVICE_UNAVAILABLE\",\"message\":\"erişilemiyor\"}");
        ServiceUnavailableException outage = assertThrows(ServiceUnavailableException.class,
                () -> client.fraud().evaluate(EvaluateRequest.create().paymentId("pay_2")));
        // No header → no value. Inventing a delay would make every client retry
        // at a rate the server never asked for.
        assertTrue(outage.retryAfter().isEmpty());
    }

    @Test
    @DisplayName("200 ama zarf 'failure': sessizce başarı sayılmaz")
    void throwsWhenATwoHundredEnvelopeReportsFailure() {
        transport.enqueue(200, "{\"status\":\"failure\",\"code\":\"fraud.decision_unavailable\","
                + "\"message\":\"karar üretilemedi\",\"data\":null,\"statusCode\":200}");

        LunixiApiException failure = assertThrows(LunixiApiException.class,
                () -> client.fraud().evaluate(EvaluateRequest.create().paymentId("pay_1")));

        assertEquals("fraud.decision_unavailable", failure.code().orElseThrow());
    }

    @Test
    @DisplayName("401: jeton tazelenir ve istek BİR KEZ yeniden gönderilir")
    void reAuthenticatesOnceAfterAnUnauthorizedAnswer() {
        transport.enqueue(401, "{\"status\":\"error\",\"code\":\"INVALID_TOKEN\",\"message\":\"süresi geçti\"}");
        transport.enqueue(200, "{\"status\":\"success\",\"data\":{\"decision\":\"allow\"}}");

        assertTrue(client.fraud().evaluate(EvaluateRequest.create().paymentId("pay_1")).isAllow());

        long tokenCalls = transport.calls().stream().filter(call -> call.url.endsWith(AUTH_PATH)).count();
        // A rejected token means nothing was executed, so re-authenticating and
        // repeating the call cannot duplicate anything.
        assertEquals(2, tokenCalls);
        assertEquals(2, transport.apiCalls().size());
    }

    @Test
    @DisplayName("500: okuma yeniden denenir")
    void retriesAReadAfterAServerError() {
        transport.enqueue(500, "{\"status\":\"error\",\"code\":\"INTERNAL\",\"message\":\"hata\"}");
        transport.enqueue(200, "{\"status\":\"success\",\"data\":[],\"pageInfo\":{\"hasMore\":false}}");

        assertEquals(0, client.fraud().decisions().list().size());
        assertEquals(2, transport.apiCalls().size());
    }

    @Test
    @DisplayName("500: idempotency anahtarı OLMAYAN yazma yeniden DENENMEZ")
    void doesNotRetryAWriteWithoutAnIdempotencyKey() {
        transport.enqueue(500, "{\"status\":\"error\",\"code\":\"INTERNAL\",\"message\":\"hata\"}");

        assertThrows(LunixiApiException.class,
                () -> client.fraud().events().record(Map.of("eventId", "evt_1")));

        // A write that timed out may well have landed; repeating it blindly would
        // submit the same event twice.
        assertEquals(1, transport.apiCalls().size());
    }

    @Test
    @DisplayName("Bilinmeyen karar değeri 'allow' sayılmaz")
    void anUnknownDecisionValueIsNotTreatedAsAllow() {
        transport.enqueue(200, "{\"status\":\"success\",\"data\":{\"decision\":\"quarantine\"}}");

        FraudDecision decision = client.fraud().evaluate(EvaluateRequest.create().paymentId("pay_1"));

        assertTrue(decision.decision().isEmpty());
        assertFalse(decision.isAllow());
        assertEquals("quarantine", decision.decisionValue().orElseThrow());
    }

    private boolean verify(String canonical, String base64Signature) throws Exception {
        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(Ed25519Signer.publicKeyFrom(keyPair.publicKeyPem()));
        verifier.update(canonical.getBytes(StandardCharsets.UTF_8));
        return verifier.verify(Base64.getDecoder().decode(base64Signature));
    }
}
