package com.lunixi.sdk.wallet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.lunixi.sdk.exception.ConfigurationException;
import com.lunixi.sdk.http.Route;
import com.lunixi.sdk.internal.ApiClient;
import com.lunixi.sdk.internal.Checks;
import com.lunixi.sdk.internal.RequestOptions;

/**
 * Sends one route of {@link WalletRoutes}, signed. The body and query are checked against the route before anything
 * leaves the process: an unknown field, a missing required field, an amount that is not a minor-unit digit string or
 * a missing {@code Idempotency-Key} is a {@link ConfigurationException}, not a 400 from the gateway.
 */
final class WalletInvoker {

    private static final Pattern CONTROL = Pattern.compile("[\\x00-\\x1f\\x7f]");
    private static final String IMAGE_ACCEPT = "image/svg+xml, image/png, application/json";

    private final ApiClient api;

    WalletInvoker(ApiClient api) {
        this.api = api;
    }

    /** A route without body, query or key. */
    Map<String, Object> call(String routeName, Map<String, String> path) {
        return call(routeName, path, null, null, null);
    }

    /**
     * @param routeName      the route name in {@link WalletRoutes#ROUTES}
     * @param path           the path parameters
     * @param query          the query filters, or {@code null}
     * @param body           the body map, or {@code null}
     * @param idempotencyKey the key, or {@code null}
     * @return the decoded envelope
     */
    Map<String, Object> call(String routeName, Map<String, String> path, Map<String, ?> query, Map<String, ?> body,
                             String idempotencyKey) {
        Route route = WalletRoutes.route(routeName);
        String filled = route.fill(path);
        RequestOptions options = options(routeName, route, query, idempotencyKey);
        Object payload = route.hasBody() ? checkBody(routeName, route, body == null ? Map.of() : body) : null;
        return api.request(route.method(), filled, payload, options);
    }

    /** A binary route ({@link Route#binary()}): the answer's bytes. */
    ApiClient.Binary binary(String routeName, Map<String, String> path, Map<String, ?> query) {
        Route route = WalletRoutes.route(routeName);
        String filled = route.fill(path);
        return api.requestBinary(route.method(), filled, IMAGE_ACCEPT, options(routeName, route, query, null));
    }

    private static RequestOptions options(String routeName, Route route, Map<String, ?> query,
                                          String idempotencyKey) {
        RequestOptions.Builder options = RequestOptions.builder().stepUp(true);
        if (query != null) {
            pickQuery(routeName, route, query).forEach(options::query);
        }
        if (route.idempotencyKey()) {
            options.idempotencyKey(requireIdempotencyKey(routeName, idempotencyKey));
        } else if (idempotencyKey != null) {
            throw new ConfigurationException(routeName + " does not take an Idempotency-Key.");
        }
        return options.build();
    }

    static Map<String, Object> checkBody(String routeName, Route route, Map<String, ?> body) {
        Map<String, Object> plain = Checks.plainMap(body, routeName + " body");
        List<String> unknown = new ArrayList<>();
        for (String key : plain.keySet()) {
            if (!route.bodyFields().contains(key)) {
                unknown.add(key);
            }
        }
        if (!unknown.isEmpty()) {
            throw new ConfigurationException(routeName + " does not accept " + String.join(", ", unknown)
                    + " (the gateway rejects unknown fields).");
        }
        List<String> missing = new ArrayList<>();
        for (String key : route.required()) {
            if (!Checks.present(plain.get(key))) {
                missing.add(key);
            }
        }
        if (!missing.isEmpty()) {
            throw new ConfigurationException(routeName + " requires " + String.join(", ", missing) + ".");
        }
        for (String field : WalletRoutes.AMOUNT_FIELDS) {
            if (plain.containsKey(field)) {
                plain.put(field, requireAmount(routeName + "." + field, plain.get(field)));
            }
        }
        WalletRoutes.NestedFields nested = WalletRoutes.NESTED_FIELDS.get(routeName);
        if (nested != null) {
            plain.put(nested.field(), checkRows(routeName + "." + nested.field(), nested, plain.get(nested.field())));
        }
        return plain;
    }

    private static List<Map<String, Object>> checkRows(String label, WalletRoutes.NestedFields spec, Object rows) {
        if (!(rows instanceof Collection<?> list) || list.isEmpty()) {
            throw new ConfigurationException(label + " must be a non-empty list.");
        }
        List<Map<String, Object>> out = new ArrayList<>();
        int index = 0;
        for (Object row : list) {
            String rowLabel = label + "[" + index++ + "]";
            Map<String, Object> plain = Checks.plainMap(row, rowLabel);
            List<String> unknown = new ArrayList<>();
            for (String key : plain.keySet()) {
                if (!spec.required().contains(key) && !spec.optional().contains(key)) {
                    unknown.add(key);
                }
            }
            if (!unknown.isEmpty()) {
                throw new ConfigurationException(rowLabel + " does not accept " + String.join(", ", unknown) + ".");
            }
            List<String> missing = new ArrayList<>();
            for (String key : spec.required()) {
                if (!Checks.present(plain.get(key))) {
                    missing.add(key);
                }
            }
            if (!missing.isEmpty()) {
                throw new ConfigurationException(rowLabel + " requires " + String.join(", ", missing) + ".");
            }
            if (plain.containsKey("amount")) {
                plain.put("amount", requireAmount(rowLabel + ".amount", plain.get("amount")));
            }
            out.add(plain);
        }
        return out;
    }

    private static Map<String, String> pickQuery(String routeName, Route route, Map<String, ?> filters) {
        List<String> unknown = new ArrayList<>();
        for (Map.Entry<String, ?> entry : filters.entrySet()) {
            if (!route.query().contains(entry.getKey()) && entry.getValue() != null) {
                unknown.add(entry.getKey());
            }
        }
        if (!unknown.isEmpty()) {
            throw new ConfigurationException(routeName + " does not accept the filter(s) "
                    + String.join(", ", unknown) + ".");
        }
        Map<String, String> query = new LinkedHashMap<>();
        for (String key : route.query()) {
            Object value = filters.get(key);
            if (value instanceof Collection<?> list) {
                List<String> parts = new ArrayList<>();
                list.forEach(part -> parts.add(String.valueOf(part)));
                value = parts.isEmpty() ? null : String.join(",", parts);
            }
            if (value != null && !"".equals(value)) {
                query.put(key, String.valueOf(value));
            }
        }
        return query;
    }

    /** Minor units only: a canonical positive digit string of at most 18 digits; a JSON number is refused. */
    private static String requireAmount(String label, Object value) {
        String text;
        try {
            text = Checks.minor(value, label, true);
        } catch (ConfigurationException notMinor) {
            text = null;
        }
        if (text == null || !WalletRoutes.AMOUNT_PATTERN.matcher(text).matches()) {
            throw new ConfigurationException(label + " must be minor units as a digit string (e.g. \"12550\" for "
                    + "125.50).");
        }
        return text;
    }

    private static String requireIdempotencyKey(String routeName, String key) {
        String value = key == null ? "" : key.trim();
        if (value.isEmpty()) {
            throw new ConfigurationException("A stable Idempotency-Key is required for " + routeName
                    + ". Reuse the same key when retrying the same operation.");
        }
        if (value.length() > WalletRoutes.IDEMPOTENCY_KEY_MAX || CONTROL.matcher(value).find()) {
            throw new ConfigurationException("Idempotency-Key must be 1-" + WalletRoutes.IDEMPOTENCY_KEY_MAX
                    + " characters without control characters.");
        }
        return value;
    }

    /** The complete DTOs require {@code operationId} in the body as well; the path wins on the server. */
    static Map<String, Object> withOperationId(String operationId, Map<String, ?> request) {
        Map<String, Object> body = request == null ? new LinkedHashMap<>() : Checks.plainMap(request, "request");
        Object given = body.get("operationId");
        if (given != null && !given.equals(operationId)) {
            throw new ConfigurationException("operationId in the body must match the operation being completed.");
        }
        body.put("operationId", operationId);
        return body;
    }

    /** A map of the non-null values, in the given order (pairs of name and value). */
    static Map<String, Object> compact(Object... pairs) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            if (pairs[i + 1] != null) {
                out.put((String) pairs[i], pairs[i + 1]);
            }
        }
        return out;
    }

    /** One path parameter. */
    static Map<String, String> path(String name, String value) {
        Map<String, String> out = new LinkedHashMap<>();
        out.put(name, value);
        return out;
    }
}
