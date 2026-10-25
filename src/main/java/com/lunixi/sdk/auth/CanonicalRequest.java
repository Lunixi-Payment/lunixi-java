package com.lunixi.sdk.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import com.lunixi.sdk.exception.SignatureException;

/**
 * Builds the string that is Ed25519-signed for every signed request, and the
 * {@code Digest} header value that goes with a body.
 *
 * <p>The canonical string is, byte for byte:
 *
 * <pre>
 * &lt;METHOD&gt;\n
 * &lt;URL&gt;\n
 * X-Date:&lt;value&gt;\n
 * X-Nonce:&lt;value&gt;
 * [\nDigest:&lt;value&gt;]
 * </pre>
 *
 * <p>Lines are joined with {@code "\n"}, the method is upper-cased, and the
 * {@code Digest} line is present only when a {@code Digest} header is sent —
 * the gateway appends that line only in that case, so adding it for a bodyless
 * request makes every signature fail. {@code URL} is the path exactly as sent,
 * query string included.
 */
public final class CanonicalRequest {

    public static final String HEADER_KEY_ID = "X-Key-Id";
    public static final String HEADER_DATE = "X-Date";
    public static final String HEADER_NONCE = "X-Nonce";
    public static final String HEADER_SIGNATURE = "X-Signature";
    public static final String HEADER_DIGEST = "Digest";

    /** Prefix of the {@code Digest} header: {@code SHA-256=<base64(sha256(body))>}. */
    public static final String DIGEST_PREFIX = "SHA-256=";

    private CanonicalRequest() {
    }

    /**
     * Builds the canonical string to sign.
     *
     * @param method HTTP method, any case
     * @param url    the request path exactly as sent, including the query string
     * @param date   the {@code X-Date} header value
     * @param nonce  the {@code X-Nonce} header value, unique per request
     * @param digest the {@code Digest} header value, or {@code null} for a
     *               bodyless request
     * @return the string to sign
     */
    public static String build(String method, String url, String date, String nonce, String digest) {
        StringBuilder canonical = new StringBuilder(128)
                .append(method.toUpperCase(java.util.Locale.ROOT)).append('\n')
                .append(url).append('\n')
                .append(HEADER_DATE).append(':').append(date).append('\n')
                .append(HEADER_NONCE).append(':').append(nonce);
        if (digest != null && !digest.isEmpty()) {
            canonical.append('\n').append(HEADER_DIGEST).append(':').append(digest);
        }
        return canonical.toString();
    }

    /**
     * Computes the {@code Digest} header value for a request body.
     *
     * <p>The gateway recomputes this hash over its own re-serialisation of the
     * body it parsed, so the bytes handed in here must be the compact form the
     * SDK's JSON writer produces. A pretty-printed or re-ordered body hashes
     * differently and is rejected with {@code INVALID_DIGEST}.
     *
     * @param rawBody the exact request body text, or {@code null} / empty for a
     *                bodyless request
     * @return the header value, or {@code null} when there is no body
     */
    public static String digestForBody(String rawBody) {
        if (rawBody == null || rawBody.isEmpty()) {
            return null;
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(rawBody.getBytes(StandardCharsets.UTF_8));
            return DIGEST_PREFIX + Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException absent) {
            throw new SignatureException("SHA-256 bu JVM'de yok / SHA-256 is unavailable on this JVM.", absent);
        }
    }
}
