package com.lunixi.sdk.exception;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Physical POS: the outcome of a payment write (start, reversal, cancel) is unknown.
 *
 * <p>Raised when any attempt of the call (the SDK retries with the same key) ended without a usable answer: no HTTP
 * answer (connection failure, timeout), a {@code 2xx} without a readable payment, or a {@code 5xx} that the gateway
 * did not mark as "nothing was started". The last error of the chain is the {@link #getCause() cause} — even a
 * {@code 429} or {@code 409} after such an attempt is reported here, because an earlier attempt may have created
 * the payment. The payment may or may not exist. Retry with the SAME
 * {@link #idempotencyKey()} — a recorded payment is returned ({@code replayed}), a missing one is created — or find
 * it with {@code listPayments} and your {@code merchantReference}. Never retry with a new key: that can start a
 * second payment.
 */
public class PhysicalPosOutcomeUnknownException extends LunixiApiException {

    private static final long serialVersionUID = 1L;

    private final String idempotencyKey;
    private final Duration retryAfter;

    public PhysicalPosOutcomeUnknownException(String message, int statusCode, String code,
                                              Map<String, Object> response, String requestId,
                                              String idempotencyKey, Duration retryAfter) {
        super(message, statusCode, code, response, requestId);
        this.idempotencyKey = idempotencyKey;
        this.retryAfter = retryAfter;
    }

    /** The {@code Idempotency-Key} that was sent; retry with exactly this value. */
    public String idempotencyKey() {
        return idempotencyKey;
    }

    /** How long to wait before retrying, when the gateway said so. */
    public Optional<Duration> retryAfter() {
        return Optional.ofNullable(retryAfter);
    }
}
