package com.lunixi.sdk.kyc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.lunixi.sdk.exception.ConfigurationException;
import com.lunixi.sdk.http.RoutePage;
import com.lunixi.sdk.internal.ApiClient;
import com.lunixi.sdk.internal.Checks;
import com.lunixi.sdk.internal.RouteRequest;
import com.lunixi.sdk.json.Json;

/**
 * KYC / KYB / evidence sessions ({@code /api/v1/kyc/*}), {@code client.kyc()}.
 *
 * <p>Bearer-only (no per-request signature). Your server creates the session ({@code externalCustomerId} = your own
 * user id, echoed on every webhook); the browser SDK then runs the capture with the publishable key. The
 * {@code kyc.*} webhook ({@link KycEvents}) is the authority for the verification result.
 *
 * <p>Every call is checked against {@link KycRoutes} before sending; a rule violation throws
 * {@link ConfigurationException} naming the field, never its value (document images included). Session calls return
 * the envelope's {@code data} ({@code {session, capturePolicy, …}}; no personal data comes back, only status,
 * decision, risk and the achieved assurance). Lists return a {@link RoutePage}.
 */
public final class KycClient {

    private static final List<String> LIST_FILTERS = List.of("limit", "cursor", "pageSize", "pageToken");

    private final RouteRequest routes;

    /**
     * @param api the gateway client
     */
    public KycClient(ApiClient api) {
        this.routes = new RouteRequest(api, KycRoutes.ROUTES);
    }

    /**
     * Same as {@link #createSession(Map, String)} with the key taken from {@code request.idempotencyKey}.
     *
     * @param request the session
     * @return the session ({@code data})
     */
    public Map<String, Object> createSession(Map<String, ?> request) {
        return createSession(request, null);
    }

    /**
     * Creates a session: {@code subjectId}, {@code sessionType} ({@code KYC} | {@code KYB} | {@code EVIDENCE}, any
     * case), and optionally {@code externalCustomerId}, {@code customerMode}, one of {@code person} /
     * {@code company} / {@code customer}, {@code jurisdiction}, {@code requiredDocumentTypes},
     * {@code primaryDocumentType}, {@code evidencePurpose}, {@code ttlMinutes} (1–1440), {@code metadata}.
     * {@code person} / {@code company} are sent as {@code customer} with {@code customerType} {@code PERSON} /
     * {@code COMPANY}; {@code metadata} is sent as the JSON text {@code evidenceContextJson}. An
     * {@code Idempotency-Key} (the argument or {@code request.idempotencyKey}) goes in the header and the body.
     *
     * @param request        the session
     * @param idempotencyKey the key, or {@code null}
     * @return the session ({@code data})
     */
    public Map<String, Object> createSession(Map<String, ?> request, String idempotencyKey) {
        return sessionCall("createSession", request, idempotencyKey);
    }

    /**
     * @param sessionId the session id
     * @return the session ({@code data})
     */
    public Map<String, Object> getSession(String sessionId) {
        return RouteRequest.data(routes.send("getSession", RouteRequest.params("sessionId", sessionId), null, null,
                null));
    }

    /** @return the first page of sessions */
    public RoutePage listSessions() {
        return listSessions(Map.of());
    }

    /**
     * Lists sessions. Filters: {@code limit}, {@code cursor} ({@code pageSize} / {@code pageToken} are read as the
     * same). Anything else is refused: the merchant list has no status or customer filter, and dropping one would
     * return every session.
     *
     * @param filters the filters
     * @return one page of sessions
     */
    public RoutePage listSessions(Map<String, ?> filters) {
        Map<String, Object> given = Checks.plainMap(filters == null ? Map.of() : filters, "listSessions filters");
        List<String> unknown = new ArrayList<>();
        for (String field : given.keySet()) {
            if (!LIST_FILTERS.contains(field)) {
                unknown.add(field);
            }
        }
        if (!unknown.isEmpty()) {
            throw new ConfigurationException("listSessions does not accept the filter(s) " + String.join(", ", unknown)
                    + "; it takes limit, cursor.");
        }
        Map<String, Object> query = RouteRequest.compact(
                "limit", given.containsKey("limit") ? given.get("limit") : given.get("pageSize"),
                "cursor", given.containsKey("cursor") ? given.get("cursor") : given.get("pageToken"));
        return new RoutePage(routes.send("listSessions", Map.of(), query, null, null));
    }

    /**
     * Sets the applicant jurisdiction (ISO country) on a server-created session.
     *
     * @param sessionId    the session id
     * @param jurisdiction the ISO country code
     * @return the session ({@code data})
     */
    public Map<String, Object> setJurisdiction(String sessionId, String jurisdiction) {
        return RouteRequest.data(routes.send("setJurisdiction", RouteRequest.params("sessionId", sessionId), null,
                Map.of("jurisdiction", Checks.text(jurisdiction, "jurisdiction")), null));
    }

    /**
     * @param sessionId    the session id
     * @param documentType {@code PASSPORT}, {@code NATIONAL_ID}, {@code DRIVERS_LICENSE} or {@code RESIDENCE_PERMIT}
     * @return the session ({@code data})
     */
    public Map<String, Object> setPrimaryDocument(String sessionId, String documentType) {
        return RouteRequest.data(routes.send("setPrimaryDocument", RouteRequest.params("sessionId", sessionId), null,
                Map.of("documentType", Checks.text(documentType, "documentType")), null));
    }

    /**
     * Server-side document upload (most flows use the browser SDK): {@code {documentType, fileBase64, mimeType,
     * side?, jurisdiction?, metadataJson?, idempotencyKey?}}. The image never appears in an error message.
     *
     * @param sessionId the session id
     * @param document  the document
     * @return the answer ({@code data})
     */
    public Map<String, Object> uploadDocument(String sessionId, Map<String, ?> document) {
        return RouteRequest.data(routes.send("uploadDocument", RouteRequest.params("sessionId", sessionId), null,
                body(document, "uploadDocument document"), null));
    }

    /**
     * @param sessionId the session id
     * @return the session event timeline (audit) ({@code data})
     */
    public Map<String, Object> timeline(String sessionId) {
        return RouteRequest.data(routes.send("timeline", RouteRequest.params("sessionId", sessionId), null, null,
                null));
    }

    /**
     * @param sessionId the session id
     * @return the session ({@code data})
     */
    public Map<String, Object> submit(String sessionId) {
        return RouteRequest.data(routes.send("submit", RouteRequest.params("sessionId", sessionId), null, null,
                null));
    }

    /**
     * @param sessionId the session id
     * @return the session ({@code data})
     */
    public Map<String, Object> manualReview(String sessionId) {
        return manualReview(sessionId, null, null);
    }

    /**
     * Sends the session to manual review.
     *
     * @param sessionId    the session id
     * @param reasonCode   the reason code, or {@code null}
     * @param reasonDetail the reason, or {@code null}
     * @return the session ({@code data})
     */
    public Map<String, Object> manualReview(String sessionId, String reasonCode, String reasonDetail) {
        Map<String, Object> body = RouteRequest.compact(
                "reasonCode", RouteRequest.optionalText(reasonCode, "reasonCode"),
                "reasonDetail", RouteRequest.optionalText(reasonDetail, "reasonDetail"));
        return RouteRequest.data(routes.send("manualReview", RouteRequest.params("sessionId", sessionId), null, body,
                null));
    }

    /**
     * Same as {@link #evidenceRequest(Map, String)} with the key taken from {@code request.idempotencyKey}.
     *
     * @param request the evidence request
     * @return the session ({@code data})
     */
    public Map<String, Object> evidenceRequest(Map<String, ?> request) {
        return evidenceRequest(request, null);
    }

    /**
     * An {@code EVIDENCE} session (proof-of-X), optionally linked to a payment or order; the same body as
     * {@link #createSession(Map, String)}.
     *
     * @param request        the evidence request
     * @param idempotencyKey the key, or {@code null}
     * @return the session ({@code data})
     */
    public Map<String, Object> evidenceRequest(Map<String, ?> request, String idempotencyKey) {
        return sessionCall("evidenceRequest", request, idempotencyKey);
    }

    /** @return your KYC policy (required assurance, providers…) ({@code data}) */
    public Map<String, Object> merchantPolicy() {
        return RouteRequest.data(routes.send("merchantPolicy"));
    }

    /**
     * @param policy the policy
     * @return the policy ({@code data})
     */
    public Map<String, Object> updateMerchantPolicy(Map<String, ?> policy) {
        return RouteRequest.data(routes.send("updateMerchantPolicy", Map.of(), null,
                body(policy, "updateMerchantPolicy policy"), null));
    }

    /** @return the answer ({@code data}): rotates the KYC webhook signing secret */
    public Map<String, Object> rotateWebhookSecret() {
        return RouteRequest.data(routes.send("rotateWebhookSecret"));
    }

    /** @return the first page of KYC webhook deliveries */
    public RoutePage webhookOutbox() {
        return webhookOutbox(Map.of());
    }

    /**
     * @param filters {@code limit}, {@code status}
     * @return one page of KYC webhook deliveries
     */
    public RoutePage webhookOutbox(Map<String, ?> filters) {
        return new RoutePage(routes.send("webhookOutbox", Map.of(), filters, null, null));
    }

    /**
     * @param eventId the webhook event id
     * @return the answer ({@code data})
     */
    public Map<String, Object> retryWebhookEvent(String eventId) {
        return RouteRequest.data(routes.send("retryWebhookEvent", RouteRequest.params("eventId", eventId), null,
                null, null));
    }

    /** @return the first page of data erasure requests */
    public RoutePage erasureRequests() {
        return erasureRequests(Map.of());
    }

    /**
     * @param filters {@code limit}, {@code status}
     * @return one page of data erasure requests
     */
    public RoutePage erasureRequests(Map<String, ?> filters) {
        return new RoutePage(routes.send("erasureRequests", Map.of(), filters, null, null));
    }

    private Map<String, Object> sessionCall(String name, Map<String, ?> request, String idempotencyKey) {
        Map<String, Object> body = body(request, name + " request");
        Object person = body.remove("person");
        Object company = body.remove("company");
        Object metadata = body.remove("metadata");
        String key = RouteRequest.idempotencyKeyOf(name, body, idempotencyKey);
        if (key != null) {
            body.put("idempotencyKey", key);
        }
        if (body.containsKey("sessionType")) {
            String type = String.valueOf(body.get("sessionType")).toUpperCase(Locale.ROOT);
            if (!KycValues.SessionType.ALL.contains(type)) {
                throw new ConfigurationException("sessionType must be one of "
                        + String.join(", ", KycValues.SessionType.ALL) + ".");
            }
            body.put("sessionType", type);
        }
        int identities = (person != null ? 1 : 0) + (company != null ? 1 : 0) + (body.get("customer") != null ? 1 : 0);
        if (identities > 1) {
            throw new ConfigurationException(name + " takes one of person, company or customer.");
        }
        if (person != null) {
            body.put("customer", customer("PERSON", person, "person"));
        }
        if (company != null) {
            body.put("customer", customer("COMPANY", company, "company"));
        }
        if (metadata != null) {
            if (body.containsKey("evidenceContextJson")) {
                throw new ConfigurationException(name + " takes metadata or evidenceContextJson, not both.");
            }
            body.put("evidenceContextJson", Json.stringify(Checks.plainMap(metadata, "metadata")));
        }
        if (body.containsKey("ttlMinutes")) {
            Object ttl = body.get("ttlMinutes");
            if (!RouteRequest.isInteger(ttl) || ((Number) ttl).longValue() < 1
                    || ((Number) ttl).longValue() > KycValues.SESSION_TTL_MAX_MINUTES) {
                throw new ConfigurationException("ttlMinutes must be an integer from 1 to "
                        + KycValues.SESSION_TTL_MAX_MINUTES + ".");
            }
        }
        if (body.containsKey("requiredDocumentTypes")) {
            Object types = body.get("requiredDocumentTypes");
            if (!(types instanceof Collection<?> list) || !list.stream().allMatch(type -> type instanceof String)) {
                throw new ConfigurationException("requiredDocumentTypes must be a list of document types.");
            }
        }
        return RouteRequest.data(routes.send(name, Map.of(), null, body, key));
    }

    private static Map<String, Object> customer(String type, Object value, String label) {
        Map<String, Object> customer = new LinkedHashMap<>();
        customer.put("customerType", type);
        customer.putAll(Checks.plainMap(value, label));
        return customer;
    }

    private static Map<String, Object> body(Map<String, ?> value, String label) {
        if (value == null) {
            throw new ConfigurationException(label + " must be an object (a map).");
        }
        return Checks.plainMap(value, label);
    }
}
