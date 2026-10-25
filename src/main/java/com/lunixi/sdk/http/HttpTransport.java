package com.lunixi.sdk.http;

import java.time.Duration;
import java.util.Map;

import com.lunixi.sdk.exception.LunixiApiException;

/**
 * The SDK's seam onto HTTP.
 *
 * <p>{@link JdkHttpTransport} is the default and needs nothing beyond the JDK.
 * The interface exists so an application can route SDK traffic through the HTTP
 * client it already configures — proxy, connection pool, tracing, metrics —
 * instead of the SDK holding a second, differently configured client.
 */
public interface HttpTransport {

    /**
     * Performs one request.
     *
     * @param method  HTTP method, upper-case
     * @param url     the absolute URL
     * @param headers the request headers
     * @param body    the request body, or {@code null} for a bodyless request
     * @param timeout the per-request timeout
     * @return the response, whatever its status code
     * @throws LunixiApiException when the request could not be completed at all
     *                            (connection refused, timeout, DNS failure)
     */
    HttpResult send(String method, String url, Map<String, String> headers, String body, Duration timeout);
}
