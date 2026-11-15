package com.lunixi.sdk.subscription;

import java.util.Map;

import com.lunixi.sdk.exception.ConfigurationException;
import com.lunixi.sdk.internal.ApiClient;
import com.lunixi.sdk.internal.Checks;
import com.lunixi.sdk.internal.RouteRequest;

/**
 * Subscriptions ({@code /api/v1/subscriptions/*}), {@code client.subscriptions()}.
 *
 * <p>Calls carry the access token minted from your API key (no per-request signature). Creating a subscription
 * needs an {@code Idempotency-Key} (reuse it when retrying the same subscription); plan, customer, card, plan-change
 * and charge-retry writes accept one.
 *
 * <p>Amounts are canonical minor-unit strings ({@code "9900"} = 99.00 TRY; {@code "0"} for a free plan), checked in
 * bodies, filters and answers. Every call is checked against {@link SubscriptionRoutes} before sending; a rule
 * violation throws {@link ConfigurationException} and nothing is sent. Entity calls return the envelope's
 * {@code data}; subscription, plan, customer, card, invoice and audit-log lists return a {@link SubscriptionPage}.
 * The related resources hang off {@link #plans()}, {@link #customers()}, {@link #cards()}, {@link #invoices()},
 * {@link #portal()}, {@link #products()}, {@link #features()}, {@link #addons()} and {@link #usage()}.
 */
public final class SubscriptionsClient {

    private final RouteRequest routes;
    private final SubscriptionPlans plans;
    private final SubscriptionCustomers customers;
    private final SubscriptionCards cards;
    private final SubscriptionInvoices invoices;
    private final SubscriptionPortal portal;
    private final SubscriptionProducts products;
    private final SubscriptionFeatures features;
    private final SubscriptionAddons addons;
    private final SubscriptionUsage usage;

    /**
     * @param api the gateway client
     */
    public SubscriptionsClient(ApiClient api) {
        this.routes = new RouteRequest(api, SubscriptionRoutes.ROUTES);
        this.plans = new SubscriptionPlans(routes);
        this.customers = new SubscriptionCustomers(routes);
        this.cards = new SubscriptionCards(routes);
        this.invoices = new SubscriptionInvoices(routes);
        this.portal = new SubscriptionPortal(routes);
        this.products = new SubscriptionProducts(routes);
        this.features = new SubscriptionFeatures(routes);
        this.addons = new SubscriptionAddons(routes);
        this.usage = new SubscriptionUsage(routes);
    }

    /**
     * Same as {@link #create(Map, String)} with the key taken from {@code request.idempotencyKey}.
     *
     * @param request the subscription
     * @return the subscription ({@code data})
     */
    public Map<String, Object> create(Map<String, ?> request) {
        return create(request, null);
    }

    /**
     * Creates a subscription ({@code POST /subscriptions}): {@code customerId}, {@code planId}, and optionally
     * {@code paymentCardId}, {@code paymentMethodId}, {@code collectionMethod}, {@code priceUpdatePolicy},
     * {@code entitlementPolicy}, {@code billingTimezone}, {@code startsAt}, {@code chargeOnCreate},
     * {@code billingCycleAnchorOverride}, {@code skipTrial}, {@code metadata}. The {@code Idempotency-Key} is required
     * (the argument or {@code request.idempotencyKey}; both given: they must agree) and is also sent in the body.
     *
     * @param request        the subscription
     * @param idempotencyKey the persisted key of this subscription, or {@code null} to use the request's
     * @return the subscription ({@code data})
     */
    public Map<String, Object> create(Map<String, ?> request, String idempotencyKey) {
        Map<String, Object> body = SubscriptionChecks.body(request, "create request");
        String key = RouteRequest.idempotencyKeyOf("create", body, idempotencyKey);
        if (key != null) {
            body.put("idempotencyKey", key);
        }
        if (body.get("collectionMethod") != null) {
            SubscriptionChecks.oneOf(body.get("collectionMethod"), SubscriptionValues.CollectionMethod.ALL,
                    "collectionMethod");
        }
        SubscriptionChecks.booleanField(body, "chargeOnCreate", "chargeOnCreate");
        SubscriptionChecks.booleanField(body, "skipTrial", "skipTrial");
        if (body.get("metadata") != null) {
            Checks.plainMap(body.get("metadata"), "metadata");
        }
        return RouteRequest.data(routes.send("create", Map.of(), null, body, key));
    }

    /**
     * @param subscriptionId the subscription id
     * @return the subscription ({@code data})
     */
    public Map<String, Object> get(String subscriptionId) {
        return RouteRequest.data(routes.send("get", RouteRequest.params("subscriptionId", subscriptionId), null,
                null, null));
    }

    /** @return the first page of subscriptions */
    public SubscriptionPage list() {
        return list(Map.of());
    }

    /**
     * @param filters {@code customerId}, {@code status}, {@code pageSize}, {@code pageToken}
     * @return one page of subscriptions
     */
    public SubscriptionPage list(Map<String, ?> filters) {
        return new SubscriptionPage(routes.send("list", Map.of(), filters, null, null));
    }

    /**
     * Cancels the subscription now.
     *
     * @param subscriptionId the subscription id
     * @return the subscription ({@code data})
     */
    public Map<String, Object> cancel(String subscriptionId) {
        return cancel(subscriptionId, false, null);
    }

    /**
     * @param subscriptionId the subscription id
     * @param atPeriodEnd    {@code true} to cancel at the end of the current period
     * @return the subscription ({@code data})
     */
    public Map<String, Object> cancel(String subscriptionId, boolean atPeriodEnd) {
        return cancel(subscriptionId, atPeriodEnd, null);
    }

    /**
     * Cancels now, or at the period end ({@code POST /subscriptions/{id}/cancel}).
     *
     * @param subscriptionId the subscription id
     * @param atPeriodEnd    {@code true} to cancel at the end of the current period
     * @param reason         the reason, or {@code null}
     * @return the subscription ({@code data})
     */
    public Map<String, Object> cancel(String subscriptionId, boolean atPeriodEnd, String reason) {
        Map<String, Object> body = RouteRequest.compact("cancelAtPeriodEnd", atPeriodEnd, "reason",
                RouteRequest.optionalText(reason, "reason"));
        return RouteRequest.data(routes.send("cancel", RouteRequest.params("subscriptionId", subscriptionId), null,
                body, null));
    }

    /**
     * Pauses billing until it is resumed.
     *
     * @param subscriptionId the subscription id
     * @return the subscription ({@code data})
     */
    public Map<String, Object> pause(String subscriptionId) {
        return pause(subscriptionId, null);
    }

    /**
     * Pauses billing; {@code resumeAt} (ISO 8601) resumes it automatically.
     *
     * @param subscriptionId the subscription id
     * @param resumeAt       when billing resumes, or {@code null}
     * @return the subscription ({@code data})
     */
    public Map<String, Object> pause(String subscriptionId, String resumeAt) {
        Map<String, Object> body = RouteRequest.compact("resumeAt", RouteRequest.optionalText(resumeAt, "resumeAt"));
        return RouteRequest.data(routes.send("pause", RouteRequest.params("subscriptionId", subscriptionId), null,
                body, null));
    }

    /**
     * @param subscriptionId the subscription id
     * @return the subscription ({@code data})
     */
    public Map<String, Object> resume(String subscriptionId) {
        return RouteRequest.data(routes.send("resume", RouteRequest.params("subscriptionId", subscriptionId), null,
                null, null));
    }

    /**
     * Same as {@link #changePlan(String, Map, String)} with the key taken from {@code request.idempotencyKey}.
     *
     * @param subscriptionId the subscription id
     * @param request        the plan change
     * @return the subscription ({@code data})
     */
    public Map<String, Object> changePlan(String subscriptionId, Map<String, ?> request) {
        return changePlan(subscriptionId, request, null);
    }

    /**
     * Changes the plan: {@code newPlanId}, {@code behavior} ({@code IMMEDIATE} | {@code NEXT_PERIOD}, any case),
     * {@code reason}. An {@code Idempotency-Key} (the argument or {@code request.idempotencyKey}) is sent in the
     * header and the body.
     *
     * @param subscriptionId the subscription id
     * @param request        the plan change
     * @param idempotencyKey the key, or {@code null}
     * @return the subscription ({@code data})
     */
    public Map<String, Object> changePlan(String subscriptionId, Map<String, ?> request, String idempotencyKey) {
        Map<String, Object> body = SubscriptionChecks.body(request, "changePlan request");
        String key = RouteRequest.idempotencyKeyOf("changePlan", body, idempotencyKey);
        if (key != null) {
            body.put("idempotencyKey", key);
        }
        if (body.get("behavior") != null) {
            body.put("behavior", SubscriptionChecks.upperOneOf(body.get("behavior"),
                    SubscriptionValues.PlanChangeBehavior.ALL, "behavior"));
        }
        return RouteRequest.data(routes.send("changePlan", RouteRequest.params("subscriptionId", subscriptionId),
                null, body, key));
    }

    /**
     * @param subscriptionId the subscription id
     * @return the first page of the audit trail
     */
    public SubscriptionPage auditLogs(String subscriptionId) {
        return auditLogs(subscriptionId, Map.of());
    }

    /**
     * The audit trail of a subscription.
     *
     * @param subscriptionId the subscription id
     * @param filters        {@code limit}, {@code pageToken}
     * @return one page of audit entries
     */
    public SubscriptionPage auditLogs(String subscriptionId, Map<String, ?> filters) {
        return new SubscriptionPage(routes.send("auditLogs", RouteRequest.params("subscriptionId", subscriptionId),
                filters, null, null));
    }

    /** @return plans: the templates a subscription is created against */
    public SubscriptionPlans plans() {
        return plans;
    }

    /** @return customers, upserted by your own {@code externalId} */
    public SubscriptionCustomers customers() {
        return customers;
    }

    /** @return saved cards for automatic charges */
    public SubscriptionCards cards() {
        return cards;
    }

    /** @return invoices: billing history, void and charge retry */
    public SubscriptionInvoices invoices() {
        return invoices;
    }

    /** @return the customer portal (embedded SDK sessions and hosted links) */
    public SubscriptionPortal portal() {
        return portal;
    }

    /** @return catalog products (groups of plans) */
    public SubscriptionProducts products() {
        return products;
    }

    /** @return entitlement features */
    public SubscriptionFeatures features() {
        return features;
    }

    /** @return add-ons */
    public SubscriptionAddons addons() {
        return addons;
    }

    /** @return metered usage and entitlement checks */
    public SubscriptionUsage usage() {
        return usage;
    }
}
