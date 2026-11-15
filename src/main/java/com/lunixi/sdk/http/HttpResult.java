package com.lunixi.sdk.http;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.lunixi.sdk.json.Json;
import com.lunixi.sdk.json.JsonParseException;

/** One HTTP response: status, headers and the body (text, or raw bytes for a binary download). */
public final class HttpResult {

    private final int statusCode;
    private final Map<String, String> headers;
    private final String body;
    private final byte[] bytes;

    /**
     * @param statusCode the HTTP status code
     * @param headers    response headers; keys are matched case-insensitively
     * @param body       the response body text, possibly empty
     */
    public HttpResult(int statusCode, Map<String, String> headers, String body) {
        this.statusCode = statusCode;
        this.headers = normalise(headers);
        this.body = body == null ? "" : body;
        this.bytes = null;
    }

    /**
     * A response whose body was read as raw bytes ({@link HttpTransport#sendBinary}). {@link #body()} decodes the
     * bytes as UTF-8, so an error answer can still be read as a JSON envelope.
     *
     * @param statusCode the HTTP status code
     * @param headers    response headers; keys are matched case-insensitively
     * @param bytes      the response body bytes, possibly empty
     */
    public HttpResult(int statusCode, Map<String, String> headers, byte[] bytes) {
        this.statusCode = statusCode;
        this.headers = normalise(headers);
        this.bytes = bytes == null ? new byte[0] : bytes.clone();
        this.body = new String(this.bytes, StandardCharsets.UTF_8);
    }

    public int statusCode() {
        return statusCode;
    }

    public String body() {
        return body;
    }

    /**
     * The body as bytes: the raw bytes of a binary response, otherwise the UTF-8 encoding of {@link #body()}.
     *
     * @return a copy of the body bytes
     */
    public byte[] bytes() {
        return bytes == null ? body.getBytes(StandardCharsets.UTF_8) : bytes.clone();
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
