package com.lunixi.sdk.exception;

/** The signing key could not be read, or signing itself failed. */
public class SignatureException extends LunixiException {

    private static final long serialVersionUID = 1L;

    public SignatureException(String message) {
        super(message);
    }

    public SignatureException(String message, Throwable cause) {
        super(message, cause);
    }
}
