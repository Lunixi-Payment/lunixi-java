package com.lunixi.sdk.subscription;

import java.util.Map;

import com.lunixi.sdk.exception.ConfigurationException;
import com.lunixi.sdk.internal.Checks;
import com.lunixi.sdk.internal.RouteRequest;

/** Plans ({@code /api/v1/subscriptions/plans}): the templates a subscription is created against. */
public final class SubscriptionPlans {

    private final RouteRequest routes;

    SubscriptionPlans(RouteRequest routes) {
        this.routes = routes;
    }

    /**
     * Same as {@link #create(Map, String)} without an {@code Idempotency-Key}.
     *
     * @param request the plan
     * @return the plan ({@code data})
     */
    public Map<String, Object> create(Map<String, ?> request) {
        return create(request, null);
    }

    /**
     * Creates a plan: {@code productId}, {@code code}, {@code name}, {@code price}, {@code currency},
     * {@code billing}, and optionally {@code description}, {@code trialPeriodDays}, {@code maxDunningAttempts},
     * {@code dunningScheduleDays}, {@code gracePeriodDays}, {@code metadata}. {@code price} is a minor-unit string
     * ({@code "0"} for a free plan); {@code billing} is {@code {frequency, interval?, byMonthDay?, byWeekDay?,
     * timezone?, lastDayOfMonthFix?, count?}} ({@code frequency} in any case, {@code interval} defaults to 1).
     *
     * @param request        the plan
     * @param idempotencyKey the key, or {@code null}
     * @return the plan ({@code data})
     */
    public Map<String, Object> create(Map<String, ?> request, String idempotencyKey) {
        Map<String, Object> body = SubscriptionChecks.body(request, "plans.create request");
        for (String field : new String[] {"productId", "code", "name"}) {
            if (body.get(field) != null) {
                body.put(field, Checks.text(body.get(field), field));
            }
        }
        if (body.get("price") == null) {
            throw new ConfigurationException("plans.create requires price.");
        }
        if (body.get("currency") != null) {
            body.put("currency", SubscriptionChecks.supportedCurrency(body.get("currency"), "currency"));
        }
        if (body.get("billing") != null) {
            body.put("billing", SubscriptionChecks.billingRule(body.get("billing")));
        }
        for (String field : new String[] {"trialPeriodDays", "maxDunningAttempts", "gracePeriodDays"}) {
            SubscriptionChecks.countField(body, field, 0, field);
        }
        if (body.get("dunningScheduleDays") != null) {
            body.put("dunningScheduleDays", SubscriptionChecks.integerList(body.get("dunningScheduleDays"),
                    "dunningScheduleDays", 0, Long.MAX_VALUE));
        }
        return RouteRequest.data(routes.send("plans.create", Map.of(), null, body, idempotencyKey));
    }

    /**
     * @param planId the plan id
     * @return the plan ({@code data})
     */
    public Map<String, Object> get(String planId) {
        return RouteRequest.data(routes.send("plans.get", RouteRequest.params("planId", planId), null, null, null));
    }

    /** @return the first page of plans */
    public SubscriptionPage list() {
        return list(Map.of());
    }

    /**
     * @param filters {@code productId}, {@code includeArchived}, {@code pageSize}, {@code pageToken}
     * @return one page of plans
     */
    public SubscriptionPage list(Map<String, ?> filters) {
        return new SubscriptionPage(routes.send("plans.list", Map.of(), filters, null, null));
    }

    /**
     * Price, billing, metadata…; a {@code price} is a minor-unit string.
     *
     * @param planId  the plan id
     * @param changes the changes
     * @return the plan ({@code data})
     */
    public Map<String, Object> update(String planId, Map<String, ?> changes) {
        return RouteRequest.data(routes.send("plans.update", RouteRequest.params("planId", planId), null,
                SubscriptionChecks.body(changes, "plans.update body"), null));
    }

    /**
     * @param planId the plan id
     * @return the answer ({@code data})
     */
    public Map<String, Object> archive(String planId) {
        return RouteRequest.data(routes.send("plans.archive", RouteRequest.params("planId", planId), null, null,
                null));
    }

    /**
     * @param planId the plan id
     * @return the plan's translations ({@code data})
     */
    public Map<String, Object> translations(String planId) {
        return RouteRequest.data(routes.send("plans.translations", RouteRequest.params("planId", planId), null,
                null, null));
    }

    /**
     * @param planId      the plan id
     * @param locale      the locale ({@code en}, {@code tr} …)
     * @param translation the translated fields
     * @return the translation ({@code data})
     */
    public Map<String, Object> upsertTranslation(String planId, String locale, Map<String, ?> translation) {
        return RouteRequest.data(routes.send("plans.upsertTranslation",
                RouteRequest.params("planId", planId, "locale", locale), null,
                SubscriptionChecks.body(translation, "plans.upsertTranslation body"), null));
    }
}
