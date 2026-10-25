package com.lunixi.sdk.http;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The single place the SDK reads a gateway response envelope.
 *
 * <p>Every response is wrapped:
 *
 * <pre>
 * { "status": "success", "code": "SUCCESS", "message": "…",
 *   "data": &lt;payload&gt;, "statusCode": 200, "requestId": "…",
 *   "pageInfo": { "hasMore": …, "nextCursor": …, "totalCount": … } }
 * </pre>
 *
 * <h2>There is no {@code success} field</h2>
 * The outcome travels in <b>{@code status}</b>, whose value is the string
 * {@code "success"}, {@code "failure"} or {@code "partial"}. A client that
 * reads a boolean {@code success} always sees nothing — and because an absent
 * field reads as "not successful" in some languages and as "no error" in
 * others, the mistake either breaks every call or hides every failure. The
 * accessor here is {@link #status(Map)}; this class deliberately exposes no
 * {@code success} anywhere, so the mistake cannot be made through the SDK.
 *
 * <h2>{@code status} can disagree with the HTTP status</h2>
 * A {@code 200} may carry {@code status: "failure"} — the envelope is produced
 * after the handler returns, and a failure reported by a service downstream
 * lands in the envelope while the HTTP status stays {@code 200}. Checking only
 * the status code would treat such a response as a success, which is why
 * {@link #isFailure(Map)} is consulted on every {@code 2xx}. {@code "partial"}
 * is <i>not</i> a failure: it is how an endpoint reports that part of a batch
 * landed, and the per-item outcome is in the payload.
 *
 * <h2>{@code pageInfo} is a sibling of {@code data}, not nested inside it</h2>
 * List endpoints return a bare array in {@code data}; putting the paging
 * metadata inside it would have meant turning that array into an object and
 * breaking every client that reads {@code data} as a list. So paging metadata
 * is always next to {@code data}. {@link #pageInfo(Map)} accepts it in either
 * position, because a payload-shaped response may carry its own
 * {@code data.pageInfo}.
 */
public final class Envelope {

    private Envelope() {
    }

    /**
     * The envelope's {@code status}: {@code "success"}, {@code "failure"} or
     * {@code "partial"}.
     *
     * @param response the decoded response
     * @return the status, when the envelope carried one
     */
    public static Optional<String> status(Map<String, Object> response) {
        return string(response, "status");
    }

    /**
     * True when the envelope reports a failure, whatever the HTTP status code.
     *
     * @param response the decoded response
     * @return {@code true} for {@code status: "failure"}
     */
    public static boolean isFailure(Map<String, Object> response) {
        return status(response).map("failure"::equals).orElse(false);
    }

    /**
     * The envelope's machine-readable {@code code}.
     *
     * @param response the decoded response
     * @return the code, when present
     */
    public static Optional<String> code(Map<String, Object> response) {
        return string(response, "code");
    }

    /**
     * The envelope's {@code requestId}, the value to quote in a support request.
     *
     * @param response the decoded response
     * @return the request id, when present
     */
    public static Optional<String> requestId(Map<String, Object> response) {
        return string(response, "requestId");
    }

    /**
     * The payload as an object.
     *
     * @param response the decoded response
     * @return {@code data} when it is an object, otherwise an empty map
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> data(Map<String, Object> response) {
        if (response == null) {
            return Map.of();
        }
        Object data = response.get("data");
        return data instanceof Map ? (Map<String, Object>) data : Map.of();
    }

    /**
     * The payload as a list of objects.
     *
     * @param response the decoded response
     * @return the rows of {@code data}, or an empty list when {@code data} is
     *         not a list
     */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> rows(Map<String, Object> response) {
        if (response == null) {
            return List.of();
        }
        Object data = response.get("data");
        if (!(data instanceof List)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object row : (List<Object>) data) {
            if (row instanceof Map) {
                out.add((Map<String, Object>) row);
            }
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * The paging metadata, looked for next to {@code data} first and inside it
     * second.
     *
     * @param response the decoded response
     * @return {@code pageInfo}, or an empty map when the response is not paged
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> pageInfo(Map<String, Object> response) {
        if (response == null) {
            return Map.of();
        }
        Object sibling = response.get("pageInfo");
        if (sibling instanceof Map) {
            return (Map<String, Object>) sibling;
        }
        Object nested = data(response).get("pageInfo");
        return nested instanceof Map ? (Map<String, Object>) nested : Map.of();
    }

    /**
     * The opaque cursor for the next page.
     *
     * <p>Do not decode it, build one, or edit one — send it back verbatim as the
     * {@code cursor} filter. A damaged cursor is answered with
     * {@code 400 INVALID_CURSOR}; it does not quietly restart from the first
     * page, so a client that mangles it loses the rest of the result set
     * loudly rather than silently looping over page one.
     *
     * @param response the decoded response
     * @return the cursor, or empty on the last page
     */
    public static Optional<String> nextCursor(Map<String, Object> response) {
        return string(pageInfo(response), "nextCursor");
    }

    /**
     * Whether a further page exists.
     *
     * @param response the decoded response
     * @return {@code true} when {@code pageInfo.hasMore} is set
     */
    public static boolean hasMore(Map<String, Object> response) {
        Object value = pageInfo(response).get("hasMore");
        return Boolean.TRUE.equals(value);
    }

    /**
     * The total number of matching rows — only when the server actually counted
     * them.
     *
     * <p>With cursor paging {@code totalCount} is absent by default, because a
     * filtered {@code COUNT(*)} is expensive. Advance with
     * {@link #nextCursor(Map)}; ask for the total only when you really need it.
     *
     * @param response the decoded response
     * @return the total, when the server returned one
     */
    public static Optional<Long> totalCount(Map<String, Object> response) {
        Object value = pageInfo(response).get("totalCount");
        return value instanceof Number ? Optional.of(((Number) value).longValue()) : Optional.empty();
    }

    private static Optional<String> string(Map<String, Object> source, String key) {
        if (source == null) {
            return Optional.empty();
        }
        Object value = source.get(key);
        return value instanceof String && !((String) value).isEmpty()
                ? Optional.of((String) value)
                : Optional.empty();
    }
}
