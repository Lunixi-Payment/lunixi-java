package com.lunixi.sdk.webhook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.lunixi.sdk.exception.WebhookVerificationException;
import com.lunixi.sdk.fraud.FraudEvents;
import com.lunixi.sdk.json.Json;

/**
 * Verification is checked against the <b>shared</b> signature vectors — the
 * same file the other language SDKs read. A vector suite that each language
 * writes for itself proves only that the language agrees with itself; reading
 * one file is what proves the implementations agree with each other, and with
 * the sender.
 */
class WebhookVerifierTest {

    /**
     * Candidate locations of the shared vector file, in order of preference: the
     * SDK's own copy (byte-identical to the Node, PHP and Python copies, so the
     * suite also runs outside the monorepo), then the shared monorepo copies. The
     * suite fails loudly if none exists rather than quietly passing with nothing
     * verified.
     */
    private static final List<String> VECTOR_PATHS = List.of(
            "src/test/resources/webhook-v2-vectors.json",
            "../fixtures/webhook-v2-vectors.json",
            "../node/test/fixtures/webhook-v2-vectors.json");

    private final WebhookVerifier verifier = new WebhookVerifier();

    @Test
    @DisplayName("Paylaşılan v2 vektörlerinin TAMAMI doğrulanır")
    void verifiesEverySharedVector() throws IOException {
        List<Map<String, Object>> vectors = loadVectors();
        assertFalse(vectors.isEmpty(), "Paylaşılan vektör dosyası boş.");

        for (Map<String, Object> vector : vectors) {
            String name = String.valueOf(vector.get("name"));
            String rawBody = String.valueOf(vector.get("rawBody"));
            String expected = String.valueOf(vector.get("expectedV2"));
            Map<String, String> headers = headersFor(vector, "v2=" + expected);

            // Tolerance off: the vectors carry fixed timestamps, so a live window
            // check would reject them for their age rather than their signature.
            WebhookEvent event = verifier.verify(
                    rawBody, headers, String.valueOf(vector.get("secret")), null);

            assertEquals(vector.get("eventId"), event.id(), name + ": olay kimliği");
            assertEquals(vector.get("timestamp"), event.timestamp(), name + ": zaman damgası");
            assertFalse(event.type().isEmpty(), name + ": olay tipi");
        }
    }

    @Test
    @DisplayName("Sır döndürme penceresi: iki v2 öğesinden BİRİ eşleşirse kabul edilir")
    void acceptsWhenAnyV2ItemMatches() throws IOException {
        Map<String, Object> vector = loadVectors().get(0);
        String expected = String.valueOf(vector.get("expectedV2"));
        // During a rotation a delivery carries one item per valid secret; the
        // endpoint must accept the delivery without being told which is current.
        Map<String, String> headers = headersFor(vector, "v2=" + "0".repeat(64) + ",v2=" + expected);

        WebhookEvent event = verifier.verify(
                String.valueOf(vector.get("rawBody")), headers, String.valueOf(vector.get("secret")), null);

        assertEquals(vector.get("eventId"), event.id());
    }

    @Test
    @DisplayName("NEGATİF: gövdenin tek baytı değişirse doğrulama BAŞARISIZ")
    void rejectsATamperedBody() throws IOException {
        Map<String, Object> vector = loadVectors().get(0);
        String tampered = String.valueOf(vector.get("rawBody")).replace("TRY", "USD");
        Map<String, String> headers = headersFor(vector, "v2=" + vector.get("expectedV2"));

        assertThrows(WebhookVerificationException.class, () -> verifier.verify(
                tampered, headers, String.valueOf(vector.get("secret")), null));
    }

    @Test
    @DisplayName("NEGATİF: v2 öğesi olmayan başlık DAİMA reddedilir")
    void rejectsAHeaderWithoutAV2Item() throws IOException {
        Map<String, Object> vector = loadVectors().get(0);
        // An unknown scheme is ignored, not accepted: an old verifier must reject
        // a delivery it cannot check instead of waving it through.
        Map<String, String> headers = headersFor(vector, "v3=" + vector.get("expectedV2"));

        assertThrows(WebhookVerificationException.class, () -> verifier.verify(
                String.valueOf(vector.get("rawBody")), headers, String.valueOf(vector.get("secret")), null));
    }

    @Test
    @DisplayName("NEGATİF: yanlış sır reddedilir")
    void rejectsAWrongSecret() throws IOException {
        Map<String, Object> vector = loadVectors().get(0);
        Map<String, String> headers = headersFor(vector, "v2=" + vector.get("expectedV2"));

        assertThrows(WebhookVerificationException.class, () -> verifier.verify(
                String.valueOf(vector.get("rawBody")), headers, "whsec_wrong_secret", null));
    }

    @Test
    @DisplayName("NEGATİF: başlık eksikse ya da sır tanımsızsa reddedilir")
    void rejectsMissingHeadersAndSecret() throws IOException {
        Map<String, Object> vector = loadVectors().get(0);
        String rawBody = String.valueOf(vector.get("rawBody"));
        String secret = String.valueOf(vector.get("secret"));

        assertThrows(WebhookVerificationException.class,
                () -> verifier.verify(rawBody, Map.of(), secret, null));
        assertThrows(WebhookVerificationException.class,
                () -> verifier.verify(rawBody, headersFor(vector, "v2=x"), "", null));
    }

    @Test
    @DisplayName("Zaman damgası penceresi: taze kabul, bayat reddedilir")
    void enforcesTheReplayWindow() {
        String secret = "whsec_" + "a".repeat(32);
        String eventId = "3b241101-e2bb-4255-8caf-4136c566a962";
        String body = "{\"id\":\"" + eventId + "\",\"type\":\"" + FraudEvents.ALERT_TRIGGERED
                + "\",\"data\":{\"ruleId\":\"rule_1\"}}";

        String fresh = Instant.now().truncatedTo(ChronoUnit.MILLIS).toString();
        WebhookEvent event = verifier.verify(body, signedHeaders(secret, eventId, fresh, body), secret);
        assertEquals(FraudEvents.ALERT_TRIGGERED, event.type());

        String stale = Instant.now().minus(Duration.ofHours(2)).toString();
        assertThrows(WebhookVerificationException.class,
                () -> verifier.verify(body, signedHeaders(secret, eventId, stale, body), secret));
    }

    @Test
    @DisplayName("Olay tipi ve kimliği İMZALI gövdeden okunur, başlıktan değil")
    void readsTypeAndIdFromTheSignedBody() {
        String secret = "whsec_" + "b".repeat(32);
        String eventId = "7c9e6679-7425-40de-944b-e07fc1f90ae7";
        String timestamp = Instant.now().truncatedTo(ChronoUnit.MILLIS).toString();
        String body = "{\"id\":\"" + eventId + "\",\"type\":\"" + FraudEvents.FLOW_ASYNC_COMPLETED
                + "\",\"data\":{\"decisionRequestId\":\"dr_1\"}}";

        Map<String, String> headers = new java.util.HashMap<>(signedHeaders(secret, eventId, timestamp, body));
        // The type header is NOT covered by the signature, so a value that
        // disagrees with the body must not win: acting on it would mean acting on
        // an unsigned claim about what the event is.
        headers.put(WebhookVerifier.HEADER_EVENT_TYPE, "fraud.service.degraded");

        WebhookEvent event = verifier.verify(body, headers, secret);

        assertEquals(FraudEvents.FLOW_ASYNC_COMPLETED, event.type());
        assertEquals("dr_1", event.get("decisionRequestId").orElseThrow());
    }

    @Test
    @DisplayName("Fraud olay kataloğu yedi tiptir ve hepsi fraud. önekli")
    void fraudCatalogueHoldsSevenTypes() {
        assertEquals(7, FraudEvents.all().size());
        assertEquals(FraudEvents.all().size(), java.util.Set.copyOf(FraudEvents.all()).size());
        for (String type : FraudEvents.all()) {
            assertTrue(type.startsWith("fraud."), type);
        }
    }

    // ───────────────────────────── helpers ─────────────────────────────

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> loadVectors() throws IOException {
        for (String candidate : VECTOR_PATHS) {
            Path path = Path.of(candidate);
            if (Files.exists(path)) {
                Object parsed = Json.parse(Files.readString(path, StandardCharsets.UTF_8));
                List<Map<String, Object>> vectors = new ArrayList<>();
                for (Object row : (List<Object>) parsed) {
                    vectors.add((Map<String, Object>) row);
                }
                return vectors;
            }
        }
        throw new IOException("Paylaşılan webhook vektör dosyası bulunamadı: " + VECTOR_PATHS);
    }

    private static Map<String, String> headersFor(Map<String, Object> vector, String signatureHeader) {
        return Map.of(
                WebhookVerifier.HEADER_SIGNATURE, signatureHeader,
                WebhookVerifier.HEADER_TIMESTAMP, String.valueOf(vector.get("timestamp")),
                WebhookVerifier.HEADER_EVENT_ID, String.valueOf(vector.get("eventId")));
    }

    private static Map<String, String> signedHeaders(String secret, String eventId, String timestamp, String body) {
        return Map.of(
                WebhookVerifier.HEADER_SIGNATURE, "v2=" + hmacHex(secret, eventId + "." + timestamp + "." + body),
                WebhookVerifier.HEADER_TIMESTAMP, timestamp,
                WebhookVerifier.HEADER_EVENT_ID, eventId);
    }

    private static String hmacHex(String secret, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
