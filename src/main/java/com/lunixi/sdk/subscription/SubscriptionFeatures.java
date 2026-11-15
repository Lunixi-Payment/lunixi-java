package com.lunixi.sdk.subscription;

import java.util.Map;

import com.lunixi.sdk.http.RoutePage;
import com.lunixi.sdk.internal.RouteRequest;

/** Entitlement features ({@code /api/v1/subscriptions/features}). */
public final class SubscriptionFeatures {

    private final RouteRequest routes;

    SubscriptionFeatures(RouteRequest routes) {
        this.routes = routes;
    }

    /**
     * @param feature the feature as the gateway documents it
     * @return the feature ({@code data})
     */
    public Map<String, Object> create(Map<String, ?> feature) {
        return RouteRequest.data(routes.send("features.create", Map.of(), null,
                SubscriptionChecks.body(feature, "features.create body"), null));
    }

    /** @return the first page of features */
    public RoutePage list() {
        return list(Map.of());
    }

    /**
     * @param filters any scalar filters the gateway documents
     * @return one page of features
     */
    public RoutePage list(Map<String, ?> filters) {
        return new RoutePage(routes.send("features.list", Map.of(), filters, null, null));
    }

    /**
     * @param featureId the feature id
     * @return the feature ({@code data})
     */
    public Map<String, Object> get(String featureId) {
        return RouteRequest.data(routes.send("features.get", RouteRequest.params("featureId", featureId), null, null, null));
    }

    /**
     * @param featureId the feature id
     * @param changes the changes
     * @return the feature ({@code data})
     */
    public Map<String, Object> update(String featureId, Map<String, ?> changes) {
        return RouteRequest.data(routes.send("features.update", RouteRequest.params("featureId", featureId), null,
                SubscriptionChecks.body(changes, "features.update body"), null));
    }

    /**
     * @param featureId the feature id
     * @return the answer ({@code data})
     */
    public Map<String, Object> delete(String featureId) {
        return RouteRequest.data(routes.send("features.delete", RouteRequest.params("featureId", featureId), null, null, null));
    }

    /**
     * @param featureId the feature id
     * @return the feature's translations ({@code data})
     */
    public Map<String, Object> translations(String featureId) {
        return RouteRequest.data(routes.send("features.translations", RouteRequest.params("featureId", featureId), null, null, null));
    }

    /**
     * @param featureId the feature id
     * @param locale the locale ({@code en}, {@code tr} …)
     * @param translation the translated fields
     * @return the translation ({@code data})
     */
    public Map<String, Object> upsertTranslation(String featureId, String locale, Map<String, ?> translation) {
        return RouteRequest.data(routes.send("features.upsertTranslation",
                RouteRequest.params("featureId", featureId, "locale", locale), null,
                SubscriptionChecks.body(translation, "features.upsertTranslation body"), null));
    }
}
