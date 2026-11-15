package com.lunixi.sdk.subscription;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.lunixi.sdk.http.Route;

/**
 * The subscription route table ({@code SUBSCRIPTION_ROUTES} of the Node SDK and the shared catalog): every route of
 * {@code /api/v1/subscriptions/*} and the portal-link routes under {@code /api/panel/v1/subscriptions/portal}, with
 * the {@code Idempotency-Key} rule and the body and query fields. Keys are the operation names ({@code plans.create}).
 * The surface is bearer-only: no route is signed per request.
 */
public final class SubscriptionRoutes {

    /** Fields of a plan's {@code billing} rule. */
    public static final List<String> BILLING_RULE_FIELDS = List.of("frequency", "interval", "byMonthDay",
            "byWeekDay", "timezone", "lastDayOfMonthFix", "count");

    /** The routes by operation name, in catalog order. */
    public static final Map<String, Route> ROUTES;

    private static final String SUBS = "/api/v1/subscriptions";
    private static final String PORTAL_PANEL = "/api/panel/v1/subscriptions/portal";
    private static final List<String> CURSOR_QUERY = List.of("pageSize", "pageToken");

    static {
        Map<String, Route> routes = new LinkedHashMap<>();
        routes.put("create", post("/subscriptions").keyed()
                .withRequired("customerId", "planId", "idempotencyKey")
                .withOptional("paymentCardId", "paymentMethodId", "collectionMethod", "priceUpdatePolicy",
                        "entitlementPolicy", "billingTimezone", "startsAt", "chargeOnCreate",
                        "billingCycleAnchorOverride", "skipTrial", "metadata"));
        routes.put("get", get("/subscriptions/:subscriptionId"));
        routes.put("list", get("/subscriptions").withQuery(List.of("customerId", "status"),
                CURSOR_QUERY.toArray(String[]::new)));
        routes.put("cancel", post("/subscriptions/:subscriptionId/cancel").withRequired("cancelAtPeriodEnd")
                .withOptional("reason"));
        routes.put("pause", post("/subscriptions/:subscriptionId/pause").withOptional("resumeAt")
                .withOptionalBody());
        routes.put("resume", post("/subscriptions/:subscriptionId/resume"));
        routes.put("changePlan", post("/subscriptions/:subscriptionId/change-plan").optionalKey()
                .withRequired("newPlanId").withOptional("behavior", "reason", "idempotencyKey"));
        routes.put("auditLogs", get("/subscriptions/:subscriptionId/audit-logs").withQuery("limit", "pageToken"));

        routes.put("plans.create", post("/plans").optionalKey()
                .withRequired("productId", "code", "name", "price", "currency", "billing")
                .withOptional("description", "trialPeriodDays", "maxDunningAttempts", "dunningScheduleDays",
                        "gracePeriodDays", "metadata"));
        routes.put("plans.get", get("/plans/:planId"));
        routes.put("plans.list", get("/plans").withQuery(List.of("productId", "includeArchived"),
                CURSOR_QUERY.toArray(String[]::new)));
        routes.put("plans.update", Route.of("PATCH", SUBS + "/plans/:planId").asFree());
        routes.put("plans.archive", Route.of("DELETE", SUBS + "/plans/:planId"));
        routes.put("plans.translations", get("/plans/:planId/translations"));
        routes.put("plans.upsertTranslation", Route.of("PATCH", SUBS + "/plans/:planId/translations/:locale")
                .asFree());

        routes.put("customers.upsert", Route.of("PUT", SUBS + "/customers").optionalKey()
                .withRequired("externalId").withOptional("email", "name", "phone", "locale", "metadata"));
        routes.put("customers.get", get("/customers/:customerId"));
        routes.put("customers.getByExternalId", get("/customers/by-external/:externalId"));
        routes.put("customers.list", get("/customers").withQuery(List.of("search", "includeArchived"),
                CURSOR_QUERY.toArray(String[]::new)));
        routes.put("customers.creditBalance", get("/customers/:customerId/credit-balance"));
        routes.put("customers.adjustCredit", post("/customers/:customerId/credit-balance/adjust").asFree());
        routes.put("customers.archive", Route.of("DELETE", SUBS + "/customers/:customerId"));

        routes.put("cards.add", post("/cards").optionalKey().withRequired("customerId", "paymentMethodToken")
                .withOptional("brand", "last4", "bin", "expireMonth", "expireYear", "cardholderName", "isDefault",
                        "nickname", "verificationMode", "supportedCurrencies"));
        routes.put("cards.get", get("/cards/:cardId"));
        routes.put("cards.list", get("/cards").withQuery(List.of("customerId", "includeInactive"),
                CURSOR_QUERY.toArray(String[]::new)));
        routes.put("cards.setDefault", post("/cards/:cardId/set-default"));
        routes.put("cards.deactivate", Route.of("DELETE", SUBS + "/cards/:cardId").withOptional("reason")
                .withOptionalBody());
        routes.put("cards.verify", post("/cards/:cardId/verify").asFree().withOptionalBody());
        routes.put("cards.update", Route.of("PATCH", SUBS + "/cards/:cardId").asFree());

        routes.put("invoices.list", get("/invoices").withQuery(List.of("subscriptionId", "status"),
                CURSOR_QUERY.toArray(String[]::new)));
        routes.put("invoices.get", get("/invoices/:invoiceId"));
        routes.put("invoices.void", post("/invoices/:invoiceId/void").withOptional("reason").withOptionalBody());
        routes.put("invoices.retryCharge", post("/invoices/:invoiceId/retry-charge").optionalKey());

        routes.put("portal.createSdkSession", post("/portal/sdk-session").withRequired("customerId", "origin")
                .withOptional("sdkMode", "portalLinkId"));
        routes.put("portal.createPortalLink", Route.of("POST", PORTAL_PANEL + "/links").asFree());
        routes.put("portal.listLinks", Route.of("GET", PORTAL_PANEL + "/links")
                .withQuery("customerId", "status", "limit"));
        routes.put("portal.getLink", Route.of("GET", PORTAL_PANEL + "/links/:linkId"));
        routes.put("portal.updateLink", Route.of("PATCH", PORTAL_PANEL + "/links/:linkId").asFree());
        routes.put("portal.revokeLink", Route.of("DELETE", PORTAL_PANEL + "/links/:linkId").withOptional("reason")
                .withOptionalBody());
        routes.put("portal.linkActivity", Route.of("GET", PORTAL_PANEL + "/links/:linkId/activity"));
        routes.put("portal.sendInvite", Route.of("POST", PORTAL_PANEL + "/invite").asFree());
        routes.put("portal.getSettings", Route.of("GET", PORTAL_PANEL + "/settings"));
        routes.put("portal.updateSettings", Route.of("PUT", PORTAL_PANEL + "/settings").asFree());

        routes.put("products.create", post("/products").asFree());
        routes.put("products.list", get("/products").withQuery("limit", "cursor", "active"));
        routes.put("products.get", get("/products/:productId"));
        routes.put("products.update", Route.of("PATCH", SUBS + "/products/:productId").asFree());
        routes.put("products.archive", Route.of("DELETE", SUBS + "/products/:productId"));
        routes.put("products.translations", get("/products/:productId/translations"));
        routes.put("products.upsertTranslation", Route.of("PATCH", SUBS + "/products/:productId/translations/:locale")
                .asFree());

        routes.put("features.create", post("/features").asFree());
        routes.put("features.list", get("/features").withAnyQuery());
        routes.put("features.get", get("/features/:featureId"));
        routes.put("features.update", Route.of("PATCH", SUBS + "/features/:featureId").asFree());
        routes.put("features.delete", Route.of("DELETE", SUBS + "/features/:featureId"));
        routes.put("features.translations", get("/features/:featureId/translations"));
        routes.put("features.upsertTranslation", Route.of("PATCH", SUBS + "/features/:featureId/translations/:locale")
                .asFree());

        routes.put("addons.create", post("/addons").asFree());
        routes.put("addons.list", get("/addons").withAnyQuery());
        routes.put("addons.update", Route.of("PATCH", SUBS + "/addons/:addonId").asFree());
        routes.put("addons.deactivate", Route.of("DELETE", SUBS + "/addons/:addonId"));

        routes.put("usage.record", post("/usage/record").asFree());
        routes.put("usage.checkEntitlement", post("/usage/check-entitlement").asFree());
        ROUTES = Collections.unmodifiableMap(routes);
    }

    private SubscriptionRoutes() {
    }

    private static Route get(String path) {
        return Route.of("GET", SUBS + path);
    }

    private static Route post(String path) {
        return Route.of("POST", SUBS + path);
    }

    /**
     * @param name the operation name
     * @return its route
     * @throws IllegalArgumentException for an unknown name
     */
    public static Route route(String name) {
        Route route = ROUTES.get(name);
        if (route == null) {
            throw new IllegalArgumentException("Unknown subscription route " + name + ".");
        }
        return route;
    }
}
