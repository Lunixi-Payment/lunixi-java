package com.lunixi.sdk.subscription;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.lunixi.sdk.exception.ConfigurationException;
import com.lunixi.sdk.internal.Checks;
import com.lunixi.sdk.internal.RouteRequest;
import com.lunixi.sdk.payment.Money;

/** Value checks of the subscription bodies, made before sending. SDK internals. */
final class SubscriptionChecks {

    /** The gateway currencies, for the error message. */
    private static final String CURRENCIES = "JPY, KWD, TRY, USD, EUR, GBP, RUB, AED, SAR, QAR, CHF, CAD, AUD, CNY, AZN";

    private SubscriptionChecks() {
    }

    /** @return the body as a mutable map; a {@code null} body is refused */
    static Map<String, Object> body(Map<String, ?> value, String label) {
        if (value == null) {
            throw new ConfigurationException(label + " must be an object (a map).");
        }
        return Checks.plainMap(value, label);
    }

    static void booleanField(Map<String, Object> body, String field, String label) {
        Object value = body.get(field);
        if (value != null && !(value instanceof Boolean)) {
            throw new ConfigurationException(label + " must be a boolean.");
        }
    }

    static void countField(Map<String, Object> body, String field, long min, String label) {
        Object value = body.get(field);
        if (value == null) {
            return;
        }
        if (!RouteRequest.isInteger(value) || ((Number) value).longValue() < min) {
            throw new ConfigurationException(label + " must be an integer of at least " + min + ".");
        }
    }

    static List<Object> integerList(Object values, String field, long min, long max) {
        boolean ok = values instanceof Collection<?>;
        if (ok) {
            for (Object value : (Collection<?>) values) {
                if (!RouteRequest.isInteger(value) || ((Number) value).longValue() < min
                        || ((Number) value).longValue() > max) {
                    ok = false;
                    break;
                }
            }
        }
        if (!ok) {
            throw new ConfigurationException(field + " must be a list of integers from " + min
                    + (max == Long.MAX_VALUE ? "" : " to " + max) + ".");
        }
        return new ArrayList<>((Collection<?>) values);
    }

    static String oneOf(Object value, List<String> allowed, String field) {
        if (!(value instanceof String text) || !allowed.contains(text)) {
            throw new ConfigurationException(field + " must be one of " + String.join(", ", allowed) + ".");
        }
        return text;
    }

    static String upperOneOf(Object value, List<String> allowed, String field) {
        return oneOf(value == null ? "" : String.valueOf(value).toUpperCase(Locale.ROOT), allowed, field);
    }

    /** @return the upper-cased code of a gateway currency */
    static String supportedCurrency(Object value, String field) {
        String code = value instanceof String text ? text.trim().toUpperCase(Locale.ROOT) : "";
        try {
            Money.exponent(code);
        } catch (IllegalArgumentException unsupported) {
            throw new ConfigurationException(field + " must be a supported currency (" + CURRENCIES + ").");
        }
        return code;
    }

    /** A plan's billing rule: known fields, an upper-cased frequency, {@code interval} defaulting to 1. */
    static Map<String, Object> billingRule(Object value) {
        Map<String, Object> rule = Checks.plainMap(value, "billing");
        List<String> unknown = new ArrayList<>();
        for (String key : rule.keySet()) {
            if (!SubscriptionRoutes.BILLING_RULE_FIELDS.contains(key)) {
                unknown.add(key);
            }
        }
        if (!unknown.isEmpty()) {
            throw new ConfigurationException("billing does not accept " + String.join(", ", unknown) + ".");
        }
        rule.put("frequency", upperOneOf(rule.get("frequency"), SubscriptionValues.BillingFrequency.ALL,
                "billing.frequency"));
        rule.putIfAbsent("interval", 1);
        countField(rule, "interval", 1, "billing.interval");
        countField(rule, "count", 0, "billing.count");
        if (rule.containsKey("byMonthDay")) {
            rule.put("byMonthDay", integerList(rule.get("byMonthDay"), "billing.byMonthDay", 1, 31));
        }
        if (rule.containsKey("byWeekDay")) {
            Object days = rule.get("byWeekDay");
            boolean ok = days instanceof Collection<?> list && list.stream().allMatch(day -> day instanceof String);
            if (!ok) {
                throw new ConfigurationException("billing.byWeekDay must be a list of week days (MO, TU, WE, TH, FR, "
                        + "SA, SU).");
            }
        }
        booleanField(rule, "lastDayOfMonthFix", "billing.lastDayOfMonthFix");
        return rule;
    }
}
