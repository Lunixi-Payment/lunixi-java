package com.lunixi.sdk.exception;

import java.util.Map;
import java.util.Optional;

/**
 * Physical POS: {@code 409 physical_pos.payment.void_after_settlement} — the payment is already in a device
 * end-of-day, so it can no longer be voided. Return the amount with a {@code REFUND} reversal on the same payment
 * (with a new {@code Idempotency-Key}).
 *
 * <p>{@link #allowedOperation()} is {@code details.allowedOperation} ({@code REFUND}), when the answer carried it.
 */
public class PhysicalPosVoidAfterSettlementException extends LunixiApiException {

    private static final long serialVersionUID = 1L;

    /** The machine-readable error code. */
    public static final String CODE = "physical_pos.payment.void_after_settlement";

    private final String allowedOperation;
    private final String idempotencyKey;

    public PhysicalPosVoidAfterSettlementException(String message, int statusCode, Map<String, Object> response,
                                                   String requestId, String allowedOperation, String idempotencyKey) {
        super(message, statusCode, CODE, response, requestId);
        this.allowedOperation = allowedOperation;
        this.idempotencyKey = idempotencyKey;
    }

    /** The operation the payment still allows ({@code REFUND}), when the answer carried it. */
    public Optional<String> allowedOperation() {
        return Optional.ofNullable(allowedOperation);
    }

    /** The {@code Idempotency-Key} the rejected reversal carried. */
    public Optional<String> idempotencyKey() {
        return Optional.ofNullable(idempotencyKey);
    }
}
