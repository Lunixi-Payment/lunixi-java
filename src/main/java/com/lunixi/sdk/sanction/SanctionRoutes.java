package com.lunixi.sdk.sanction;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.lunixi.sdk.http.Route;

/**
 * The single description of every sanction route an API key can reach ({@code /api/v1/sanction/*}): method, path
 * template, the body fields the gateway forwards to the sanction service, the query parameters it reads, the nested
 * list fields and the value sets. {@link SanctionClient} checks a call against it before sending (fields outside the
 * service's request are otherwise dropped silently). The tables are the Node SDK's {@code SANCTION_ROUTES},
 * {@code SANCTION_NESTED_FIELDS} and {@code SANCTION_VALUE_SETS}, and the shared catalog's groups of the same names.
 *
 * <p>Every route needs {@code sanction:view_results}. Not listed, on purpose: the screening report PDF (panel
 * session only), the deny-list write and the analyst decision, approval and assignment routes (permissions an API key
 * does not carry), and the list-source syncs (compliance operator routes).
 */
public final class SanctionRoutes {

    /**
     * The fields of a nested list, checked element by element.
     *
     * @param required fields each element needs
     * @param optional other fields an element may carry
     */
    public record NestedFields(List<String> required, List<String> optional) {
        public NestedFields {
            required = List.copyOf(required);
            optional = List.copyOf(optional);
        }
    }

    /** Nested list rules by {@code route.field}. */
    public static final Map<String, NestedFields> NESTED_FIELDS = Map.of(
            "screenName.identifiers", new NestedFields(List.of("type", "value"), List.of()),
            "screenTransaction.parties", new NestedFields(List.of("name"),
                    List.of("role", "subjectType", "countryCode", "customerId", "dateOfBirth", "nationalId")),
            "screenTransaction.accounts", new NestedFields(List.of("accountType", "value"),
                    List.of("role", "providerCode", "chain")));

    /** Value sets by {@code route.field}, checked before sending; the sanction service refuses anything else. */
    public static final Map<String, List<String>> VALUE_SETS = Map.of(
            "screenName.riskDomains", SanctionValues.RiskDomain.ALL,
            "screenName.mode", SanctionValues.ScreenMode.ALL,
            "screenName.subjectType", SanctionValues.SubjectType.ALL,
            "screenWallet.mode", SanctionValues.WalletScreenMode.ALL,
            "registerMonitoring.subjectType", SanctionValues.SubjectType.ALL,
            "registerMonitoring.riskDomainScope", SanctionValues.RiskDomain.ALL);

    /** Every route by name, in the Node table's order. */
    public static final Map<String, Route> ROUTES;

    private static final String BASE = "/api/v1/sanction";
    private static final List<String> PAGE_QUERY = List.of("limit", "cursor");

    static {
        Map<String, Route> routes = new LinkedHashMap<>();
        routes.put("screenName", screening("/screening/name").withRequired("name")
                .withOptional("subjectType", "country", "dateOfBirth", "identifiers", "riskDomains", "mode",
                        "threshold", "customerReference"));
        routes.put("screenWallet", screening("/screening/wallet").withRequired("walletAddress")
                .withOptional("chain", "mode", "customerReference"));
        routes.put("screenAccount", screening("/screening/account").withRequired("accountValue", "accountScheme")
                .withOptional("providerCode", "productChannel", "customerReference"));
        // Neither parties nor accounts: the sanction service holds the transaction as not screenable.
        routes.put("screenTransaction", screening("/screening/transaction")
                .withOptional("transactionType", "direction", "amount", "currency", "channel", "occurredAt",
                        "parties", "accounts", "metadataJson")
                .withAnyOf("parties", "accounts"));
        routes.put("screenPublicProcurementBan", screening("/intelligence/public-procurement-ban")
                .withOptional("entityName", "tckn", "vkn", "entityType", "onlyActive", "limit", "customerReference",
                        "productChannel")
                .withAnyOf("entityName", "tckn", "vkn"));
        routes.put("screenAdverseMedia", screening("/intelligence/adverse-media")
                .withOptional("entityName", "entityType", "tckn", "vkn", "countryCode", "limit", "onlyActive",
                        "customerReference", "productChannel"));
        routes.put("evaluateBehavioralAlert", screening("/intelligence/behavioral-alert")
                .withOptional("subjectReference", "customerReference", "productChannel", "velocityScore",
                        "unusualPattern", "countryRiskLevel", "metadataJson"));
        routes.put("listScreeningHistory", get("/screening/history").withQuery(PAGE_QUERY));

        routes.put("registerMonitoring", Route.of("POST", BASE + "/monitoring/register")
                .withRequired("externalCustomerId")
                .withOptional("subjectType", "profileJson", "frequency", "riskDomainScope"));
        routes.put("listMonitoringAlerts", get("/monitoring/alerts")
                .withQuery(List.of("status", "limit", "cursor", "includeTotal")));
        routes.put("listMonitoredEntities", get("/monitoring/entities")
                .withQuery(List.of("activeOnly", "limit", "cursor", "includeTotal")));
        routes.put("listMonitoringMatches", get("/monitoring/matches").withQuery(List.of("status", "limit",
                "cursor")));
        routes.put("getDatasetFreshness", get("/monitoring/dataset-freshness"));

        routes.put("searchEntities", get("/intelligence/entity-search").withQuery("q", "limit"));
        routes.put("getEntityProfile", get("/intelligence/entity-profiles/:entityId")
                .withQuery("screeningRequestId"));
        routes.put("getEntityGraph", get("/intelligence/entity-graph/:entityId").withQuery("depth"));
        routes.put("getIndirectExposure", get("/intelligence/indirect-exposure/:entityId")
                .withQuery("maxDepth", "entityIds"));
        routes.put("getOwnershipAggregation", get("/intelligence/ownership-aggregation/:entityId")
                .withQuery("depth", "entityIds"));

        routes.put("listBlacklistEntries", get("/blacklist").withQuery(PAGE_QUERY));
        ROUTES = Collections.unmodifiableMap(routes);
    }

    private SanctionRoutes() {
    }

    /**
     * @param name a route name, e.g. {@code screenName}
     * @return the route
     * @throws IllegalArgumentException for an unknown name
     */
    public static Route route(String name) {
        Route route = ROUTES.get(name);
        if (route == null) {
            throw new IllegalArgumentException("Unknown sanction route " + name);
        }
        return route;
    }

    private static Route screening(String path) {
        return Route.of("POST", BASE + path).asScreening();
    }

    private static Route get(String path) {
        return Route.of("GET", BASE + path);
    }
}
