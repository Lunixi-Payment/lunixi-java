package com.lunixi.sdk.http;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.lunixi.sdk.json.Json;
import com.lunixi.sdk.json.JsonParseException;

/** One HTTP response: status, headers and the body text. */
public final class HttpResult {

    private final int statusCode;
    private final Map<String, String> headers;
    private final String body;

    /**
     * @param statusCode the HTTP status code
     * @param headers    response headers; keys are matched case-insensitively
     * @param body       the response body text, possibly empty
     */
    public HttpResult(int statusCode, Map<String, String> headers, String body) {
        this.statusCode = statusCode;
        this.headers = normalise(headers);
        this.body = body == null ? "" : body;
    }

    public int statusCode() {
        return statusCode;
    }

    public String body() {
        return body;
    }

    /** True for {@code 2xx}. Note that a {@code 2xx} envelope can still report a failure. */
    public boolean isHttpSuccess() {
        return statusCode >= 200 && statusCode < 300;
    }

    /**
     * Returns a response header, matched without regard to case.
     *
     * @param name the header name
     * @return the value, when present
     */
    public Optional<String> header(String name) {
        return Optional.ofNullable(headers.get(name.toLowerCase(Locale.ROOT)));
    }

    /**
     * Parses the body as a JSON object.
     *
     * @return the decoded envelope, or a map holding only {@code message} when
     *         the body was not JSON (an upstream proxy's HTML error page, for
     *         instance) — the caller still gets a usable error rather than a
     *         parse failure masking the status code
     */
    public Map<String, Object> json() {
        try {
            return Json.parseObject(body);
        } catch (JsonParseException notJson) {
            return Map.of("message", body);
        }
    }

    private static Map<String, String> normalise(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return Map.of();
        }
        java.util.Map<String, String> out = new java.util.HashMap<>(headers.size());
        headers.forEach((key, value) -> {
            if (key != null && value != null) {
                out.put(key.toLowerCase(Locale.ROOT), value);
            }
        });
        return Map.copyOf(out);
    }
}
