package com.lunixi.sdk.kyc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import com.lunixi.sdk.http.RoutePage;
import com.lunixi.sdk.json.Json;
import com.lunixi.sdk.support.RecordingTransport;

/** KYC: route table, session bodies, the list filter rule, admin routes and local refusals. Fixture names are fictional. */
class KycClientTest {

    static final String GATEWAY = "https://gateway.invalid";
    static final String BASE = GATEWAY + "/api/v1/kyc";
    static final String OK = "{\"success\":true,\"data\":{}}";

    RecordingTransport transport;
    KycClient kyc;

    @BeforeEach
    void setUp() {
        transport = new RecordingTransport("/api/v1/auth/token", "fixture-only");
        kyc = LunixiClient.create(LunixiConfig.builder().baseUrl(GATEWAY).keyId("test-key")
                .privateKey(Ed25519KeyPair.generate().privateKeyPem()).transport(transport).maxRetries(0).build())
                .kyc();
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

    @Test
    void everyRouteIsBearerOnlyUnderTheKycPath() {
        assertEquals(16, KycRoutes.ROUTES.size());
        KycRoutes.ROUTES.forEach((name, route) -> {
            assertFalse(route.stepUp(), name);
            assertTrue(route.path().startsWith("/api/v1/kyc/"), name);
            assertFalse(route.path().contains("/sdk/init"), "the SDK bootstrap is publishable-key only");
        });
    }

    @Test
    void valuesAndHelpers() {
        assertEquals("ongoing_monitoring.alert.created", KycEvents.MONITORING_ALERT_CREATED);
        assertEquals("kyb.related_kyc.completed", KycEvents.KYB_RELATED_KYC_COMPLETED);
        assertEquals(25, KycEvents.ALL.size());
        assertEquals("possession_biometric_binding", KycValues.AssuranceDomain.BIOMETRIC);
        assertEquals(17, KycValues.SessionStatus.ALL.size());
        assertTrue(KycValues.isSessionTerminal("manual_review"));
        assertFalse(KycValues.isSessionTerminal("PROCESSING"));
        assertFalse(KycValues.isSessionTerminal(null));
        assertTrue(KycValues.assuranceMeets(KycValues.AssuranceLevel.HIGH, KycValues.AssuranceLevel.SUBSTANTIAL));
        assertFalse(KycValues.assuranceMeets(KycValues.AssuranceLevel.NOT_PERFORMED, KycValues.AssuranceLevel.LOW));
        assertEquals(3, KycValues.assuranceRank("high"));
    }

    @Test
    void createSessionSendsTheBodyBearerOnlyWithTheKeyInHeaderAndBody() {
        transport.enqueue(201, "{\"success\":true,\"data\":{\"session\":{\"sessionId\":\"sess_1\",\"status\":"
                + "\"CREATED\",\"externalCustomerId\":\"42\"}}}");
        Map<String, Object> result = kyc.createSession(map("subjectId", "42", "sessionType", "kyc",
                "externalCustomerId", "42", "jurisdiction", "TR", "person", map("firstName", "Ada"),
                "metadata", map("orderId", "o-1")), "idem-kyc-1");
        assertEquals("sess_1", ((Map<?, ?>) result.get("session")).get("sessionId"));
        RecordingTransport.Call call = transport.onlyApiCall();
        assertEquals("POST", call.method);
        assertEquals(BASE + "/sessions", call.url);
        assertNull(call.headers.get(CanonicalRequest.HEADER_SIGNATURE));
        assertEquals("idem-kyc-1", call.headers.get("Idempotency-Key"));
        assertEquals(map("subjectId", "42", "sessionType", "KYC", "externalCustomerId", "42", "jurisdiction", "TR",
                "customer", map("customerType", "PERSON", "firstName", "Ada"),
                "evidenceContextJson", "{\"orderId\":\"o-1\"}", "idempotencyKey", "idem-kyc-1"), body(call));
    }

    @Test
    void createSessionRefusesBeforeSending() {
        assertThrows(ConfigurationException.class, () -> kyc.createSession(map("subjectId", "42",
                "sessionType", "AML")));
        ConfigurationException two = assertThrows(ConfigurationException.class, () -> kyc.createSession(
                map("subjectId", "42", "sessionType", KycValues.SessionType.KYB, "person", map(), "company", map())));
        assertTrue(two.getMessage().contains("one of person, company or customer"), two.getMessage());
        ConfigurationException ttl = assertThrows(ConfigurationException.class, () -> kyc.createSession(
                map("subjectId", "42", "sessionType", "KYC", "ttlMinutes", 2000)));
        assertTrue(ttl.getMessage().contains("ttlMinutes"), ttl.getMessage());
        ConfigurationException unknown = assertThrows(ConfigurationException.class, () -> kyc.createSession(
                map("subjectId", "42", "sessionType", "KYC", "status", "APPROVED")));
        assertTrue(unknown.getMessage().contains("does not accept status"), unknown.getMessage());
        assertThrows(ConfigurationException.class, () -> kyc.createSession(map("sessionType", "KYC")));
        assertThrows(ConfigurationException.class, () -> kyc.createSession(map("subjectId", "42",
                "sessionType", "KYC", "requiredDocumentTypes", List.of(1))));
        assertThrows(ConfigurationException.class, () -> kyc.evidenceRequest(map("subjectId", "42",
                "sessionType", "EVIDENCE", "metadata", map("a", 1), "evidenceContextJson", "{}")));
        assertNothingSent();
    }

    @Test
    void evidenceRequestUsesItsOwnRoute() {
        transport.enqueue(201, OK);
        kyc.evidenceRequest(map("subjectId", "42", "sessionType", "evidence", "evidencePurpose",
                KycValues.EvidencePurpose.PROOF_OF_ADDRESS, "company", map("legalName", "Acme")));
        RecordingTransport.Call call = transport.onlyApiCall();
        assertEquals(BASE + "/evidence-requests", call.url);
        assertNull(call.headers.get("Idempotency-Key"));
        assertEquals(map("customerType", "COMPANY", "legalName", "Acme"), body(call).get("customer"));
        assertEquals("EVIDENCE", body(call).get("sessionType"));
    }

    @Test
    void listSessionsTakesOnlyLimitAndCursorAndMapsPageSizeAndPageToken() {
        transport.enqueue(200, "{\"status\":\"success\",\"data\":{\"items\":[{\"sessionId\":\"s1\"},"
                + "{\"sessionId\":\"s2\"}],\"nextCursor\":\"tok2\"}}");
        RoutePage page = kyc.listSessions(map("pageSize", 2, "pageToken", "abc"));
        assertEquals(2, page.items().size());
        assertEquals("tok2", page.nextCursor().orElseThrow());
        assertTrue(page.hasMore());
        assertEquals(BASE + "/sessions?limit=2&cursor=abc", transport.onlyApiCall().url);
        ConfigurationException error = assertThrows(ConfigurationException.class,
                () -> kyc.listSessions(map("status", "APPROVED")));
        assertTrue(error.getMessage().contains("does not accept the filter(s) status"), error.getMessage());
        assertEquals(1, transport.apiCalls().size());
    }

    @Test
    void aPageInfoWithoutACursorIsTheLastPage() {
        transport.enqueue(200, "{\"status\":\"success\",\"data\":{\"items\":[{\"sessionId\":\"s1\"}],\"nextCursor\":"
                + "\"stale\",\"pageInfo\":{\"hasMore\":false,\"nextCursor\":null}}}");
        RoutePage page = kyc.listSessions();
        assertTrue(page.nextCursor().isEmpty());
        assertFalse(page.hasMore());
    }

    @Test
    void sessionOperationsAndAdminRoutes() {
        for (int i = 0; i < 10; i++) {
            transport.enqueue(200, OK);
        }
        kyc.setPrimaryDocument("sess_1", "PASSPORT");
        kyc.setJurisdiction("sess_1", "TR");
        kyc.submit("sess_1");
        kyc.manualReview("sess_1");
        kyc.manualReview("sess_1", "DOC_BLUR", null);
        kyc.merchantPolicy();
        kyc.rotateWebhookSecret();
        kyc.webhookOutbox(map("status", "FAILED", "limit", 10));
        kyc.retryWebhookEvent("evt_1");
        kyc.erasureRequests(map("limit", 5));
        List<RecordingTransport.Call> calls = transport.apiCalls();
        assertEquals(BASE + "/sessions/sess_1/primary-document", calls.get(0).url);
        assertEquals(map("documentType", "PASSPORT"), body(calls.get(0)));
        assertEquals(map("jurisdiction", "TR"), body(calls.get(1)));
        assertNull(calls.get(2).body);
        assertNull(calls.get(3).body);
        assertEquals(map("reasonCode", "DOC_BLUR"), body(calls.get(4)));
        assertEquals(BASE + "/admin/merchant-policy", calls.get(5).url);
        assertEquals("GET", calls.get(5).method);
        assertEquals(BASE + "/admin/merchant-policy/webhook-secret/rotate", calls.get(6).url);
        assertEquals(BASE + "/admin/webhook-outbox?status=FAILED&limit=10", calls.get(7).url);
        assertEquals(BASE + "/admin/webhook-outbox/evt_1/retry", calls.get(8).url);
        assertEquals(BASE + "/admin/erasure-requests?limit=5", calls.get(9).url);
        calls.forEach(call -> assertNull(call.headers.get(CanonicalRequest.HEADER_SIGNATURE)));
        assertThrows(ConfigurationException.class, () -> kyc.webhookOutbox(map("type", "x")));
        assertThrows(ConfigurationException.class, () -> kyc.setJurisdiction("sess_1", " "));
        assertEquals(10, transport.apiCalls().size());
    }

    @Test
    void uploadDocumentNeverEchoesTheImage() {
        ConfigurationException error = assertThrows(ConfigurationException.class, () -> kyc.uploadDocument("sess_1",
                map("documentType", "PASSPORT", "fileBase64", "SECRETIMAGEDATA", "mimeType", "image/jpeg",
                        "extra", "SECRETIMAGEDATA")));
        assertFalse(error.getMessage().contains("SECRETIMAGEDATA"), error.getMessage());
        ConfigurationException missing = assertThrows(ConfigurationException.class, () -> kyc.uploadDocument(
                "sess_1", map("documentType", "PASSPORT", "fileBase64", "SECRETIMAGEDATA")));
        assertTrue(missing.getMessage().contains("mimeType"), missing.getMessage());
        assertFalse(missing.getMessage().contains("SECRETIMAGEDATA"), missing.getMessage());
        assertNothingSent();
    }
}
