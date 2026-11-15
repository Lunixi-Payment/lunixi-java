package com.lunixi.sdk.kyc;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.lunixi.sdk.http.Route;

/**
 * The KYC route table ({@code KYC_ROUTES} of the Node SDK and the shared catalog): every route of
 * {@code /api/v1/kyc/*} the SDK calls, with the {@code Idempotency-Key} rule and the body and query fields. The
 * surface is bearer-only. The browser bootstraps its own SDK token with the publishable key ({@code /kyc/sdk/init});
 * that route is not reachable from a server key and is not here.
 */
public final class KycRoutes {

    /** Optional body fields of {@code createSession} and {@code evidenceRequest}. */
    public static final List<String> SESSION_FIELDS = List.of("externalCustomerId", "customerMode", "customer",
            "jurisdiction", "requiredDocumentTypes", "primaryDocumentType", "evidencePurpose", "ttlMinutes",
            "idempotencyKey", "evidenceContextJson");

    /** The routes by operation name, in catalog order. */
    public static final Map<String, Route> ROUTES;

    private static final String KYC = "/api/v1/kyc";

    static {
        Map<String, Route> routes = new LinkedHashMap<>();
        routes.put("createSession", Route.of("POST", KYC + "/sessions").optionalKey()
                .withRequired("subjectId", "sessionType").withOptional(SESSION_FIELDS));
        routes.put("getSession", Route.of("GET", KYC + "/sessions/:sessionId"));
        // The merchant list takes only these two; an unknown filter is a 400 on the gateway.
        routes.put("listSessions", Route.of("GET", KYC + "/sessions").withQuery("limit", "cursor"));
        routes.put("setJurisdiction", Route.of("POST", KYC + "/sessions/:sessionId/jurisdiction")
                .withRequired("jurisdiction"));
        routes.put("setPrimaryDocument", Route.of("POST", KYC + "/sessions/:sessionId/primary-document")
                .withRequired("documentType"));
        routes.put("uploadDocument", Route.of("POST", KYC + "/sessions/:sessionId/documents")
                .withRequired("documentType", "fileBase64", "mimeType")
                .withOptional("side", "jurisdiction", "metadataJson", "idempotencyKey"));
        routes.put("timeline", Route.of("GET", KYC + "/sessions/:sessionId/timeline"));
        routes.put("submit", Route.of("POST", KYC + "/sessions/:sessionId/submit"));
        routes.put("manualReview", Route.of("POST", KYC + "/sessions/:sessionId/manual-review")
                .withOptional("reasonCode", "reasonDetail").withOptionalBody());
        routes.put("evidenceRequest", Route.of("POST", KYC + "/evidence-requests").optionalKey()
                .withRequired("subjectId", "sessionType").withOptional(SESSION_FIELDS));
        routes.put("merchantPolicy", Route.of("GET", KYC + "/admin/merchant-policy"));
        routes.put("updateMerchantPolicy", Route.of("POST", KYC + "/admin/merchant-policy").asFree());
        routes.put("rotateWebhookSecret", Route.of("POST", KYC + "/admin/merchant-policy/webhook-secret/rotate"));
        routes.put("webhookOutbox", Route.of("GET", KYC + "/admin/webhook-outbox").withQuery("limit", "status"));
        routes.put("retryWebhookEvent", Route.of("POST", KYC + "/admin/webhook-outbox/:eventId/retry"));
        routes.put("erasureRequests", Route.of("GET", KYC + "/admin/erasure-requests").withQuery("limit", "status"));
        ROUTES = Collections.unmodifiableMap(routes);
    }

    private KycRoutes() {
    }

    /**
     * @param name the operation name
     * @return its route
     * @throws IllegalArgumentException for an unknown name
     */
    public static Route route(String name) {
        Route route = ROUTES.get(name);
        if (route == null) {
            throw new IllegalArgumentException("Unknown KYC route " + name + ".");
        }
        return route;
    }
}
