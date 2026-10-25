package com.lunixi.sdk.support;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import com.lunixi.sdk.http.HttpResult;
import com.lunixi.sdk.http.HttpTransport;

/**
 * A transport that answers from a queue and records what it was asked to send.
 *
 * <p>No test in this suite reaches the network. The behaviour under test is the
 * request the SDK builds — canonical string, signature, digest, headers, query
 * string — and the way it reads an answer, so the assertions are made against
 * the recorded call and against a canned response.
 */
public final class RecordingTransport implements HttpTransport {

    /** One recorded request. */
    public static final class Call {
        public final String method;
        public final String url;
        public final Map<String, String> headers;
        public final String body;

        Call(String method, String url, Map<String, String> headers, String body) {
            this.method = method;
            this.url = url;
            this.headers = Map.copyOf(headers);
            this.body = body;
        }
    }

    private final List<Call> calls = new ArrayList<>();
    private final Deque<HttpResult> responses = new ArrayDeque<>();
    private final String authTokenPath;
    private final String accessToken;

    /**
     * @param authTokenPath the token path this transport answers automatically
     * @param accessToken   the token to hand back
     */
    public RecordingTransport(String authTokenPath, String accessToken) {
        this.authTokenPath = authTokenPath;
        this.accessToken = accessToken;
    }

    /** Queues the next response for a non-token call. */
    public RecordingTransport enqueue(int statusCode, String body) {
        return enqueue(statusCode, Map.of("x-request-id", "req_test"), body);
    }

    /** Queues the next response for a non-token call, with headers. */
    public RecordingTransport enqueue(int statusCode, Map<String, String> headers, String body) {
        responses.add(new HttpResult(statusCode, headers, body));
        return this;
    }

    /** @return every recorded request, token fetches included */
    public List<Call> calls() {
        return List.copyOf(calls);
    }

    /** @return the recorded requests that were not token fetches */
    public List<Call> apiCalls() {
        List<Call> out = new ArrayList<>();
        for (Call call : calls) {
            if (!call.url.endsWith(authTokenPath)) {
                out.add(call);
            }
        }
        return out;
    }

    /** @return the single recorded non-token request */
    public Call onlyApiCall() {
        List<Call> api = apiCalls();
        if (api.size() != 1) {
            throw new IllegalStateException("Beklenen tek API çağrısı, kaydedilen: " + api.size());
        }
        return api.get(0);
    }

    @Override
    public HttpResult send(String method, String url, Map<String, String> headers, String body, Duration timeout) {
        calls.add(new Call(method, url, headers, body));

        if (url.endsWith(authTokenPath)) {
            return new HttpResult(200, Map.of("x-request-id", "req_token"),
                    "{\"status\":\"success\",\"code\":\"SUCCESS\",\"data\":{\"access_token\":\"" + accessToken
                            + "\",\"token_type\":\"Bearer\",\"expires_in\":1800},\"statusCode\":200,"
                            + "\"requestId\":\"req_token\"}");
        }
        if (responses.isEmpty()) {
            throw new IllegalStateException("Kuyrukta yanıt yok / no queued response for " + method + " " + url);
        }
        return responses.poll();
    }
}
