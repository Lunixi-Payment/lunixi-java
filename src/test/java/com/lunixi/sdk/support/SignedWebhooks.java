package com.lunixi.sdk.support;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.lunixi.sdk.webhook.WebhookEvent;
import com.lunixi.sdk.webhook.WebhookVerifier;

/** Signs a webhook body the way the platform does and verifies it, so tests get a real {@link WebhookEvent}. */
public final class SignedWebhooks {

    /** The endpoint secret of the fixtures. */
    public static final String SECRET = "whsec_wave_fixture";

    private SignedWebhooks() {
    }

    /** @return the v2 signature headers of {@code body} */
    public static Map<String, String> headers(String id, String type, String body) {
        try {
            String timestamp = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String signature = HexFormat.of().formatHex(
                    mac.doFinal((id + "." + timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
            return Map.of(WebhookVerifier.HEADER_EVENT_ID, id, WebhookVerifier.HEADER_EVENT_TYPE, type,
                    WebhookVerifier.HEADER_TIMESTAMP, timestamp, WebhookVerifier.HEADER_SIGNATURE,
                    "v2=" + signature);
        } catch (java.security.GeneralSecurityException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /**
     * @param id   the event id
     * @param type the event type
     * @param data the {@code data} object as JSON
     * @return the verified event of a merchant envelope carrying {@code data}
     */
    public static WebhookEvent verified(String id, String type, String data) {
        String body = "{\"id\":\"" + id + "\",\"type\":\"" + type + "\",\"organizationId\":\"org\",\"environment\":"
                + "\"TEST\",\"eventVersion\":1,\"schemaVersion\":\"v1\",\"createdAt\":\"2026-11-15T09:00:00.000Z\","
                + "\"data\":" + data + "}";
        return new WebhookVerifier().verify(body, headers(id, type, body), SECRET);
    }
}
