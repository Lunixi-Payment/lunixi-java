package com.lunixi.sdk.exception;

import java.util.Map;
import java.util.Optional;

/**
 * Physical POS: the request carried card data — {@code physical_pos.payment.card_data_rejected}.
 *
 * <p>Card data (card number, security code, expiry, track or PIN data) is never sent through the API: the card is
 * read on the device. The SDK raises this before anything is sent when a request or filter key looks like card
 * data ({@link #statusCode()} {@code 0}), and maps the gateway's {@code 400} with the same code to it.
 * {@link #field()} is the field path only (for example {@code items[1].cvv}); the value is never echoed.
 */
public class PhysicalPosCardDataRejectedException extends ValidationException {

    private static final long serialVersionUID = 1L;

    /** The machine-readable error code shared by the SDK check and the gateway. */
    public static final String CODE = "physical_pos.payment.card_data_rejected";

    private final String field;
    private final String idempotencyKey;

    public PhysicalPosCardDataRejectedException(String message, int statusCode, Map<String, Object> response,
                                                String requestId, String field) {
        this(message, statusCode, response, requestId, field, null);
    }

    public PhysicalPosCardDataRejectedException(String message, int statusCode, Map<String, Object> response,
                                                String requestId, String field, String idempotencyKey) {
        super(message, statusCode, CODE, response, requestId);
        this.field = field;
        this.idempotencyKey = idempotencyKey;
    }

    /** The {@code Idempotency-Key} the rejected write carried, when it was a keyed write. */
    public Optional<String> idempotencyKey() {
        return Optional.ofNullable(idempotencyKey);
    }

    /** The path of the rejected field (never its value), when known. */
    public Optional<String> field() {
        return Optional.ofNullable(field);
    }
}
