package com.lunixi.sdk.http;

import java.io.IOException;
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
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8);

        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .method(method, publisher);
        headers.forEach(request::header);

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

    private static Map<String, String> flatten(HttpResponse<String> response) {
        Map<String, String> headers = new HashMap<>();
        response.headers().map().forEach((name, values) -> {
            if (!values.isEmpty()) {
                headers.put(name, values.get(0));
            }
        });
        return headers;
    }
}
