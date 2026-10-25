package com.lunixi.sdk.exception;

/**
 * The SDK was configured or called with values that cannot produce a valid
 * request. Raised locally, before anything is sent.
 */
public class ConfigurationException extends LunixiException {

    private static final long serialVersionUID = 1L;

    public ConfigurationException(String message) {
        super(message);
    }
}
