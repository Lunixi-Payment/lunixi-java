package com.lunixi.sdk.identity;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.lunixi.sdk.http.Route;

/**
 * The identity route table ({@code IDENTITY_ROUTES} of the Node SDK and the shared catalog): every route of
 * {@code /api/v1/identity/*} the SDK calls. The surface is bearer-only; the browser fingerprint SDK uses the
 * publishable key ({@code /identity/sdk/init}), which is not here.
 */
public final class IdentityRoutes {

    /** Body of {@code analyze} / {@code analyzeSync}; the nested objects are forwarded as given. */
    public static final List<String> ANALYZE_FIELDS = List.of("subjectType", "subjectId", "productContext",
            "integrationMode", "customer", "device", "session", "source", "account", "metadata", "paymentId");

    /** The routes by operation name, in catalog order. */
    public static final Map<String, Route> ROUTES;

    private static final String LIVE = "/api/v1/identity/live-sessions";

    static {
        Map<String, Route> routes = new LinkedHashMap<>();
        routes.put("analyzeSync", Route.of("POST", LIVE + "/analyze-sync").withOptional(ANALYZE_FIELDS));
        routes.put("analyze", Route.of("POST", LIVE + "/analyze").withOptional(ANALYZE_FIELDS));
        routes.put("accountLinks", Route.of("GET", LIVE + "/:traceId/account-links"));
        routes.put("getSession", Route.of("GET", LIVE + "/:traceId"));
        routes.put("relatedSessions", Route.of("GET", LIVE + "/:traceId/related-sessions").withQuery("limit"));
        routes.put("timeline", Route.of("GET", LIVE + "/:traceId/timeline"));
        routes.put("topSources", Route.of("GET", LIVE + "/top-sources").withQuery("days", "limit"));
        routes.put("listSessions", Route.of("GET", LIVE).withQuery("fingerprintId", "deviceId", "paymentId", "limit"));
        ROUTES = Collections.unmodifiableMap(routes);
    }

    private IdentityRoutes() {
    }

    /**
     * @param name the operation name
     * @return its route
     * @throws IllegalArgumentException for an unknown name
     */
    public static Route route(String name) {
        Route route = ROUTES.get(name);
        if (route == null) {
            throw new IllegalArgumentException("Unknown identity route " + name + ".");
        }
        return route;
    }
}
