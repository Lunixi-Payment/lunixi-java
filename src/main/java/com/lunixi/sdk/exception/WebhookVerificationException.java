package com.lunixi.sdk.exception;

/**
 * An inbound webhook could not be verified.
 *
 * <p>Verification is fail-closed: any discrepancy throws, and an unverified
 * payload must never be acted on.
 */
public class WebhookVerificationException extends LunixiException {

    private static final long serialVersionUID = 1L;

    public WebhookVerificationException(String message) {
        super(message);
    }
}
