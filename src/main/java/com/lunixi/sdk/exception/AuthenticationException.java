package com.lunixi.sdk.exception;

import java.util.Map;

/**
 * The access token could not be obtained: the signature was rejected, the key
 * is unknown or suspended, or the calling IP is outside the key's allow-list.
 */
public class AuthenticationException extends LunixiApiException {

    private static final long serialVersionUID = 1L;

    public AuthenticationException(String message, int statusCode, String code,
                                   Map<String, Object> response, String requestId) {
        super(message, statusCode, code, response, requestId);
    }
}
