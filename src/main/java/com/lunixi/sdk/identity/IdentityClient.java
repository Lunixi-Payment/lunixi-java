package com.lunixi.sdk.identity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.lunixi.sdk.exception.ConfigurationException;
import com.lunixi.sdk.http.RoutePage;
import com.lunixi.sdk.internal.ApiClient;
import com.lunixi.sdk.internal.Checks;
import com.lunixi.sdk.internal.RouteRequest;

/**
 * Identity signals ({@code /api/v1/identity/*}), {@code client.identity()}.
 *
 * <p>Bearer-only. The browser fingerprint SDK produces a {@code traceId}; your server reads the linked accounts of
 * that trace and flags or blocks when the device is shared ("one person, many accounts",
 * {@link #isDeviceShared(Map)}).
 *
 * <p>This is a signal surface: it carries risk flags, reason codes, similarity probabilities and the identity
 * snapshot, not an allow / review / block verdict (that is the fraud decision). Every call is checked against
 * {@link IdentityRoutes} before sending. Calls return the envelope's {@code data}; {@link #listSessions(Map)} returns
 * a {@link RoutePage}.
 */
public final class IdentityClient {

    private static final List<String> OBJECT_FIELDS = List.of("customer", "device", "session", "source", "account",
            "metadata");

    private final RouteRequest routes;

    /**
     * @param api the gateway client
     */
    public IdentityClient(ApiClient api) {
        this.routes = new RouteRequest(api, IdentityRoutes.ROUTES);
    }

    /**
     * Synchronous analysis: {@code {subjectType?, subjectId?, productContext?, integrationMode?, customer?, device?,
     * session?, source?, account?, metadata?, paymentId?}} ({@code subjectType} and {@code subjectId} go together).
     *
     * @param request the analysis request
     * @return {@code {traceId, analysisStatus, riskFlags, reasonCodes, identitySnapshot, …}} ({@code data})
     */
    public Map<String, Object> analyzeSync(Map<String, ?> request) {
        return RouteRequest.data(routes.send("analyzeSync", Map.of(), null, analyzeBody(request), null));
    }

    /**
     * Queues the analysis and returns the {@code traceId} at once; the same body as {@link #analyzeSync(Map)}.
     *
     * @param request the analysis request
     * @return the answer ({@code data})
     */
    public Map<String, Object> analyze(Map<String, ?> request) {
        return RouteRequest.data(routes.send("analyze", Map.of(), null, analyzeBody(request), null));
    }

    /**
     * Other accounts seen on this device / fingerprint / IP (account keys hashed).
     *
     * @param traceId the trace id
     * @return {@code {linkedAccounts, sessions, …}} ({@code data})
     */
    public Map<String, Object> accountLinks(String traceId) {
        return RouteRequest.data(routes.send("accountLinks", RouteRequest.params("traceId", traceId), null, null,
                null));
    }

    /**
     * @param traceId the trace id
     * @return the session ({@code data})
     */
    public Map<String, Object> getSession(String traceId) {
        return RouteRequest.data(routes.send("getSession", RouteRequest.params("traceId", traceId), null, null, null));
    }

    /**
     * @param traceId the trace id
     * @return recent sessions of the same device-identity cluster ({@code data})
     */
    public Map<String, Object> relatedSessions(String traceId) {
        return relatedSessions(traceId, null);
    }

    /**
     * @param traceId the trace id
     * @param limit   the number of sessions, or {@code null}
     * @return recent sessions of the same device-identity cluster ({@code data})
     */
    public Map<String, Object> relatedSessions(String traceId, Integer limit) {
        return RouteRequest.data(routes.send("relatedSessions", RouteRequest.params("traceId", traceId),
                RouteRequest.compact("limit", limit), null, null));
    }

    /**
     * @param traceId the trace id
     * @return the session summary and recorded lifecycle events ({@code data})
     */
    public Map<String, Object> timeline(String traceId) {
        return RouteRequest.data(routes.send("timeline", RouteRequest.params("traceId", traceId), null, null, null));
    }

    /** @return source domains ranked by repeated risky activity ({@code data}) */
    public Map<String, Object> topSources() {
        return topSources(Map.of());
    }

    /**
     * @param filters {@code days}, {@code limit}
     * @return source domains ranked by repeated risky activity ({@code data})
     */
    public Map<String, Object> topSources(Map<String, ?> filters) {
        return RouteRequest.data(routes.send("topSources", Map.of(), filters, null, null));
    }

    /** @return the first page of sessions */
    public RoutePage listSessions() {
        return listSessions(Map.of());
    }

    /**
     * @param filters {@code fingerprintId}, {@code deviceId}, {@code paymentId}, {@code limit}
     * @return one page of sessions
     */
    public RoutePage listSessions(Map<String, ?> filters) {
        return new RoutePage(routes.send("listSessions", Map.of(), filters, null, null));
    }

    /**
     * Same as {@link #isDeviceShared(Map, int)} with the threshold 2: a second account on the device trips it.
     *
     * @param accountLinks an {@link #accountLinks(String)} answer
     * @return {@code true} when the device is shared
     */
    public static boolean isDeviceShared(Map<String, ?> accountLinks) {
        return isDeviceShared(accountLinks, 2);
    }

    /**
     * @param accountLinks an {@link #accountLinks(String)} answer
     * @param threshold    the number of accounts, counting the current one ({@code linkedAccounts} excludes it); at
     *                     least 2
     * @return {@code true} when the device is shared by at least {@code threshold} accounts
     */
    public static boolean isDeviceShared(Map<String, ?> accountLinks, int threshold) {
        int linked = accountLinks != null && accountLinks.get("linkedAccounts") instanceof Collection<?> list
                ? list.size() : 0;
        return linked + 1 >= Math.max(2, threshold);
    }

    /**
     * @param accountLinks an {@link #accountLinks(String)} answer
     * @return the distinct {@code linkReasons} of its sessions ({@code same_device_cluster},
     *         {@code same_fingerprint}, …), in order of appearance
     */
    public static List<String> linkReasons(Map<String, ?> accountLinks) {
        Set<String> reasons = new LinkedHashSet<>();
        if (accountLinks != null && accountLinks.get("sessions") instanceof Collection<?> sessions) {
            for (Object session : sessions) {
                if (session instanceof Map<?, ?> map && map.get("linkReasons") instanceof Collection<?> list) {
                    list.forEach(reason -> reasons.add(String.valueOf(reason)));
                }
            }
        }
        return List.copyOf(new ArrayList<>(reasons));
    }

    private static Map<String, Object> analyzeBody(Map<String, ?> request) {
        if (request == null) {
            throw new ConfigurationException("analyze request must be an object (a map).");
        }
        Map<String, Object> body = Checks.plainMap(request, "analyze request");
        for (String field : OBJECT_FIELDS) {
            if (body.get(field) != null) {
                Checks.plainMap(body.get(field), field);
            }
        }
        if (body.containsKey("subjectType") != body.containsKey("subjectId")) {
            throw new ConfigurationException("subjectType and subjectId go together.");
        }
        return body;
    }
}
