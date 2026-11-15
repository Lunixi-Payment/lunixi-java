package com.lunixi.sdk.sanction;

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
import com.lunixi.sdk.exception.ServiceUnavailableException;
import com.lunixi.sdk.json.Json;
import com.lunixi.sdk.support.RecordingTransport;

/** Sanction screening: bearer-only calls, the route table checks, failed screenings and the clear-result rule. */
class SanctionClientTest {

    static final String GATEWAY = "https://gateway.invalid";
    static final String BASE = GATEWAY + "/api/v1/sanction";
    static final String CLEAR = "{\"status\":\"success\",\"code\":\"SUCCESS\",\"data\":{\"screeningRequestId\":"
            + "\"scr_1\",\"matches\":[],\"coverageStatus\":\"COMPLETE\"},\"requestId\":\"req_1\"}";

    RecordingTransport transport;
    SanctionClient sanction;

    @BeforeEach
    void setUp() {
        transport = new RecordingTransport("/api/v1/auth/token", "fixture-only");
        sanction = LunixiClient.create(LunixiConfig.builder().baseUrl(GATEWAY).keyId("test-key")
                .privateKey(Ed25519KeyPair.generate().privateKeyPem()).transport(transport).maxRetries(0).build())
                .sanction();
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
    void screenNameSendsTheBodyWithTheTokenOnly() {
        transport.enqueue(200, CLEAR);
        Map<String, Object> result = sanction.screenName(map("name", "Ali Veli", "subjectType",
                SanctionValues.SubjectType.PERSON, "riskDomains", List.of("sanctions", "pep"), "mode",
                SanctionValues.ScreenMode.SANCTIONS_PEP, "identifiers", List.of(map("type", "TCKN", "value", "1"))));
        assertEquals("scr_1", result.get("screeningRequestId"));
        assertTrue(SanctionClient.isClearScreening(result));
        RecordingTransport.Call call = transport.onlyApiCall();
        assertEquals("POST", call.method);
        assertEquals(BASE + "/screening/name", call.url);
        assertNotNull(call.headers.get("Authorization"));
        assertNull(call.headers.get(CanonicalRequest.HEADER_SIGNATURE), "sanction routes are not signed");
        assertNull(call.headers.get("Idempotency-Key"));
        assertEquals("Ali Veli", Json.parseObject(call.body).get("name"));
    }

    @Test
    void bodiesAreCheckedAgainstTheRouteTable() {
        assertThrows(ConfigurationException.class, () -> sanction.screenName(map("fullName", "Ali")));
        assertThrows(ConfigurationException.class, () -> sanction.screenName(map("name", "")));
        assertThrows(ConfigurationException.class, () -> sanction.screenName(map("name", "Ali", "riskDomains",
                List.of("sanctions", "terror"))));
        assertThrows(ConfigurationException.class, () -> sanction.screenName(map("name", "Ali", "mode",
                "wallet_risk")));
        assertThrows(ConfigurationException.class, () -> sanction.screenName(map("name", "Ali", "identifiers",
                List.of(map("type", "TCKN")))));
        assertThrows(ConfigurationException.class, () -> sanction.screenName(map("name", "Ali", "identifiers",
                List.of(map("type", "TCKN", "value", "1", "country", "TR")))));
        assertThrows(ConfigurationException.class, () -> sanction.screenWallet(map("walletAddress", "0xabc",
                "mode", "full_risk")));
        assertThrows(ConfigurationException.class, () -> sanction.screenTransaction(map("amount", "100")));
        assertThrows(ConfigurationException.class, () -> sanction.screenTransaction(map("parties", List.of())));
        assertThrows(ConfigurationException.class, () -> sanction.screenTransaction(map("parties",
                List.of(map("role", "payer")))));
        assertThrows(ConfigurationException.class, () -> sanction.screenPublicProcurementBan(map("onlyActive",
                true)));
        assertThrows(ConfigurationException.class, () -> sanction.registerMonitoring(map("externalCustomerId", "c1",
                "riskDomainScope", List.of("weather"))));
        assertThrows(ConfigurationException.class, () -> sanction.listMonitoringAlerts(map("severity", "high")));
        assertNothingSent();
    }

    @Test
    void transactionWithAccountsOnlyIsScreenable() {
        transport.enqueue(200, CLEAR);
        sanction.screenTransaction(map("accounts", List.of(map("accountType", "IBAN", "value", "TR00"))));
        assertEquals(BASE + "/screening/transaction", transport.onlyApiCall().url);
    }

    @Test
    void aScreeningThatDidNotRunThrows() {
        transport.enqueue(503, "{\"status\":\"failure\",\"code\":\"sanction.screen_name.failed\",\"message\":"
                + "\"no publication\",\"statusCode\":503}");
        assertThrows(ServiceUnavailableException.class, () -> sanction.screenName(map("name", "Ali")));
        transport.enqueue(200, "{\"status\":\"partial\",\"code\":\"sanction.partial\",\"statusCode\":200,\"data\":"
                + "{\"matches\":[]}}");
        LunixiApiException partial = assertThrows(LunixiApiException.class,
                () -> sanction.screenWallet(map("walletAddress", "0xabc")));
        assertEquals("sanction.partial", partial.code().orElseThrow());
        transport.enqueue(200, "{\"status\":\"failure\",\"code\":\"sanction.dataset.failed\",\"data\":null}");
        assertThrows(LunixiApiException.class, () -> sanction.getDatasetFreshness());
    }

    @Test
    void clearReadingRule() {
        assertTrue(SanctionClient.isClearScreening(map("matches", List.of(), "coverageStatus", "COMPLETE")));
        assertTrue(SanctionClient.isClearScreening(map("hits", List.of(), "decision", "clear")),
                "account screening reports no coverage");
        assertFalse(SanctionClient.isClearScreening(null));
        assertFalse(SanctionClient.isClearScreening(map("matches", List.of(map("entityId", "e1")),
                "coverageStatus", "COMPLETE")));
        assertFalse(SanctionClient.isClearScreening(map("publicationHits", List.of(map("x", 1)))));
        assertFalse(SanctionClient.isClearScreening(map("matches", List.of(), "coverageStatus", "PARTIAL")));
        assertFalse(SanctionClient.isClearScreening(map("matches", List.of(), "coverageStatus", "DEGRADED")));
        assertFalse(SanctionClient.isClearScreening(map("matches", List.of(), "coverageStatus", null)),
                "a replay without coverage is not clear");
        assertFalse(SanctionClient.isClearScreening(map("isSanctioned", true)));
        assertFalse(SanctionClient.isClearScreening(map("isBlacklisted", true)));
        assertFalse(SanctionClient.isClearScreening(map("matched", true)));
        assertFalse(SanctionClient.isClearScreening(map("caseRequired", true)));
        assertFalse(SanctionClient.isClearScreening(map("decision", "REVIEW")));
        assertFalse(SanctionClient.isClearScreening(map("decision", "block")));
    }

    @Test
    void monitoringSendsTheProfileAsJsonText() {
        transport.enqueue(200, "{\"status\":\"success\",\"data\":{\"monitoredEntityId\":\"me_1\"}}");
        assertEquals("me_1", sanction.registerMonitoring(map("externalCustomerId", "c1", "profile",
                map("name", "Ali Veli"))).get("monitoredEntityId"));
        Map<String, Object> body = Json.parseObject(transport.onlyApiCall().body);
        assertEquals("{\"name\":\"Ali Veli\"}", body.get("profileJson"));
        assertFalse(body.containsKey("profile"));
        assertThrows(ConfigurationException.class, () -> sanction.registerMonitoring(map("externalCustomerId", "c1",
                "profile", map("name", "A"), "profileJson", "{}")));
    }

    @Test
    void listsAndIntelligenceQueries() {
        transport.enqueue(200, "{\"status\":\"success\",\"data\":{\"items\":[{\"id\":\"h1\"}],\"nextCursor\":\"c2\","
                + "\"runsToday\":3}}");
        transport.enqueue(200, "{\"status\":\"success\",\"data\":{\"items\":[]},\"pageInfo\":{\"hasMore\":false,"
                + "\"nextCursor\":null,\"totalCount\":7}}");
        transport.enqueue(200, "{\"status\":\"success\",\"data\":{\"paths\":[]}}");
        SanctionPage history = sanction.listScreeningHistory(map("limit", 20));
        assertEquals("c2", history.nextCursor().orElseThrow());
        assertTrue(history.hasMore());
        assertEquals(3L, history.raw().get("runsToday"));
        SanctionPage alerts = sanction.listMonitoringAlerts(map("status", "open", "includeTotal", true));
        assertFalse(alerts.hasMore());
        assertEquals(7L, alerts.totalCount().orElseThrow());
        sanction.getIndirectExposure("ent 1", 2, List.of("e1", " e2 "));
        List<RecordingTransport.Call> calls = transport.apiCalls();
        assertEquals(BASE + "/screening/history?limit=20", calls.get(0).url);
        assertEquals(BASE + "/monitoring/alerts?status=open&includeTotal=true", calls.get(1).url);
        assertEquals(BASE + "/intelligence/indirect-exposure/ent%201?maxDepth=2&entityIds=e1%2Ce2", calls.get(2).url);
        calls.forEach(call -> assertNull(call.headers.get(CanonicalRequest.HEADER_SIGNATURE)));
        assertThrows(ConfigurationException.class, () -> sanction.getEntityGraph(" "));
    }

    @Test
    void routeTableShape() {
        assertEquals(19, SanctionRoutes.ROUTES.size());
        assertTrue(SanctionRoutes.route("screenName").screening());
        assertFalse(SanctionRoutes.route("registerMonitoring").screening());
        assertEquals(List.of("parties", "accounts"), SanctionRoutes.route("screenTransaction").anyOf());
        assertEquals(List.of("entityName", "tckn", "vkn"), SanctionRoutes.route("screenPublicProcurementBan").anyOf());
        assertEquals(SanctionValues.RiskDomain.ALL, SanctionRoutes.VALUE_SETS.get("screenName.riskDomains"));
    }
}
