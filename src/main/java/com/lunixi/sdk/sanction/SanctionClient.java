package com.lunixi.sdk.sanction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.lunixi.sdk.exception.ConfigurationException;
import com.lunixi.sdk.exception.LunixiApiException;
import com.lunixi.sdk.http.Envelope;
import com.lunixi.sdk.http.Route;
import com.lunixi.sdk.internal.ApiClient;
import com.lunixi.sdk.internal.Checks;
import com.lunixi.sdk.internal.CursorPages;
import com.lunixi.sdk.internal.RequestOptions;
import com.lunixi.sdk.json.Json;

/**
 * Sanction screening ({@code /api/v1/sanction/*}), {@code client.sanction()}.
 *
 * <p>Calls carry the access token minted from your API key (no per-request signature). The key needs the
 * {@code SANCTION_SERVICE} product and the {@code sanction:view_results} permission (403 otherwise).
 *
 * <p>Screening returns matches, not verdicts: the decision is yours. Read a result with
 * {@link #isClearScreening(Map)}: an empty match list is clear only when {@code coverageStatus} is
 * {@code COMPLETE}. When the screening cannot run (no list publication can be pinned) the gateway answers 503 and
 * the call throws {@link com.lunixi.sdk.exception.ServiceUnavailableException}; a {@code 2xx} answer whose
 * {@code status} is not {@code success} throws {@link LunixiApiException}. It never comes back as an empty result.
 *
 * <p>Bodies and filters are checked against {@link SanctionRoutes} before sending: an unknown field or filter, a
 * missing required field, an empty "at least one of" group, a nested list element with unknown or missing fields,
 * or a risk domain / mode outside its set throws {@link ConfigurationException}.
 *
 * <p>Single answers are the envelope's {@code data}. The envelope's {@code requestId} is the HTTP request's id; the
 * screening's own id is {@code screeningRequestId} (name screening). List calls return a {@link SanctionPage}.
 */
public final class SanctionClient {

    private static final Set<String> BLOCKING_DECISIONS = Set.of("block", "blocked", "reject", "rejected", "review",
            "hit", "escalate");

    private final ApiClient api;

    /**
     * @param api the gateway client
     */
    public SanctionClient(ApiClient api) {
        this.api = api;
    }

    // ── Screening ─────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Screens a person or organization ({@code POST /screening/name}).
     *
     * @param request {@code name}; optional {@code subjectType} ({@link SanctionValues.SubjectType}),
     *                {@code country}, {@code dateOfBirth}, {@code identifiers} ({@code [{type, value}]}),
     *                {@code riskDomains} ({@link SanctionValues.RiskDomain}), {@code mode}
     *                ({@link SanctionValues.ScreenMode}), {@code threshold}, {@code customerReference}
     * @return the screening ({@code data})
     */
    public Map<String, Object> screenName(Map<String, ?> request) {
        return screening("screenName", request);
    }

    /** Screens a crypto wallet address, an exact match against the listed addresses ({@code POST /screening/wallet}). */
    public Map<String, Object> screenWallet(Map<String, ?> request) {
        return screening("screenWallet", request);
    }

    /** Screens a bank account / IBAN against your own deny list and account risk data ({@code POST /screening/account}). */
    public Map<String, Object> screenAccount(Map<String, ?> request) {
        return screening("screenAccount", request);
    }

    /** Screens every party and account of a transaction against one list publication ({@code parties} or {@code accounts}). */
    public Map<String, Object> screenTransaction(Map<String, ?> request) {
        return screening("screenTransaction", request);
    }

    /**
     * Screens public procurement debarment lists by name and TCKN/VKN (at least one of {@code entityName},
     * {@code tckn}, {@code vkn}). The Turkish procurement list is not published, so {@code coverageStatus} is
     * {@code PARTIAL} and {@code uncoveredSources} names it: no match is not a clean result for it.
     */
    public Map<String, Object> screenPublicProcurementBan(Map<String, ?> request) {
        return screening("screenPublicProcurementBan", request);
    }

    /** Screens adverse media. Runs only when your adverse-media policy is enabled. */
    public Map<String, Object> screenAdverseMedia(Map<String, ?> request) {
        return screening("screenAdverseMedia", request);
    }

    /** Evaluates a behavioural risk signal. */
    public Map<String, Object> evaluateBehavioralAlert(Map<String, ?> request) {
        return screening("evaluateBehavioralAlert", request);
    }

    /** @return the first page of earlier screenings */
    public SanctionPage listScreeningHistory() {
        return listScreeningHistory(Map.of());
    }

    /** Earlier screenings (audit trail), newest first ({@code limit}, {@code cursor}). */
    public SanctionPage listScreeningHistory(Map<String, ?> filters) {
        return new SanctionPage(invoke("listScreeningHistory", Map.of(), filters, null));
    }

    /** Every earlier screening, following the cursors. */
    public Iterable<Map<String, Object>> iterateScreeningHistory(Map<String, ?> filters) {
        return CursorPages.iterate(filters, this::listScreeningHistory, SanctionPage::items,
                page -> page.nextCursor().orElse(null));
    }

    // ── Ongoing monitoring ────────────────────────────────────────────────────────────────────────────────────

    /**
     * Registers a subject for ongoing monitoring ({@code POST /monitoring/register}); returns
     * {@code {monitoredEntityId}}. {@code profile} (a map: name, dateOfBirth, country, identifiers…) is sent as the
     * {@code profileJson} string; pass one of the two.
     *
     * @param request {@code externalCustomerId}; optional {@code subjectType}, {@code profile} or
     *                {@code profileJson}, {@code frequency}, {@code riskDomainScope}
     * @return the registration ({@code data})
     */
    public Map<String, Object> registerMonitoring(Map<String, ?> request) {
        Map<String, Object> body = request == null ? new LinkedHashMap<>()
                : Checks.plainMap(request, "registerMonitoring body");
        Object profile = body.remove("profile");
        if (profile != null) {
            if (body.containsKey("profileJson")) {
                throw new ConfigurationException("registerMonitoring takes profile or profileJson, not both.");
            }
            body.put("profileJson", Json.stringify(profile));
        }
        return Envelope.data(invoke("registerMonitoring", Map.of(), null, body));
    }

    /** @return the first page of monitoring alerts */
    public SanctionPage listMonitoringAlerts() {
        return listMonitoringAlerts(Map.of());
    }

    /** New-match alerts raised by monitoring ({@code status}, {@code limit}, {@code cursor}, {@code includeTotal}). */
    public SanctionPage listMonitoringAlerts(Map<String, ?> filters) {
        return new SanctionPage(invoke("listMonitoringAlerts", Map.of(), filters, null));
    }

    /** Every monitoring alert, following the cursors. */
    public Iterable<Map<String, Object>> iterateMonitoringAlerts(Map<String, ?> filters) {
        return CursorPages.iterate(filters, this::listMonitoringAlerts, SanctionPage::items,
                page -> page.nextCursor().orElse(null));
    }

    /** @return the first page of monitored subjects */
    public SanctionPage listMonitoredEntities() {
        return listMonitoredEntities(Map.of());
    }

    /** Monitored subjects; {@code activeOnly} defaults to {@code true} on the gateway. */
    public SanctionPage listMonitoredEntities(Map<String, ?> filters) {
        return new SanctionPage(invoke("listMonitoredEntities", Map.of(), filters, null));
    }

    /** Every monitored subject, following the cursors. */
    public Iterable<Map<String, Object>> iterateMonitoredEntities(Map<String, ?> filters) {
        return CursorPages.iterate(filters, this::listMonitoredEntities, SanctionPage::items,
                page -> page.nextCursor().orElse(null));
    }

    /** @return the first page of monitoring matches */
    public SanctionPage listMonitoringMatches() {
        return listMonitoringMatches(Map.of());
    }

    /** Matches raised by monitoring ({@code status}, {@code limit}, {@code cursor}). */
    public SanctionPage listMonitoringMatches(Map<String, ?> filters) {
        return new SanctionPage(invoke("listMonitoringMatches", Map.of(), filters, null));
    }

    /** Every monitoring match, following the cursors. */
    public Iterable<Map<String, Object>> iterateMonitoringMatches(Map<String, ?> filters) {
        return CursorPages.iterate(filters, this::listMonitoringMatches, SanctionPage::items,
                page -> page.nextCursor().orElse(null));
    }

    /**
     * Freshness of the source lists: {@code {manifest}}. A manifest that could not be produced comes back as
     * {@code status: "failure"} and throws.
     */
    public Map<String, Object> getDatasetFreshness() {
        return unwrapScreening(invoke("getDatasetFreshness", Map.of(), null, null));
    }

    // ── Entity intelligence ───────────────────────────────────────────────────────────────────────────────────

    /** Entity search by name ({@code q}, {@code limit}). */
    public Map<String, Object> searchEntities(Map<String, ?> filters) {
        return Envelope.data(invoke("searchEntities", Map.of(), filters, null));
    }

    /** Entity search by name. */
    public Map<String, Object> searchEntities(String q) {
        return searchEntities(Map.of("q", Checks.text(q, "q")));
    }

    /** @return the entity's risk profile */
    public Map<String, Object> getEntityProfile(String entityId) {
        return getEntityProfile(entityId, null);
    }

    /**
     * The entity's risk profile.
     *
     * @param entityId           the entity id
     * @param screeningRequestId reads the profile on that screening's list publication, or {@code null}
     * @return the profile ({@code data})
     */
    public Map<String, Object> getEntityProfile(String entityId, String screeningRequestId) {
        return Envelope.data(invoke("getEntityProfile", entity(entityId),
                compact("screeningRequestId", screeningRequestId), null));
    }

    /** @return the entity's relationship graph at the gateway's default depth */
    public Map<String, Object> getEntityGraph(String entityId) {
        return getEntityGraph(entityId, null);
    }

    /** The entity's relationship graph; {@code depth} 1–3, or {@code null}. */
    public Map<String, Object> getEntityGraph(String entityId, Integer depth) {
        return Envelope.data(invoke("getEntityGraph", entity(entityId), compact("depth", depth), null));
    }

    /** @return the entity's indirect exposure paths */
    public Map<String, Object> getIndirectExposure(String entityId) {
        return getIndirectExposure(entityId, null, null);
    }

    /**
     * Indirect exposure paths.
     *
     * @param entityId  the entity id
     * @param maxDepth  the longest path, or {@code null}
     * @param entityIds the records of a merged match (sent comma-separated), or {@code null}
     * @return the exposure ({@code data})
     */
    public Map<String, Object> getIndirectExposure(String entityId, Integer maxDepth, List<String> entityIds) {
        return Envelope.data(invoke("getIndirectExposure", entity(entityId),
                compact("maxDepth", maxDepth, "entityIds", idList(entityIds)), null));
    }

    /** @return the entity's ownership aggregation (50% rule) */
    public Map<String, Object> getOwnershipAggregation(String entityId) {
        return getOwnershipAggregation(entityId, null, null);
    }

    /**
     * Ownership aggregation (50% rule).
     *
     * @param entityId  the entity id
     * @param depth     the depth, or {@code null}
     * @param entityIds the records of a merged match (sent comma-separated), or {@code null}
     * @return the aggregation ({@code data})
     */
    public Map<String, Object> getOwnershipAggregation(String entityId, Integer depth, List<String> entityIds) {
        return Envelope.data(invoke("getOwnershipAggregation", entity(entityId),
                compact("depth", depth, "entityIds", idList(entityIds)), null));
    }

    // ── Deny list ─────────────────────────────────────────────────────────────────────────────────────────────

    /** @return the first page of your own deny-list entries */
    public SanctionPage listBlacklistEntries() {
        return listBlacklistEntries(Map.of());
    }

    /** Your own deny-list entries (read only with an API key; {@code limit}, {@code cursor}). */
    public SanctionPage listBlacklistEntries(Map<String, ?> filters) {
        return new SanctionPage(invoke("listBlacklistEntries", Map.of(), filters, null));
    }

    /** Every deny-list entry, following the cursors. */
    public Iterable<Map<String, Object>> iterateBlacklistEntries(Map<String, ?> filters) {
        return CursorPages.iterate(filters, this::listBlacklistEntries, SanctionPage::items,
                page -> page.nextCursor().orElse(null));
    }

    // ── Reading a result ──────────────────────────────────────────────────────────────────────────────────────

    /**
     * Whether a screening answer can be read as "nothing found".
     *
     * <p>True only when the answer has no match or hit of any kind ({@code matches}, {@code hits},
     * {@code publicationHits}) and, when it reports coverage, its {@code coverageStatus} is {@code COMPLETE}. A
     * {@code PARTIAL} or {@code DEGRADED} screening, a replay without coverage ({@code null}), a wallet held by a
     * listed party ({@code isSanctioned}), a deny-list hit ({@code isBlacklisted}), a matched procurement ban
     * ({@code matched}), a transaction that needs a case ({@code caseRequired}) or a blocking {@code decision} is
     * never clear. Account and adverse-media screening do not report coverage; for them the hits and flags decide.
     *
     * @param result the {@code data} a screening method returned
     * @return {@code true} only for a clean, complete result
     */
    public static boolean isClearScreening(Map<String, ?> result) {
        if (result == null) {
            return false;
        }
        for (String key : List.of("matches", "hits", "publicationHits")) {
            if (result.get(key) instanceof Collection<?> list && !list.isEmpty()) {
                return false;
            }
        }
        for (String key : List.of("isSanctioned", "isBlacklisted", "matched", "caseRequired")) {
            if (Boolean.TRUE.equals(result.get(key))) {
                return false;
            }
        }
        if (result.get("decision") instanceof String decision
                && BLOCKING_DECISIONS.contains(decision.toLowerCase(Locale.ROOT))) {
            return false;
        }
        if (result.containsKey("coverageStatus")) {
            return SanctionValues.CoverageStatus.COMPLETE.equals(result.get("coverageStatus"));
        }
        return true;
    }

    // ── Internals ─────────────────────────────────────────────────────────────────────────────────────────────

    private Map<String, Object> screening(String routeName, Map<String, ?> request) {
        return unwrapScreening(invoke(routeName, Map.of(), null, request));
    }

    /** Sends one route of {@link SanctionRoutes} with the access token (no signature). */
    private Map<String, Object> invoke(String routeName, Map<String, String> path, Map<String, ?> query,
                                       Map<String, ?> body) {
        Route route = SanctionRoutes.route(routeName);
        String filled = route.fill(path);
        RequestOptions.Builder options = RequestOptions.builder();
        if (query != null) {
            pickQuery(routeName, route, query).forEach(options::query);
        }
        Object payload = "GET".equals(route.method()) ? null
                : checkBody(routeName, route, body == null ? Map.of() : body);
        return api.request(route.method(), filled, payload, options.build());
    }

    static Map<String, Object> checkBody(String routeName, Route route, Map<String, ?> body) {
        Map<String, Object> plain = fields(routeName, Checks.plainMap(body, routeName + " body"), route.required(),
                route.optional());
        if (!route.anyOf().isEmpty() && route.anyOf().stream().noneMatch(key -> Checks.presentNonEmpty(plain.get(key)))) {
            throw new ConfigurationException(routeName + " requires at least one of "
                    + String.join(", ", route.anyOf()) + ".");
        }
        for (Map.Entry<String, Object> entry : plain.entrySet()) {
            String label = routeName + "." + entry.getKey();
            SanctionRoutes.NestedFields nested = SanctionRoutes.NESTED_FIELDS.get(label);
            if (nested != null) {
                entry.setValue(checkRows(label, nested, entry.getValue()));
            }
            List<String> allowed = SanctionRoutes.VALUE_SETS.get(label);
            if (allowed != null) {
                checkValues(label, allowed, entry.getValue());
            }
        }
        return plain;
    }

    private static Map<String, Object> fields(String label, Map<String, Object> plain, List<String> required,
                                              List<String> optional) {
        List<String> unknown = new ArrayList<>();
        for (String key : plain.keySet()) {
            if (!required.contains(key) && !optional.contains(key)) {
                unknown.add(key);
            }
        }
        if (!unknown.isEmpty()) {
            throw new ConfigurationException(label + " does not accept " + String.join(", ", unknown) + ".");
        }
        List<String> missing = new ArrayList<>();
        for (String key : required) {
            if (!Checks.presentNonEmpty(plain.get(key))) {
                missing.add(key);
            }
        }
        if (!missing.isEmpty()) {
            throw new ConfigurationException(label + " requires " + String.join(", ", missing) + ".");
        }
        return plain;
    }

    private static List<Map<String, Object>> checkRows(String label, SanctionRoutes.NestedFields spec, Object rows) {
        if (!(rows instanceof Collection<?> list)) {
            throw new ConfigurationException(label + " must be a list.");
        }
        List<Map<String, Object>> out = new ArrayList<>();
        int index = 0;
        for (Object row : list) {
            String rowLabel = label + "[" + index++ + "]";
            out.add(fields(rowLabel, Checks.plainMap(row, rowLabel), spec.required(), spec.optional()));
        }
        return out;
    }

    private static void checkValues(String label, List<String> allowed, Object value) {
        List<String> outside = new ArrayList<>();
        for (Object entry : value instanceof Collection<?> list ? list : List.of(value)) {
            if (!(entry instanceof String text) || !allowed.contains(text)) {
                outside.add(Json.stringify(entry));
            }
        }
        if (!outside.isEmpty()) {
            throw new ConfigurationException(label + " must be one of " + String.join(", ", allowed) + "; got "
                    + String.join(", ", outside) + ".");
        }
    }

    private static Map<String, String> pickQuery(String routeName, Route route, Map<String, ?> filters) {
        List<String> unknown = new ArrayList<>();
        for (Map.Entry<String, ?> entry : filters.entrySet()) {
            if (!route.query().contains(entry.getKey()) && entry.getValue() != null) {
                unknown.add(entry.getKey());
            }
        }
        if (!unknown.isEmpty()) {
            throw new ConfigurationException(routeName + " does not accept the filter(s) "
                    + String.join(", ", unknown) + ".");
        }
        Map<String, String> query = new LinkedHashMap<>();
        for (String key : route.query()) {
            Object value = filters.get(key);
            if (value instanceof Collection<?> list) {
                value = idList(list);
            }
            if (value != null && !"".equals(value)) {
                query.put(key, String.valueOf(value));
            }
        }
        return query;
    }

    /** Member ids of a merged match, sent comma-separated (the gateway accepts both forms). */
    private static String idList(Collection<?> ids) {
        if (ids == null) {
            return null;
        }
        List<String> values = new ArrayList<>();
        for (Object id : ids) {
            String text = id == null ? "" : String.valueOf(id).trim();
            if (!text.isEmpty()) {
                values.add(text);
            }
        }
        return values.isEmpty() ? null : String.join(",", values);
    }

    /**
     * A {@code 2xx} answer that is not {@code status: "success"} is refused rather than read: a screening that did
     * not run must never look like an empty result.
     */
    private static Map<String, Object> unwrapScreening(Map<String, Object> response) {
        if (response.get("status") instanceof String status && !"success".equals(status)) {
            Object message = response.get("message");
            Object statusCode = response.get("statusCode");
            throw new LunixiApiException(message instanceof String text && !text.isEmpty() ? text
                    : "The screening did not complete.",
                    statusCode instanceof Number number ? number.intValue() : 0,
                    Envelope.code(response).orElse(null), response, Envelope.requestId(response).orElse(null));
        }
        return Envelope.data(response);
    }

    private static Map<String, String> entity(String entityId) {
        Map<String, String> path = new LinkedHashMap<>();
        path.put("entityId", entityId);
        return path;
    }

    private static Map<String, Object> compact(Object... pairs) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            if (pairs[i + 1] != null) {
                out.put((String) pairs[i], pairs[i + 1]);
            }
        }
        return out;
    }
}
