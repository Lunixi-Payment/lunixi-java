package com.lunixi.sdk.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.lunixi.sdk.json.Json;

/**
 * The canonical string is a byte-for-byte contract with the gateway: one extra
 * line, one lower-case method or one wrong separator makes every signature fail
 * verification, and the failure looks like a bad key rather than a bad string.
 */
class CanonicalRequestTest {

    @Test
    @DisplayName("Gövdesiz istekte Digest satırı YOKTUR")
    void omitsDigestLineForBodylessRequest() {
        String canonical = CanonicalRequest.build(
                "post", "/api/v1/auth/token", "2026-10-11T08:00:00Z", "abc123", null);

        assertEquals(
                "POST\n/api/v1/auth/token\nX-Date:2026-10-11T08:00:00Z\nX-Nonce:abc123",
                canonical);
        assertTrue(!canonical.contains("Digest"));
    }

    @Test
    @DisplayName("Gövdeli istekte Digest satırı SONA eklenir")
    void appendsDigestLineForBodyRequest() {
        String canonical = CanonicalRequest.build(
                "POST", "/api/v1/fraud/events/transactions", "2026-10-11T08:00:00Z", "abc123",
                "SHA-256=Zm9v");

        assertEquals(
                "POST\n/api/v1/fraud/events/transactions\nX-Date:2026-10-11T08:00:00Z\nX-Nonce:abc123"
                        + "\nDigest:SHA-256=Zm9v",
                canonical);
    }

    @Test
    @DisplayName("Metot büyük harfe çevrilir, yol aynen imzalanır")
    void upperCasesMethodAndKeepsUrlVerbatim() {
        String canonical = CanonicalRequest.build(
                "get", "/api/v1/fraud/decision-logs?limit=1&paymentId=pay_1",
                "2026-10-11T08:00:00Z", "n", null);

        assertTrue(canonical.startsWith("GET\n/api/v1/fraud/decision-logs?limit=1&paymentId=pay_1\n"));
    }

    @Test
    @DisplayName("Digest = SHA-256=base64(sha256(gövde))")
    void computesDigestOverRawBody() throws Exception {
        String body = "{\"eventId\":\"evt_1\"}";
        String expected = "SHA-256=" + Base64.getEncoder().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));

        assertEquals(expected, CanonicalRequest.digestForBody(body));
    }

    @Test
    @DisplayName("Gövde yoksa Digest üretilmez")
    void producesNoDigestWithoutBody() {
        assertNull(CanonicalRequest.digestForBody(null));
        assertNull(CanonicalRequest.digestForBody(""));
    }

    @Test
    @DisplayName("NEGATİF: kompakt olmayan JSON başka bir özet üretir")
    void prettyPrintedBodyProducesADifferentDigest() {
        // The gateway hashes its own compact re-serialisation of the body. A
        // pretty-printed body therefore hashes to something else and is rejected
        // as an invalid digest — which is why the SDK owns its serialiser instead
        // of accepting whatever a mapper emits.
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", "evt_1");
        body.put("eventType", "card_payment");

        String compact = Json.stringify(body);
        String pretty = "{\n  \"eventId\": \"evt_1\",\n  \"eventType\": \"card_payment\"\n}";

        assertEquals("{\"eventId\":\"evt_1\",\"eventType\":\"card_payment\"}", compact);
        assertNotEquals(CanonicalRequest.digestForBody(compact), CanonicalRequest.digestForBody(pretty));
    }

    @Test
    @DisplayName("NEGATİF: anahtar sırası değişirse özet değişir")
    void keyOrderChangesTheDigest() {
        String first = Json.stringify(new LinkedHashMap<>(Map.of("a", 1)));
        String reordered = "{\"a\":1,\"b\":2}";
        String different = "{\"b\":2,\"a\":1}";

        assertNotEquals(CanonicalRequest.digestForBody(reordered), CanonicalRequest.digestForBody(different));
        assertTrue(first.startsWith("{"));
    }
}
