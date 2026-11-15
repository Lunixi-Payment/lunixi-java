package com.lunixi.sdk.subscription;

import java.util.Map;

import com.lunixi.sdk.http.RoutePage;
import com.lunixi.sdk.internal.RouteRequest;

/** Catalog products ({@code /api/v1/subscriptions/products}): groups of plans. */
public final class SubscriptionProducts {

    private final RouteRequest routes;

    SubscriptionProducts(RouteRequest routes) {
        this.routes = routes;
    }

    /**
     * @param product the product as the gateway documents it
     * @return the product ({@code data})
     */
    public Map<String, Object> create(Map<String, ?> product) {
        return RouteRequest.data(routes.send("products.create", Map.of(), null,
                SubscriptionChecks.body(product, "products.create body"), null));
    }

    /** @return the first page of products */
    public RoutePage list() {
        return list(Map.of());
    }

    /**
     * @param filters {@code limit}, {@code cursor}, {@code active}; send {@link RoutePage#nextCursor()} back as {@code cursor}
     * @return one page of products
     */
    public RoutePage list(Map<String, ?> filters) {
        return new RoutePage(routes.send("products.list", Map.of(), filters, null, null));
    }

    /**
     * @param productId the product id
     * @return the product ({@code data})
     */
    public Map<String, Object> get(String productId) {
        return RouteRequest.data(routes.send("products.get", RouteRequest.params("productId", productId), null, null, null));
    }

    /**
     * @param productId the product id
     * @param changes the changes
     * @return the product ({@code data})
     */
    public Map<String, Object> update(String productId, Map<String, ?> changes) {
        return RouteRequest.data(routes.send("products.update", RouteRequest.params("productId", productId), null,
                SubscriptionChecks.body(changes, "products.update body"), null));
    }

    /**
     * @param productId the product id
     * @return the answer ({@code data})
     */
    public Map<String, Object> archive(String productId) {
        return RouteRequest.data(routes.send("products.archive", RouteRequest.params("productId", productId), null, null, null));
    }

    /**
     * @param productId the product id
     * @return the product's translations ({@code data})
     */
    public Map<String, Object> translations(String productId) {
        return RouteRequest.data(routes.send("products.translations", RouteRequest.params("productId", productId), null, null, null));
    }

    /**
     * @param productId the product id
     * @param locale the locale ({@code en}, {@code tr} …)
     * @param translation the translated fields
     * @return the translation ({@code data})
     */
    public Map<String, Object> upsertTranslation(String productId, String locale, Map<String, ?> translation) {
        return RouteRequest.data(routes.send("products.upsertTranslation",
                RouteRequest.params("productId", productId, "locale", locale), null,
                SubscriptionChecks.body(translation, "products.upsertTranslation body"), null));
    }
}
