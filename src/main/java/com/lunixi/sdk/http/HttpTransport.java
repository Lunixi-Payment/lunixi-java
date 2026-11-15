package com.lunixi.sdk.http;

import java.time.Duration;
import java.util.Map;

import com.lunixi.sdk.exception.ConfigurationException;
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

    /**
     * Performs one request whose successful answer is a file (for example the PDF of
     * {@code PaymentsClient.downloadPromissoryNoteDocument}) and returns the body as raw bytes
     * ({@link HttpResult#HttpResult(int, Map, byte[])}). An error answer is still a JSON envelope.
     *
     * <p>The default implementation refuses, because a transport that decodes the body as text cannot return the
     * bytes unchanged. {@link JdkHttpTransport} implements it; a custom transport overrides it when the application
     * downloads documents.
     *
     * @param method  HTTP method, upper-case
     * @param url     the absolute URL
     * @param headers the request headers
     * @param body    the request body, or {@code null} for a bodyless request
     * @param timeout the per-request timeout
     * @return the response, whatever its status code, with the body as bytes
     * @throws LunixiApiException     when the request could not be completed at all
     * @throws ConfigurationException when this transport cannot read binary responses
     */
    default HttpResult sendBinary(String method, String url, Map<String, String> headers, String body,
                                  Duration timeout) {
        throw new ConfigurationException("This HTTP transport cannot read binary responses; implement "
                + "HttpTransport.sendBinary(...) to download documents.");
    }

    /**
     * Performs one request with a raw byte body, such as the image PUT of
     * {@code PaymentLinksClient.uploadImage} to a presigned storage URL. The SDK calls it with exactly the headers
     * the URL was signed for: no access token and no request signature.
     *
     * <p>The default implementation refuses, because {@link #send} takes a text body and cannot carry the bytes
     * unchanged. {@link JdkHttpTransport} implements it; a custom transport overrides it when the application
     * uploads payment link images.
     *
     * @param method  HTTP method, upper-case
     * @param url     the absolute URL
     * @param headers the request headers
     * @param body    the request body bytes
     * @param timeout the per-request timeout
     * @return the response, whatever its status code
     * @throws LunixiApiException     when the request could not be completed at all
     * @throws ConfigurationException when this transport cannot send a byte body
     */
    default HttpResult sendBytes(String method, String url, Map<String, String> headers, byte[] body,
                                 Duration timeout) {
        throw new ConfigurationException("This HTTP transport cannot send a byte body; implement "
                + "HttpTransport.sendBytes(...) to upload payment link images.");
    }
}
