package com.lunixi.sdk.auth;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.lunixi.sdk.LunixiConfig;
import com.lunixi.sdk.exception.AuthenticationException;
import com.lunixi.sdk.http.Envelope;
import com.lunixi.sdk.http.HttpResult;

/**
 * Obtains and caches the access token.
 *
 * <p>{@code POST <authTokenPath>} is sent with no body and an Ed25519
 * signature ({@code X-Key-Id} / {@code X-Date} / {@code X-Nonce} /
 * {@code X-Signature}); the answer carries {@code access_token} and its
 * lifetime. The token is cached for slightly less than its real lifetime, so a
 * token that is about to expire is replaced before a call can fail on it.
 */
public final class TokenManager {

    /** Refresh this long before the token actually expires. */
    private static final Duration EXPIRY_SAFETY_MARGIN = Duration.ofSeconds(60);

    /** Used when the response reports no lifetime at all. */
    private static final Duration FALLBACK_TTL = Duration.ofSeconds(300);

    /** Never cache for less than this, so a short-lived token is still reused. */
    private static final Duration MINIMUM_TTL = Duration.ofSeconds(30);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final LunixiConfig config;
    private final Ed25519Signer signer;

    public TokenManager(LunixiConfig config, Ed25519Signer signer) {
        this.config = config;
        this.signer = signer;
    }

    /**
     * Returns a usable access token, fetching one when the cache has none.
     *
     * @return the bearer token
     * @throws AuthenticationException when a token cannot be obtained
     */
    public String getToken() {
        return getToken(false);
    }

    /**
     * Returns a usable access token.
     *
     * @param forceRefresh {@code true} to ignore the cache and fetch a fresh token
     * @return the bearer token
     * @throws AuthenticationException when a token cannot be obtained
     */
    public String getToken(boolean forceRefresh) {
        String cacheKey = config.tokenCacheKey();
        if (!forceRefresh) {
            Optional<String> cached = config.tokenStore().get(cacheKey);
            if (cached.isPresent()) {
                return cached.get();
            }
        }
        Fetched fetched = fetch();
        config.tokenStore().put(cacheKey, fetched.accessToken, fetched.ttl);
        return fetched.accessToken;
    }

    /**
     * Drops the cached token, so the next call fetches a fresh one. Used after a
     * {@code 401}, where the token was rotated or revoked.
     */
    public void invalidate() {
        config.tokenStore().remove(config.tokenCacheKey());
    }

    /**
     * The {@code X-Date} header value: an ISO-8601 instant with no fractional
     * seconds, which is the form the gateway's clock-skew check reads.
     *
     * @return the header value
     */
    public static String timestamp() {
        return Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
    }

    /**
     * A fresh nonce. Every signed request needs its own: the gateway remembers
     * the ones it has seen and rejects a repeat as a replay, so reusing a nonce
     * fails the second request rather than the first.
     *
     * @return 32 hex characters of randomness
     */
    public static String nonce() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private Fetched fetch() {
        String path = config.authTokenPath();
        String date = timestamp();
        String nonce = nonce();
        // Bodyless request → no Digest line in the canonical string.
        String canonical = CanonicalRequest.build("POST", path, date, nonce, null);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/json");
        headers.put("User-Agent", config.userAgent());
        headers.put(CanonicalRequest.HEADER_KEY_ID, config.keyId());
        headers.put(CanonicalRequest.HEADER_DATE, date);
        headers.put(CanonicalRequest.HEADER_NONCE, nonce);
        headers.put(CanonicalRequest.HEADER_SIGNATURE, signer.sign(canonical));

        HttpResult response = config.transport()
                .send("POST", config.baseUrl() + path, headers, null, config.timeout());
        Map<String, Object> body = response.json();

        if (!response.isHttpSuccess() || Envelope.isFailure(body)) {
            throw new AuthenticationException(
                    "Jeton alınamadı / could not obtain an access token (HTTP " + response.statusCode()
                            + Envelope.code(body).map(code -> ", " + code).orElse("") + ").",
                    response.statusCode(),
                    Envelope.code(body).orElse(null),
                    body,
                    response.header("x-request-id").orElse(null));
        }

        // The token fields arrive under `data`; a flat body is accepted too.
        Map<String, Object> payload = Envelope.data(body).containsKey("access_token")
                ? Envelope.data(body)
                : body;

        Object accessToken = payload.get("access_token");
        if (!(accessToken instanceof String) || ((String) accessToken).isEmpty()) {
            throw new AuthenticationException(
                    "Jeton yanıtında 'access_token' yok / the token response carried no 'access_token'.",
                    response.statusCode(),
                    Envelope.code(body).orElse(null),
                    body,
                    response.header("x-request-id").orElse(null));
        }
        return new Fetched((String) accessToken, resolveTtl(payload));
    }

    private static Duration resolveTtl(Map<String, Object> payload) {
        Duration lifetime = null;
        Object expiresIn = payload.get("expires_in");
        if (expiresIn instanceof Number) {
            lifetime = Duration.ofSeconds(((Number) expiresIn).longValue());
        } else {
            Object expiresAt = payload.get("expires_at");
            if (expiresAt instanceof Number) {
                lifetime = Duration.between(Instant.now(), Instant.ofEpochSecond(((Number) expiresAt).longValue()));
            }
        }
        if (lifetime == null || lifetime.isZero() || lifetime.isNegative()) {
            lifetime = FALLBACK_TTL;
        }
        Duration cacheable = lifetime.minus(EXPIRY_SAFETY_MARGIN);
        return cacheable.compareTo(MINIMUM_TTL) < 0 ? MINIMUM_TTL : cacheable;
    }

    private static final class Fetched {
        private final String accessToken;
        private final Duration ttl;

        Fetched(String accessToken, Duration ttl) {
            this.accessToken = accessToken;
            this.ttl = ttl;
        }
    }
}
