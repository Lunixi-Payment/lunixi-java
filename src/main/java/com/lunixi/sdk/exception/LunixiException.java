package com.lunixi.sdk.exception;

/** Base class of every exception the SDK raises. */
public class LunixiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public LunixiException(String message) {
        super(message);
    }

    public LunixiException(String message, Throwable cause) {
        super(message, cause);
    }
}
