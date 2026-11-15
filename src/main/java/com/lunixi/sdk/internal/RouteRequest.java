package com.lunixi.sdk.internal;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.lunixi.sdk.exception.ConfigurationException;
import com.lunixi.sdk.exception.LunixiApiException;
import com.lunixi.sdk.http.Envelope;
import com.lunixi.sdk.http.Route;

/**
 * Request plumbing shared by the route-table clients (subscriptions, KYC, identity, marketplace), the same as the
 * Node SDK's {@code route-request.js}. Each product describes its routes in one table of {@link Route} rows; a call
 * names its row and passes the path parameters, the filters, the body and the key:
 *
 * <ul>
 *   <li>the path parameters fill the template (missing or blank: refused);</li>
 *   <li>the {@code Idempotency-Key} follows the row's {@link Route.KeyRule} (missing where required, given where
 *       refused, or blank: refused);</li>
 *   <li>filters outside the row's query list are refused (unless {@link Route#anyQuery()}); values must be strings,
 *       numbers or booleans, booleans are sent as {@code true} / {@code false}, empty strings are left out;</li>
 *   <li>a body with a field list is checked field by field (unknown or missing: refused), a free body must be an
 *       object, a row without either takes no body; with {@link Route#optionalBody()} an empty body is sent as no
 *       body;</li>
 *   <li>on the subscription and marketplace contracts ({@code /api/v1/subscriptions/*}, {@code /api/v1/marketplace/*})
 *       every money member of the body, the filters and the answer is a canonical minor-unit string
 *       ({@link #WIRE_MONEY_FIELDS}, signed only for balances).</li>
 * </ul>
 *
 * <p>Every check runs before anything leaves the process and fails with a {@link ConfigurationException} that names
 * the field, never its value (card data and document images included). A malformed money member in an answer is a
 * {@link LunixiApiException}.
 *
 * <p>SDK internals; this type's signature may change in a minor release.
 */
public final class RouteRequest {

    /** Money members of the subscription and marketplace contracts. */
    public static final Set<String> WIRE_MONEY_FIELDS = Set.of(
            "amount", "amountMinor", "price", "priceAmount", "paidAmount", "totalAmount", "subtotalAmount",
            "taxAmount", "amountPaid", "legAmount", "chargeAmount", "capturedAmount", "refundedAmount",
            "authorizedAmount", "compensatedAmount", "requestedAmount", "refundableAmount", "nonRefundableAmount",
            "disputedAmount", "legsAmount", "minAmount", "maxAmount", "minAmountMinor", "maxAmountMinor",
            "unitPriceMinor", "collectedAmountMinor", "premiumAmount", "collectedPremiumAmount",
            "inForcePremiumAmount", "cancelledPremiumAmount", "refundedPremiumAmount", "refundOpenPremiumAmount",
            "insuranceValueAmount", "priceSnapshotAmount", "sourceAmountMinor", "targetAmountMinor",
            "taxAmountMinor", "balanceMinor", "balanceAfterMinor", "availableMinor", "blockedMinor",
            "availableWithoutOverdraftMinor", "overdraftAvailableMinor", "sourceBalanceAfterMinor",
            "targetBalanceAfterMinor", "balance", "availableBalance", "blockedBalance", "feeAmount", "feeAmountMinor",
            "fixedAmountMinor", "minTransactionMinor", "commissionAmountMinor", "payoutAmountMinor",
            "settlementAmountMinor", "netAmountMinor", "grossAmountMinor", "fixedAmount", "fixedFee", "fixedFeeMinor",
            "mtdVolumeMinor", "orderAmountMinor", "capturedAmountMinor", "cumulativeCapturedMinor",
            "remainingCapturableMinor", "refundAmountMinor", "cumulativeRefundedMinor", "remainingRefundableMinor",
            "netAmount", "grossAmount");

    /** Closed schema containers searched for money members; metadata, custom fields and provider bodies are opaque. */
    public static final Set<String> WIRE_MONEY_CONTAINERS = Set.of(
            "data", "items", "legs", "steps", "pendingSteps", "schedule", "promissoryNote", "notes", "aggregate",
            "refund", "dispute", "details", "payment", "basketItems", "sellers", "scenario", "counters", "balances",
            "totals", "orders", "policies", "options", "quote", "order", "transfer", "commission", "commissionRule",
            "fee", "settlement", "payout", "context");

    private static final Pattern MONEY_CONTRACT = Pattern.compile("^/api/v1/(?:subscriptions|marketplace)(?:/.*)?$");
    private static final Pattern NON_NEGATIVE = Pattern.compile("^(0|[1-9][0-9]*)$");
    private static final Pattern SIGNED = Pattern.compile("^(0|-?[1-9][0-9]*)$");
    private static final BigInteger MAX = BigInteger.valueOf(Long.MAX_VALUE);
    private static final BigInteger MIN = BigInteger.valueOf(Long.MIN_VALUE);

    private final ApiClient api;
    private final Map<String, Route> routes;

    /**
     * @param api    the gateway client
     * @param routes the product's route table
     */
    public RouteRequest(ApiClient api, Map<String, Route> routes) {
        this.api = api;
        this.routes = routes;
    }

    /** A route without path parameters, filters, body or key. */
    public Map<String, Object> send(String name) {
        return send(name, Map.of(), null, null, null);
    }

    /**
     * Checks and sends one route.
     *
     * @param name           the route name in the table
     * @param params         the path parameters
     * @param query          the filters, or {@code null}
     * @param body           the body, or {@code null}
     * @param idempotencyKey the key, or {@code null}
     * @return the decoded envelope
     */
    public Map<String, Object> send(String name, Map<String, String> params, Map<String, ?> query, Map<String, ?> body,
                                    String idempotencyKey) {
        return exchange(name, params, query, body, idempotencyKey).envelope();
    }

    /**
     * Same as {@link #send(String, Map, Map, Map, String)}, but also returns the HTTP status of the {@code 2xx}
     * answer.
     *
     * @param name           the route name in the table
     * @param params         the path parameters
     * @param query          the filters, or {@code null}
     * @param body           the body, or {@code null}
     * @param idempotencyKey the key, or {@code null}
     * @return the HTTP status and the decoded envelope
     */
    public ApiClient.Exchange exchange(String name, Map<String, String> params, Map<String, ?> query,
                                       Map<String, ?> body, String idempotencyKey) {
        Route route = routes.get(name);
        if (route == null) {
            throw new IllegalArgumentException("Unknown route " + name + ".");
        }
        String path = route.fill(params);
        String key = checkIdempotencyKey(name, route.keyRule(), idempotencyKey);
        Map<String, Object> filters = query == null ? null : pickQuery(name, route, query);
        Map<String, Object> payload = buildBody(name, route, body);
        boolean money = MONEY_CONTRACT.matcher(route.path()).matches();
        if (money) {
            checkWireMoney(payload);
            checkWireMoney(filters);
        }
        RequestOptions.Builder options = RequestOptions.builder().stepUp(route.stepUp());
        if (filters != null) {
            filters.forEach((field, value) -> options.query(field, String.valueOf(value)));
        }
        if (key != null) {
            options.idempotencyKey(key);
        }
        ApiClient.Exchange exchange = api.exchange(route.method(), path, payload, options.build());
        Map<String, Object> envelope = exchange.envelope();
        if (money) {
            try {
                checkWireMoney(envelope);
            } catch (ConfigurationException malformed) {
                throw new LunixiApiException("The answer of " + name + " carries a malformed amount: "
                        + malformed.getMessage(), exchange.statusCode(), "sdk.answer.invalid_money", envelope,
                        Envelope.requestId(envelope).orElse(null));
            }
        }
        return exchange;
    }

    private static Map<String, Object> buildBody(String name, Route route, Map<String, ?> body) {
        if (route.hasFieldList() || route.free()) {
            Map<String, Object> plain = Checks.plainMap(body == null ? Map.of() : body, name + " body");
            if (route.hasFieldList()) {
                fields(name, plain, route.required(), route.bodyFields());
            }
            return route.optionalBody() && plain.isEmpty() ? null : plain;
        }
        if (body != null && !body.isEmpty()) {
            throw new ConfigurationException(name + " takes no body.");
        }
        return null;
    }

    private static void fields(String label, Map<String, Object> plain, List<String> required,
                               Collection<String> allowed) {
        List<String> unknown = new ArrayList<>();
        for (String field : plain.keySet()) {
            if (!allowed.contains(field)) {
                unknown.add(field);
            }
        }
        if (!unknown.isEmpty()) {
            throw new ConfigurationException(label + " does not accept " + String.join(", ", unknown) + ".");
        }
        List<String> missing = new ArrayList<>();
        for (String field : required) {
            if (!Checks.presentNonEmpty(plain.get(field))) {
                missing.add(field);
            }
        }
        if (!missing.isEmpty()) {
            throw new ConfigurationException(label + " requires " + String.join(", ", missing) + ".");
        }
    }

    private static Map<String, Object> pickQuery(String name, Route route, Map<String, ?> filters) {
        Map<String, Object> given = Checks.plainMap(filters, name + " filters");
        if (!route.anyQuery()) {
            List<String> unknown = new ArrayList<>();
            for (String field : given.keySet()) {
                if (!route.query().contains(field)) {
                    unknown.add(field);
                }
            }
            if (!unknown.isEmpty()) {
                throw new ConfigurationException(name + " does not accept the filter(s) " + String.join(", ", unknown)
                        + (route.query().isEmpty() ? "" : "; it takes " + String.join(", ", route.query())) + ".");
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : given.entrySet()) {
            Object value = entry.getValue();
            if ("".equals(value)) {
                continue;
            }
            if (!(value instanceof String || value instanceof Number || value instanceof Boolean)) {
                throw new ConfigurationException(name + " filter " + entry.getKey()
                        + " must be a string, number or boolean.");
            }
            out.put(entry.getKey(), value);
        }
        return out;
    }

    private static String checkIdempotencyKey(String name, Route.KeyRule rule, String key) {
        if (key == null) {
            if (rule == Route.KeyRule.REQUIRED) {
                throw new ConfigurationException("A stable Idempotency-Key is required for " + name
                        + ". Reuse the same key when retrying the same operation.");
            }
            return null;
        }
        if (rule == Route.KeyRule.NONE) {
            throw new ConfigurationException(name + " does not take an Idempotency-Key.");
        }
        if (key.isBlank()) {
            throw new ConfigurationException("The Idempotency-Key of " + name + " must be a non-empty string.");
        }
        return key.trim();
    }

    /**
     * A request's own {@code idempotencyKey} field or the argument; when both are given they must agree.
     *
     * @param name    the operation, for the error message
     * @param request the request body (may be {@code null})
     * @param key     the argument (may be {@code null})
     * @return the key, or {@code null}
     */
    public static String idempotencyKeyOf(String name, Map<String, ?> request, String key) {
        String own = request != null && request.get("idempotencyKey") instanceof String text && !text.isBlank()
                ? text.trim() : null;
        String given = key != null && !key.isBlank() ? key.trim() : null;
        if (own != null && given != null && !own.equals(given)) {
            throw new ConfigurationException(name + ": the request's idempotencyKey and the idempotencyKey "
                    + "argument differ.");
        }
        return given != null ? given : own;
    }

    /**
     * Every listed money member of a body, a filter map or an answer is a canonical minor-unit string; balances may
     * be negative. Values are never converted.
     *
     * @param value a map, a list or anything else (ignored)
     * @throws ConfigurationException naming the field
     */
    public static void checkWireMoney(Object value) {
        if (value instanceof Collection<?> list) {
            list.forEach(RouteRequest::checkWireMoney);
            return;
        }
        if (!(value instanceof Map<?, ?> map)) {
            return;
        }
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key) || entry.getValue() == null) {
                continue;
            }
            Object member = entry.getValue();
            if (WIRE_MONEY_FIELDS.contains(key)) {
                boolean signed = key.toLowerCase(java.util.Locale.ROOT).contains("balance")
                        || "availableMinor".equals(key) || "availableWithoutOverdraftMinor".equals(key);
                wireMinor(member, key, signed);
            } else if ("suggestedAmountsMinor".equals(key)) {
                if (!(member instanceof Collection<?> amounts)) {
                    throw new ConfigurationException("suggestedAmountsMinor must be a list.");
                }
                for (Object amount : amounts) {
                    Checks.minor(amount, key, true);
                }
            } else if (WIRE_MONEY_CONTAINERS.contains(key)) {
                checkWireMoney(member);
            }
        }
    }

    private static void wireMinor(Object value, String field, boolean signed) {
        boolean ok = value instanceof String text && (signed ? SIGNED : NON_NEGATIVE).matcher(text).matches();
        if (ok) {
            BigInteger exact = new BigInteger((String) value);
            ok = exact.compareTo(MAX) <= 0 && exact.compareTo(MIN) >= 0;
        }
        if (!ok) {
            throw new ConfigurationException(field + " must be a canonical string in minor units.");
        }
    }

    /**
     * The envelope's {@code data} when it is an object; otherwise the envelope itself (an answer without
     * {@code data}, or whose {@code data} is a list).
     *
     * @param response the decoded envelope
     * @return the entity
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> data(Map<String, Object> response) {
        if (response != null && response.get("data") instanceof Map<?, ?> data) {
            return (Map<String, Object>) data;
        }
        return response == null ? Map.of() : response;
    }

    /**
     * Answers that put their fields at the envelope root and/or under {@code data}: both, merged ({@code data}
     * wins).
     *
     * @param response the decoded envelope
     * @return the merged fields
     */
    public static Map<String, Object> mergeRoot(Map<String, Object> response) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (response == null) {
            return out;
        }
        response.forEach((field, value) -> {
            if (!"data".equals(field)) {
                out.put(field, value);
            }
        });
        out.putAll(Envelope.data(response));
        return out;
    }

    /** @return {@code true} for an {@link Integer} or a {@link Long} */
    public static boolean isInteger(Object value) {
        return value instanceof Integer || value instanceof Long;
    }

    /** @return the trimmed text, or {@code null} when the value is {@code null} or empty */
    public static String optionalText(Object value, String field) {
        if (value == null || "".equals(value)) {
            return null;
        }
        if (!(value instanceof String text)) {
            throw new ConfigurationException(field + " must be a string.");
        }
        return text;
    }

    /** A map of the non-null values, in the given order (pairs of name and value). */
    public static Map<String, Object> compact(Object... pairs) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            if (pairs[i + 1] != null) {
                out.put((String) pairs[i], pairs[i + 1]);
            }
        }
        return out;
    }

    /** Path parameters (pairs of name and value). */
    public static Map<String, String> params(String... pairs) {
        Map<String, String> out = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            out.put(pairs[i], pairs[i + 1]);
        }
        return out;
    }
}
