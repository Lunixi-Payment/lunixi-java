package com.lunixi.sdk.subscription;

import java.util.Map;

import com.lunixi.sdk.internal.RouteRequest;

/** Customers ({@code /api/v1/subscriptions/customers}), upserted by your own {@code externalId}. */
public final class SubscriptionCustomers {

    private final RouteRequest routes;

    SubscriptionCustomers(RouteRequest routes) {
        this.routes = routes;
    }

    /**
     * Same as {@link #upsert(Map, String)} without an {@code Idempotency-Key}.
     *
     * @param request the customer
     * @return the customer ({@code data})
     */
    public Map<String, Object> upsert(Map<String, ?> request) {
        return upsert(request, null);
    }

    /**
     * Creates or updates the customer of your {@code externalId} (one stable customer per user):
     * {@code externalId}, and optionally {@code email}, {@code name}, {@code phone}, {@code locale},
     * {@code metadata}.
     *
     * @param request        the customer
     * @param idempotencyKey the key, or {@code null}
     * @return the customer ({@code data})
     */
    public Map<String, Object> upsert(Map<String, ?> request, String idempotencyKey) {
        return RouteRequest.data(routes.send("customers.upsert", Map.of(), null,
                SubscriptionChecks.body(request, "customers.upsert request"), idempotencyKey));
    }

    /**
     * @param customerId the customer id
     * @return the customer ({@code data})
     */
    public Map<String, Object> get(String customerId) {
        return RouteRequest.data(routes.send("customers.get", RouteRequest.params("customerId", customerId), null,
                null, null));
    }

    /**
     * @param externalId your own customer id
     * @return the customer ({@code data})
     */
    public Map<String, Object> getByExternalId(String externalId) {
        return RouteRequest.data(routes.send("customers.getByExternalId",
                RouteRequest.params("externalId", externalId), null, null, null));
    }

    /** @return the first page of customers */
    public SubscriptionPage list() {
        return list(Map.of());
    }

    /**
     * @param filters {@code search}, {@code includeArchived}, {@code pageSize}, {@code pageToken}
     * @return one page of customers
     */
    public SubscriptionPage list(Map<String, ?> filters) {
        return new SubscriptionPage(routes.send("customers.list", Map.of(), filters, null, null));
    }

    /**
     * @param customerId the customer id
     * @return the credit balance ({@code data})
     */
    public Map<String, Object> creditBalance(String customerId) {
        return RouteRequest.data(routes.send("customers.creditBalance", RouteRequest.params("customerId", customerId),
                null, null, null));
    }

    /**
     * Grants or deducts credit: {@code {amountMinor, currency, reason, type, …}} ({@code amountMinor} a minor-unit
     * string).
     *
     * @param customerId the customer id
     * @param adjustment the adjustment
     * @return the credit balance ({@code data})
     */
    public Map<String, Object> adjustCredit(String customerId, Map<String, ?> adjustment) {
        return RouteRequest.data(routes.send("customers.adjustCredit", RouteRequest.params("customerId", customerId),
                null, SubscriptionChecks.body(adjustment, "customers.adjustCredit body"), null));
    }

    /**
     * @param customerId the customer id
     * @return the answer ({@code data})
     */
    public Map<String, Object> archive(String customerId) {
        return RouteRequest.data(routes.send("customers.archive", RouteRequest.params("customerId", customerId),
                null, null, null));
    }
}
