package com.lunixi.sdk.internal;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import com.lunixi.sdk.LunixiConfig;
import com.lunixi.sdk.auth.CanonicalRequest;
import com.lunixi.sdk.auth.Ed25519Signer;
import com.lunixi.sdk.auth.TokenManager;
import com.lunixi.sdk.exception.LunixiApiException;
import com.lunixi.sdk.exception.RateLimitException;
import com.lunixi.sdk.exception.ServiceUnavailableException;
import com.lunixi.sdk.exception.ValidationException;
import com.lunixi.sdk.http.Envelope;
import com.lunixi.sdk.http.HttpResult;
import com.lunixi.sdk.json.Json;

/**
 * Performs authenticated gateway requests: attaches the bearer token, adds the
 * per-request Ed25519 signature where a route needs one, sets the idempotency
 * key, retries only where retrying is safe, and turns an error response into
 * the matching exception.
 *
 * <h2>Retry policy</h2>
 * <ul>
 *   <li>{@code 401} → drop the token, re-authenticate, send once more. A
 *       rejected token means nothing was executed, so this cannot duplicate
 *       anything.</li>
 *   <li>transport failure or {@code 5xx} → retried <b>only</b> for a read
 *       ({@code GET} / {@code HEAD}) or a call carrying an idempotency key.
 *       Without one, a timed-out write may well have landed, and a blind retry
 *       would submit it a second time.</li>
 *   <li>{@code 4xx} → never retried.</li>
 * </ul>
 *
 * <p>This class is SDK internals. Call the gateway through
 * {@link com.lunixi.sdk.LunixiClient}; this type's signature may change in a
 * minor release.
 */
public final class ApiClient {

    private static final String JSON_CONTENT_TYPE = "application/json";

    private final LunixiConfig config;
    private final Ed25519Signer signer;
    private final TokenManager tokens;

    public ApiClient(LunixiConfig config, Ed25519Signer signer, TokenManager tokens) {
        this.config = config;
        this.signer = signer;
        this.tokens = tokens;
    }

    /**
     * Sends one request and returns the decoded response envelope.
     *
     * @param method  HTTP method
     * @param path    the path, beginning with {@code /}; signed exactly as sent
     * @param body    the request body value, or {@code null}
     * @param options per-call options
     * @return the decoded envelope
     * @throws LunixiApiException when the gateway reports an error
     */
    public Map<String, Object> request(String method, String path, Object body, RequestOptions options) {
        RequestOptions effective = options == null ? RequestOptions.none() : options;
        String upperMethod = method.toUpperCase(java.util.Locale.ROOT);
        String signedPath = path + queryString(effective.query());
        String url = config.baseUrl() + signedPath;

        // Serialised ONCE. The same bytes are hashed for the Digest header and
        // sent as the body; serialising twice would risk the two drifting apart,
        // and a digest over different bytes than were sent is a 401 with nothing
        // visibly wrong in the request.
        String rawBody = body == null ? null : Json.stringify(body);

        boolean idempotent = "GET".equals(upperMethod) || "HEAD".equals(upperMethod)
                || effective.idempotencyKey() != null;
        int maxAttempts = config.maxRetries() + 1;
        boolean reAuthenticated = false;

        for (int attempt = 1; ; attempt++) {
            Map<String, String> headers = buildHeaders(upperMethod, signedPath, rawBody, effective);

            HttpResult response;
            try {
                response = config.transport().send(upperMethod, url, headers, rawBody, config.timeout());
            } catch (LunixiApiException transportFailure) {
                if (idempotent && attempt < maxAttempts) {
                    backoff(attempt);
                    continue;
                }
                throw transportFailure;
            }

            if (response.isHttpSuccess()) {
                Map<String, Object> envelope = response.json();
                // A 2xx envelope can still report a failure; see Envelope.
                if (Envelope.isFailure(envelope)) {
                    throw toException(response, envelope);
                }
                return envelope;
            }

            if (response.statusCode() == 401 && !reAuthenticated) {
                tokens.invalidate();
                reAuthenticated = true;
                continue;
            }

            if (response.statusCode() >= 500 && idempotent && attempt < maxAttempts) {
                backoff(attempt);
                continue;
            }

            throw toException(response, response.json());
        }
    }

    private Map<String, String> buildHeaders(String method, String signedPath, String rawBody,
                                             RequestOptions options) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", JSON_CONTENT_TYPE);
        headers.put("User-Agent", config.userAgent());
        headers.put("Authorization", "Bearer " + tokens.getToken());
        if (rawBody != null) {
            headers.put("Content-Type", JSON_CONTENT_TYPE);
        }
        if (options.idempotencyKey() != null) {
            headers.put("Idempotency-Key", options.idempotencyKey());
        }

        if (options.stepUp()) {
            String date = TokenManager.timestamp();
            String nonce = TokenManager.nonce();
            String digest = CanonicalRequest.digestForBody(rawBody);
            String canonical = CanonicalRequest.build(method, signedPath, date, nonce, digest);

            headers.put(CanonicalRequest.HEADER_KEY_ID, config.keyId());
            headers.put(CanonicalRequest.HEADER_DATE, date);
            headers.put(CanonicalRequest.HEADER_NONCE, nonce);
            headers.put(CanonicalRequest.HEADER_SIGNATURE, signer.sign(canonical));
            if (digest != null) {
                headers.put(CanonicalRequest.HEADER_DIGEST, digest);
            }
        }
        return headers;
    }

    private static LunixiApiException toException(HttpResult response, Map<String, Object> envelope) {
        int status = response.statusCode();
        String code = Envelope.code(envelope).orElse(null);
        String requestId = response.header("x-request-id").orElse(null);
        Object message = envelope.get("message");
        String text = message instanceof String && !((String) message).isEmpty()
                ? (String) message
                : "İstek başarısız / the request failed (HTTP " + status + ").";

        if (status == 400 || status == 422) {
            return new ValidationException(text, status, code, envelope, requestId);
        }
        if (status == 429) {
            return new RateLimitException(text, status, code, envelope, requestId, retryAfter(response));
        }
        if (status == 503) {
            return new ServiceUnavailableException(text, status, code, envelope, requestId, retryAfter(response));
        }
        return new LunixiApiException(text, status, code, envelope, requestId);
    }

    /**
     * Reads {@code Retry-After}. Returns {@code null} when the header is absent:
     * the gateway omits it rather than guessing, and a client that invented a
     * delay would retry at the wrong rate.
     */
    private static Duration retryAfter(HttpResult response) {
        return response.header("retry-after")
                .map(value -> {
                    try {
                        return Duration.ofSeconds(Long.parseLong(value.trim()));
                    } catch (NumberFormatException notSeconds) {
                        return null;
                    }
                })
                .orElse(null);
    }

    private static String queryString(Map<String, String> query) {
        if (query.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(64);
        for (Map.Entry<String, String> entry : query.entrySet()) {
            out.append(out.length() == 0 ? '?' : '&')
                    .append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return out.toString();
    }

    private static void backoff(int attempt) {
        long delayMs = Math.min(2000L, 100L * (1L << (attempt - 1)));
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new LunixiApiException("Yeniden deneme kesildi / the retry was interrupted.", interrupted);
        }
    }
}
