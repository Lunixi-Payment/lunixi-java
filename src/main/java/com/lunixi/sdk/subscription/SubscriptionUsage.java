package com.lunixi.sdk.subscription;

import java.util.Map;

import com.lunixi.sdk.internal.RouteRequest;

/** Metered usage and entitlement checks ({@code /api/v1/subscriptions/usage}). */
public final class SubscriptionUsage {

    private final RouteRequest routes;

    SubscriptionUsage(RouteRequest routes) {
        this.routes = routes;
    }

    /**
     * Records a metered-usage event: {@code {customerId | subscriptionId, featureKey, quantity, timestamp?,
     * idempotencyKey?, …}}.
     *
     * @param usage the usage event
     * @return the answer ({@code data})
     */
    public Map<String, Object> record(Map<String, ?> usage) {
        return RouteRequest.data(routes.send("usage.record", Map.of(), null,
                SubscriptionChecks.body(usage, "usage.record body"), null));
    }

    /**
     * Whether the customer is entitled / has quota left: {@code {customerId | subscriptionId, featureKey,
     * requested?, …}}.
     *
     * @param check the check
     * @return the entitlement ({@code data})
     */
    public Map<String, Object> checkEntitlement(Map<String, ?> check) {
        return RouteRequest.data(routes.send("usage.checkEntitlement", Map.of(), null,
                SubscriptionChecks.body(check, "usage.checkEntitlement body"), null));
    }
}
