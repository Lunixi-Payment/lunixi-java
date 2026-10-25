package com.lunixi.sdk.exception;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import com.lunixi.sdk.internal.ResponseMaps;

/**
 * The gateway answered with a non-success response, or the transport failed
 * before an answer arrived.
 *
 * <p>Branch on {@link #code()} — the machine-readable error code from the
 * response envelope — never on the message text, which is localised and may be
 * reworded. {@link #statusCode()} is {@code 0} when the request never reached
 * the server.
 */
public class LunixiApiException extends LunixiException {

    private static final long serialVersionUID = 1L;

    private final int statusCode;
    private final String code;
    private final transient Map<String, Object> response;
    private final String requestId;

    public LunixiApiException(String message, int statusCode, String code,
                              Map<String, Object> response, String requestId) {
        super(message);
        this.statusCode = statusCode;
        this.code = code;
        this.response = ResponseMaps.immutableCopy(response);
        this.requestId = requestId;
    }

    public LunixiApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
        this.code = null;
        this.response = Collections.emptyMap();
        this.requestId = null;
    }

    /** HTTP status code, or {@code 0} when the request never reached the server. */
    public int statusCode() {
        return statusCode;
    }

    /** The envelope's machine-readable error code, when the response carried one. */
    public Optional<String> code() {
        return Optional.ofNullable(code);
    }

    /** The decoded response envelope; empty when there was no parsable body. */
    public Map<String, Object> response() {
        return response;
    }

    /** The gateway's {@code x-request-id}, the value to quote in a support request. */
    public Optional<String> requestId() {
        return Optional.ofNullable(requestId);
    }
}
