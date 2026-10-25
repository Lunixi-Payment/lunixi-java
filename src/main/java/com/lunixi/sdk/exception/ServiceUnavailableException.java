package com.lunixi.sdk.exception;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * {@code 503} — the service, or the backend that answers for its quota, is
 * unavailable. Carries {@code Retry-After} when the gateway sent it.
 */
public class ServiceUnavailableException extends LunixiApiException {

    private static final long serialVersionUID = 1L;

    private final Duration retryAfter;

    public ServiceUnavailableException(String message, int statusCode, String code,
                                       Map<String, Object> response, String requestId, Duration retryAfter) {
        super(message, statusCode, code, response, requestId);
        this.retryAfter = retryAfter;
    }

    /** How long to wait before retrying, when the gateway said so. */
    public Optional<Duration> retryAfter() {
        return Optional.ofNullable(retryAfter);
    }
}
