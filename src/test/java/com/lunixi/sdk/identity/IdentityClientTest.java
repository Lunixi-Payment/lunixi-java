package com.lunixi.sdk.identity;

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

/** Identity: route table, analysis bodies, linked accounts, reads, lists and local refusals. */
class IdentityClientTest {

    static final String GATEWAY = "https://gateway.invalid";
    static final String BASE = GATEWAY + "/api/v1/identity/live-sessions";

    RecordingTransport transport;
    IdentityClient identity;

    @BeforeEach
    void setUp() {
        transport = new RecordingTransport("/api/v1/auth/token", "fixture-only");
        identity = LunixiClient.create(LunixiConfig.builder().baseUrl(GATEWAY).keyId("test-key")
                .privateKey(Ed25519KeyPair.generate().privateKeyPem()).transport(transport).maxRetries(0).build())
                .identity();
    }

    static Map<String, Object> map(Object... pairs) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            out.put((String) pairs[i], pairs[i + 1]);
        }
        return out;
    }

    @Test
    void routesAreBearerOnly() {
        assertEquals(8, IdentityRoutes.ROUTES.size());
        IdentityRoutes.ROUTES.forEach((name, route) -> {
            assertFalse(route.stepUp(), name);
            assertTrue(route.path().startsWith("/api/v1/identity/live-sessions"), name);
        });
    }

    @Test
    void analyzeSyncSendsTheSignalBodyAndReturnsTheTrace() {
        transport.enqueue(200, "{\"success\":true,\"data\":{\"traceId\":\"tr_1\",\"analysisStatus\":\"completed\","
                + "\"riskFlags\":[\"same_device_cluster\"],\"identitySnapshot\":{\"decisionGrade\":true}}}");
        transport.enqueue(202, "{\"success\":true,\"data\":{\"traceId\":\"tr_2\"}}");
        Map<String, Object> analysis = identity.analyzeSync(map("subjectType", "user", "subjectId", "42",
                "device", map("deviceId", "fp_1", "ipAddress", "203.0.113.7"), "account", map("accountId", "42")));
        assertEquals("tr_1", analysis.get("traceId"));
        assertEquals(List.of("same_device_cluster"), analysis.get("riskFlags"));
        RecordingTransport.Call call = transport.apiCalls().get(0);
        assertEquals(BASE + "/analyze-sync", call.url);
        assertNull(call.headers.get(CanonicalRequest.HEADER_SIGNATURE));
        assertEquals(map("deviceId", "fp_1", "ipAddress", "203.0.113.7"), Json.parseObject(call.body).get("device"));

        identity.analyze(map("paymentId", "pay_1"));
        assertEquals(BASE + "/analyze", transport.apiCalls().get(1).url);
        ConfigurationException pair = assertThrows(ConfigurationException.class,
                () -> identity.analyzeSync(map("subjectId", "42")));
        assertTrue(pair.getMessage().contains("subjectType and subjectId"), pair.getMessage());
        assertThrows(ConfigurationException.class, () -> identity.analyzeSync(map("verdict", "allow")));
        assertThrows(ConfigurationException.class, () -> identity.analyzeSync(map("device", "fp_1")));
        assertEquals(2, transport.apiCalls().size());
    }

    @Test
    void accountLinksDetectsASharedDevice() {
        transport.enqueue(200, "{\"success\":true,\"data\":{\"totalLinkedSessions\":3,\"linkedAccounts\":[\"h_a\"],"
                + "\"sessions\":[{\"linkReasons\":[\"same_fingerprint\"]},{\"linkReasons\":[\"same_fingerprint\","
                + "\"same_ip_asn\"]}]}}");
        Map<String, Object> links = identity.accountLinks("tr_1");
        assertEquals(BASE + "/tr_1/account-links", transport.onlyApiCall().url);
        assertTrue(IdentityClient.isDeviceShared(links));
        assertFalse(IdentityClient.isDeviceShared(links, 3));
        assertEquals(List.of("same_fingerprint", "same_ip_asn"), IdentityClient.linkReasons(links));
        assertFalse(IdentityClient.isDeviceShared(Map.of("linkedAccounts", List.of())));
        assertFalse(IdentityClient.isDeviceShared(null));
    }

    @Test
    void sessionReadsRelatedSessionsTimelineTopSourcesAndLists() {
        for (int i = 0; i < 5; i++) {
            transport.enqueue(200, "{\"success\":true,\"data\":{\"items\":[{\"traceId\":\"tr_1\"}]}}");
        }
        identity.getSession("tr_1");
        identity.relatedSessions("tr_1", 5);
        identity.timeline("tr_1");
        identity.topSources(map("days", 7, "limit", 10));
        RoutePage page = identity.listSessions(map("deviceId", "fp_1"));
        List<RecordingTransport.Call> calls = transport.apiCalls();
        assertEquals(BASE + "/tr_1", calls.get(0).url);
        assertEquals(BASE + "/tr_1/related-sessions?limit=5", calls.get(1).url);
        assertEquals(BASE + "/tr_1/timeline", calls.get(2).url);
        assertEquals(BASE + "/top-sources?days=7&limit=10", calls.get(3).url);
        assertEquals(BASE + "?deviceId=fp_1", calls.get(4).url);
        assertEquals("tr_1", page.items().get(0).get("traceId"));
        assertTrue(page.nextCursor().isEmpty());
        ConfigurationException error = assertThrows(ConfigurationException.class,
                () -> identity.listSessions(map("email", "ada@example.com")));
        assertTrue(error.getMessage().contains("does not accept the filter"), error.getMessage());
        assertFalse(error.getMessage().contains("ada@example.com"), error.getMessage());
        assertThrows(ConfigurationException.class, () -> identity.getSession(""));
        assertEquals(5, transport.apiCalls().size());
    }
}
