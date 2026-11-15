package com.lunixi.sdk.subscription;

import java.util.Map;

import com.lunixi.sdk.http.RoutePage;
import com.lunixi.sdk.internal.RouteRequest;

/** Add-ons ({@code /api/v1/subscriptions/addons}). */
public final class SubscriptionAddons {

    private final RouteRequest routes;

    SubscriptionAddons(RouteRequest routes) {
        this.routes = routes;
    }

    /**
     * @param addon the addon as the gateway documents it
     * @return the addon ({@code data})
     */
    public Map<String, Object> create(Map<String, ?> addon) {
        return RouteRequest.data(routes.send("addons.create", Map.of(), null,
                SubscriptionChecks.body(addon, "addons.create body"), null));
    }

    /** @return the first page of add-ons */
    public RoutePage list() {
        return list(Map.of());
    }

    /**
     * @param filters any scalar filters the gateway documents
     * @return one page of add-ons
     */
    public RoutePage list(Map<String, ?> filters) {
        return new RoutePage(routes.send("addons.list", Map.of(), filters, null, null));
    }

    /**
     * @param addonId the add-on id
     * @param changes the changes
     * @return the add-on ({@code data})
     */
    public Map<String, Object> update(String addonId, Map<String, ?> changes) {
        return RouteRequest.data(routes.send("addons.update", RouteRequest.params("addonId", addonId), null,
                SubscriptionChecks.body(changes, "addons.update body"), null));
    }

    /**
     * @param addonId the add-on id
     * @return the add-on ({@code data})
     */
    public Map<String, Object> deactivate(String addonId) {
        return RouteRequest.data(routes.send("addons.deactivate", RouteRequest.params("addonId", addonId), null, null, null));
    }
}
