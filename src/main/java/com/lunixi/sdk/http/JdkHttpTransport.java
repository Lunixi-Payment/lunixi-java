package com.lunixi.sdk.http;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import com.lunixi.sdk.exception.LunixiApiException;

/** The default {@link HttpTransport}, built on {@code java.net.http.HttpClient}. */
public final class JdkHttpTransport implements HttpTransport {

    /** Largest binary body {@link #sendBinary} reads (16 MiB); a longer answer fails without being kept. */
    public static final int MAX_BINARY_BYTES = 16 * 1024 * 1024;

    private final HttpClient client;

    /** Creates a transport with its own HTTP client and a 10-second connect timeout. */
    public JdkHttpTransport() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    /**
     * Creates a transport over an existing HTTP client.
     *
     * <p>Redirects must not be followed: a redirect would replay the request at
     * another URL while the signature covers the original path, so the retried
     * call would fail verification — and a signed body would be re-sent to a
     * host the signature never authorised.
     *
     * @param client the client to use
     */
    public JdkHttpTransport(HttpClient client) {
        if (client == null || client.followRedirects() != HttpClient.Redirect.NEVER) { throw new IllegalArgumentException("The signed HTTP transport must never follow redirects."); }
        this.client = client;
    }

    @Override
    public HttpResult send(String method, String url, Map<String, String> headers, String body, Duration timeout) {
        try {
            HttpResponse<String> response = client.send(build(method, url, headers, body, timeout),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new HttpResult(response.statusCode(), flatten(response), response.body());
        } catch (IOException transportFailure) {
            throw new LunixiApiException(
                    "İstek gönderilemedi / the request could not be sent: " + transportFailure.getMessage(),
                    transportFailure);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new LunixiApiException("İstek kesildi / the request was interrupted.", interrupted);
        }
    }

    /**
     * Reads the body as raw bytes, at most {@link #MAX_BINARY_BYTES}.
     */
    @Override
    public HttpResult sendBinary(String method, String url, Map<String, String> headers, String body,
                                 Duration timeout) {
        try {
            HttpResponse<InputStream> response = client.send(build(method, url, headers, body, timeout),
                    HttpResponse.BodyHandlers.ofInputStream());
            byte[] bytes;
            try (InputStream stream = response.body()) {
                bytes = stream.readNBytes(MAX_BINARY_BYTES + 1);
            }
            if (bytes.length > MAX_BINARY_BYTES) {
                throw new LunixiApiException("Yanıt çok büyük / the response exceeds " + MAX_BINARY_BYTES
                        + " bytes.", null);
            }
            return new HttpResult(response.statusCode(), flatten(response), bytes);
        } catch (IOException transportFailure) {
            throw new LunixiApiException(
                    "İstek gönderilemedi / the request could not be sent: " + transportFailure.getMessage(),
                    transportFailure);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new LunixiApiException("İstek kesildi / the request was interrupted.", interrupted);
        }
    }

    /**
     * Sends the bytes as they are. {@code Host} and {@code Content-Length} are set by the HTTP client from the URL
     * and the body, so given values for them are not copied.
     */
    @Override
    public HttpResult sendBytes(String method, String url, Map<String, String> headers, byte[] body,
                                Duration timeout) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .method(method, HttpRequest.BodyPublishers.ofByteArray(body == null ? new byte[0] : body.clone()));
        headers.forEach((name, value) -> {
            String lower = name.toLowerCase(java.util.Locale.ROOT);
            if (!"host".equals(lower) && !"content-length".equals(lower)) {
                request.header(name, value);
            }
        });
        try {
            HttpResponse<String> response = client.send(request.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new HttpResult(response.statusCode(), flatten(response), response.body());
        } catch (IOException transportFailure) {
            throw new LunixiApiException(
                    "İstek gönderilemedi / the request could not be sent: " + transportFailure.getMessage(),
                    transportFailure);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new LunixiApiException("İstek kesildi / the request was interrupted.", interrupted);
        }
    }

    private static HttpRequest build(String method, String url, Map<String, String> headers, String body,
                                     Duration timeout) {
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8);
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .method(method, publisher);
        headers.forEach(request::header);
        return request.build();
    }

    private static Map<String, String> flatten(HttpResponse<?> response) {
        Map<String, String> headers = new HashMap<>();
        response.headers().map().forEach((name, values) -> {
            if (!values.isEmpty()) {
                headers.put(name, values.get(0));
            }
        });
        return headers;
    }
}
