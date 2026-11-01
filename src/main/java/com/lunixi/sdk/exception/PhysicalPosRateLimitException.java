package com.lunixi.sdk.exception;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Physical POS: {@code 429 RATE_LIMITED} — the per-key write limit of the physical POS surface was reached.
 *
 * <p>{@link #retryAfter()} is the {@code Retry-After} header, or the envelope's {@code details.retryAfterSeconds}
 * when the header is missing. Retry a payment write after that delay with the SAME {@code Idempotency-Key}.
 */
public class PhysicalPosRateLimitException extends RateLimitException {

    private static final long serialVersionUID = 1L;

    private final String idempotencyKey;

    public PhysicalPosRateLimitException(String message, int statusCode, String code,
                                         Map<String, Object> response, String requestId, Duration retryAfter) {
        this(message, statusCode, code, response, requestId, retryAfter, null);
    }

    public PhysicalPosRateLimitException(String message, int statusCode, String code,
                                         Map<String, Object> response, String requestId, Duration retryAfter,
                                         String idempotencyKey) {
        super(message, statusCode, code, response, requestId, retryAfter);
        this.idempotencyKey = idempotencyKey;
    }

    /** The {@code Idempotency-Key} the limited write carried; retry after the delay with exactly this value. */
    public Optional<String> idempotencyKey() {
        return Optional.ofNullable(idempotencyKey);
    }
}
