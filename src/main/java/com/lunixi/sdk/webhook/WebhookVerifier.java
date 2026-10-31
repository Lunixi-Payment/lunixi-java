package com.lunixi.sdk.webhook;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.lunixi.sdk.exception.WebhookVerificationException;
import com.lunixi.sdk.json.Json;
import com.lunixi.sdk.json.JsonParseException;

/**
 * Verifies an inbound webhook.
 *
 * <p>The {@code x-lunixi-signature} header is a comma-separated list of
 * {@code scheme=value} items. There is a single signature scheme, {@code v2};
 * the list form exists for the grace window after a secret is rotated, during
 * which a delivery carries one item per valid secret. A delivery is accepted
 * when <i>any</i> {@code v2} item matches, which is what makes a rotation
 * invisible to your endpoint.
 *
 * <pre>
 * v2 = hex( HMAC_SHA256( secret, "&lt;eventId&gt;.&lt;timestamp&gt;.&lt;rawBody&gt;" ) )
 * </pre>
 *
 * <h2>Pass the raw body, never a re-serialised one</h2>
 * The signature is computed over the exact bytes that were sent. If your
 * framework has already parsed the JSON, serialising it again produces
 * different bytes — different key order, different spacing, different escaping
 * — and verification fails on a payload that is in fact genuine. Capture the
 * body as a string or byte array before any parsing.
 *
 * <h2>Fail-closed</h2>
 * Every discrepancy throws. A header with no {@code v2} item fails too: an
 * unknown scheme is ignored rather than accepted, so a delivery signed only
 * with some future scheme is rejected by an old verifier instead of being
 * waved through unverified.
 *
 * <p>Instances are stateless and safe to share between threads.
 */
public final class WebhookVerifier {

    public static final String HEADER_SIGNATURE = "x-lunixi-signature";
    public static final String HEADER_TIMESTAMP = "x-lunixi-signature-timestamp";
    public static final String HEADER_EVENT_ID = "x-lunixi-event-id";
    public static final String HEADER_EVENT_TYPE = "x-lunixi-event-type";

    /** The default replay window for the signature timestamp. */
    public static final Duration DEFAULT_TOLERANCE = Duration.ofMinutes(5);

    private static final String SCHEME_V2 = "v2";

    /** Strict current merchant envelope. Generic legacy verify remains available separately. */
    public WebhookEvent verifyMerchant(String rawBody, Map<String, String> headers, String secret) {
        if (rawBody == null || rawBody.getBytes(StandardCharsets.UTF_8).length > 1_048_576) { throw new WebhookVerificationException("Webhook body is missing or too large."); }
        Map<String, String> normal = normalise(headers);
        WebhookEvent event = verify(rawBody, normal, secret);
        Object id = event.envelope().get("id"), type = event.envelope().get("type"), data = event.envelope().get("data");
        if (!(id instanceof String) || ((String) id).isBlank() || !id.equals(normal.get(HEADER_EVENT_ID)) || !(type instanceof String) || ((String) type).isBlank() || !(data instanceof Map)) { throw new WebhookVerificationException("Signed merchant envelope identity/type/data are required."); }
        String headerType = normal.get(HEADER_EVENT_TYPE);
        if (headerType != null && !headerType.equals(type)) { throw new WebhookVerificationException("Webhook type conflicts with its signed body."); }
        return new WebhookEvent(event.id(), event.type(), event.timestamp(), event.data(), event.envelope(), event.rawBodyHash(), true);
    }

    /**
     * Verifies a delivery with the default five-minute replay window.
     *
     * @param rawBody the exact request body, unparsed
     * @param headers the request headers; names are matched without regard to case
     * @param secret  the endpoint's signing secret
     * @return the verified event
     * @throws WebhookVerificationException on any verification failure
     */
    public WebhookEvent verify(String rawBody, Map<String, String> headers, String secret) {
        return verify(rawBody, headers, secret, DEFAULT_TOLERANCE);
    }

    /**
     * Verifies a delivery.
     *
     * @param rawBody   the exact request body, unparsed
     * @param headers   the request headers; names are matched without regard to case
     * @param secret    the endpoint's signing secret
     * @param tolerance how far the signature timestamp may be from now;
     *                  {@code null} skips the check. Keep the check on: it is
     *                  what stops a captured delivery from being replayed later.
     * @return the verified event
     * @throws WebhookVerificationException on any verification failure
     */
    public WebhookEvent verify(String rawBody, Map<String, String> headers, String secret, Duration tolerance) {
        if (secret == null || secret.isEmpty()) {
            throw new WebhookVerificationException(
                    "Webhook sırrı tanımlı değil / the webhook secret is not configured.");
        }
        Map<String, String> normalised = normalise(headers);
        String signatureHeader = normalised.getOrDefault(HEADER_SIGNATURE, "");
        String timestamp = normalised.getOrDefault(HEADER_TIMESTAMP, "");
        String eventId = normalised.getOrDefault(HEADER_EVENT_ID, "");
        String headerEventType = normalised.getOrDefault(HEADER_EVENT_TYPE, "");

        if (signatureHeader.isEmpty() || timestamp.isEmpty() || eventId.isEmpty()) {
            throw new WebhookVerificationException(
                    "İmza, zaman damgası veya olay kimliği başlığı eksik "
                            + "/ the signature, timestamp or event id header is missing.");
        }
        if (tolerance != null) {
            assertFreshTimestamp(timestamp, tolerance);
        }

        List<String> candidates = v2Items(signatureHeader);
        if (candidates.isEmpty()) {
            throw new WebhookVerificationException(
                    "İmza başlığında v2 öğesi yok / the signature header carries no v2 item.");
        }

        String body = rawBody == null ? "" : rawBody;
        String expected = hmacSha256Hex(secret, eventId + "." + timestamp + "." + body);
        boolean matched = false;
        for (String candidate : candidates) {
            // Constant-time comparison: a byte-by-byte early exit leaks how much
            // of a guessed signature was right, which is enough to find the rest.
            if (MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    candidate.getBytes(StandardCharsets.UTF_8))) {
                matched = true;
            }
        }
        if (!matched) {
            throw new WebhookVerificationException("İmza uyuşmuyor / the signature does not match.");
        }

        Map<String, Object> envelope;
        try {
            envelope = Json.parseObject(body);
        } catch (JsonParseException notJson) {
            envelope = Map.of();
        }

        // The type and id inside the body are the AUTHENTICATED ones: the body is
        // covered by the signature, the x-lunixi-event-type header is not. The
        // header is only a fallback for a flat payload.
        String type = stringOr(envelope.get("type"), headerEventType);
        String id = stringOr(envelope.get("id"), eventId);
        Object data = envelope.get("data");
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = data instanceof Map ? (Map<String, Object>) data : envelope;

        return new WebhookEvent(id, type, timestamp, payload, envelope, bodyHash(body), false);
    }

    private static String bodyHash(String body) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8))); }
        catch (GeneralSecurityException error) { throw new WebhookVerificationException("SHA-256 is required for authenticated event receipts."); }
    }

    private static List<String> v2Items(String header) {
        List<String> values = new ArrayList<>();
        for (String part : header.split(",")) {
            String item = part.trim();
            int separator = item.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            if (SCHEME_V2.equals(item.substring(0, separator).trim())) {
                values.add(item.substring(separator + 1).trim());
            }
        }
        return values;
    }

    private static String hmacSha256Hex(String secret, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException failure) {
            throw new WebhookVerificationException(
                    "HMAC-SHA256 hesaplanamadı / could not compute HMAC-SHA256: " + failure.getMessage());
        }
    }

    private static void assertFreshTimestamp(String timestamp, Duration tolerance) {
        Instant sentAt;
        try {
            sentAt = OffsetDateTime.parse(timestamp).toInstant();
        } catch (DateTimeParseException notAnInstant) {
            throw new WebhookVerificationException(
                    "Zaman damgası geçerli bir tarih değil / the timestamp is not a valid date.");
        }
        if (Duration.between(sentAt, Instant.now()).abs().compareTo(tolerance) > 0) {
            throw new WebhookVerificationException(
                    "Zaman damgası izin verilen aralığın dışında (yeniden oynatma koruması) "
                            + "/ the timestamp is outside the allowed window (replay protection).");
        }
    }

    private static Map<String, String> normalise(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return Map.of();
        }
        Map<String, String> out = new java.util.HashMap<>(headers.size());
        headers.forEach((key, value) -> {
            if (key != null && value != null) {
                String normal = key.toLowerCase(Locale.ROOT);
                if (out.containsKey(normal)) { throw new WebhookVerificationException("Duplicate webhook header name."); }
                out.put(normal, value);
            }
        });
        return out;
    }

    private static String stringOr(Object value, String fallback) {
        return value instanceof String && !((String) value).isEmpty() ? (String) value : fallback;
    }
}
