package com.lunixi.sdk.exception;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * {@code 429} — the account's request quota is exhausted.
 *
 * <p>{@link #retryAfter()} is the {@code Retry-After} header when the gateway
 * sent one. It is empty rather than guessed: the gateway omits the header when
 * it does not know the number, and a client that invented a value would retry
 * at the wrong rate.
 */
public class RateLimitException extends LunixiApiException {

    private static final long serialVersionUID = 1L;

    private final Duration retryAfter;

    public RateLimitException(String message, int statusCode, String code,
                              Map<String, Object> response, String requestId, Duration retryAfter) {
        super(message, statusCode, code, response, requestId);
        this.retryAfter = retryAfter;
    }

    /** How long to wait before retrying, when the gateway said so. */
    public Optional<Duration> retryAfter() {
        return Optional.ofNullable(retryAfter);
    }
}
