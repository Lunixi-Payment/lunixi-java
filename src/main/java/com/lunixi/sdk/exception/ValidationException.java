package com.lunixi.sdk.exception;

import java.util.Map;

/**
 * {@code 400} — the gateway rejected the request body.
 *
 * <p>The decision endpoint raises this for a declared value outside its closed
 * dictionary: a {@code channel} that only the platform's own senders may
 * declare, an {@code integrationMode} other than {@code standalone}, or a
 * {@code productContext} reserved for internal senders. Unknown fields are
 * rejected the same way, which is why {@link com.lunixi.sdk.fraud.EvaluateRequest}
 * checks them locally first.
 */
public class ValidationException extends LunixiApiException {

    private static final long serialVersionUID = 1L;

    public ValidationException(String message, int statusCode, String code,
                               Map<String, Object> response, String requestId) {
        super(message, statusCode, code, response, requestId);
    }
}
