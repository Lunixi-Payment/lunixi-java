package com.lunixi.sdk.http;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.lunixi.sdk.exception.ConfigurationException;

/**
 * One row of a route table ({@code WalletRoutes}, {@code SanctionRoutes}, {@code BankTransferRoutes},
 * {@code SubscriptionRoutes}, {@code KycRoutes}, {@code IdentityRoutes}, {@code MarketplaceRoutes}): the HTTP
 * method, the path template ({@code :name} segments are filled from arguments), the {@code Idempotency-Key} rule,
 * whether the call is signed, and the body and query fields it accepts. The clients check every call against its row
 * before anything is sent; the tables are the same as the Node SDK's and the shared catalog's.
 *
 * @param method       {@code GET}, {@code POST}, {@code PUT}, {@code PATCH} or {@code DELETE}
 * @param path         the path template
 * @param keyRule      whether the {@code Idempotency-Key} is required, accepted or refused
 * @param stepUp       the call carries the per-request signature (the wallet signs every route whatever this says)
 * @param screening    a sanction screening: the answer must be {@code status: "success"} to be read
 * @param binary       the {@code 2xx} answer is a file (an image), not the JSON envelope
 * @param free         the body is an object the gateway validates itself (no field list)
 * @param optionalBody an empty body is sent as no body
 * @param anyQuery     any scalar filter is accepted (no query list)
 * @param required     body fields the call cannot do without
 * @param optional     other body fields the gateway accepts
 * @param anyOf        at least one of these body fields must be present (a list: non-empty)
 * @param query        accepted query parameters
 */
public record Route(String method, String path, KeyRule keyRule, boolean stepUp, boolean screening, boolean binary,
                    boolean free, boolean optionalBody, boolean anyQuery, List<String> required,
                    List<String> optional, List<String> anyOf, List<String> query) {

    private static final Pattern PATH_PARAM = Pattern.compile(":([A-Za-z]+)");

    /** The {@code Idempotency-Key} rule of a route; {@link #wire()} is the catalog's spelling. */
    public enum KeyRule {
        /** The call does not take a key. */
        NONE("none"),
        /** A key is accepted; the call also works without one. */
        OPTIONAL("optional"),
        /** The gateway rejects the call without a key. */
        REQUIRED("required");

        private final String wire;

        KeyRule(String wire) {
            this.wire = wire;
        }

        /** @return {@code none}, {@code optional} or {@code required} */
        public String wire() {
            return wire;
        }
    }

    public Route {
        keyRule = keyRule == null ? KeyRule.NONE : keyRule;
        required = List.copyOf(required);
        optional = List.copyOf(optional);
        anyOf = List.copyOf(anyOf);
        query = List.copyOf(query);
    }

    /**
     * @param method the HTTP method
     * @param path   the path template
     * @return a route without a key, signature, body or query fields
     */
    public static Route of(String method, String path) {
        return new Route(method, path, KeyRule.NONE, false, false, false, false, false, false, List.of(), List.of(),
                List.of(), List.of());
    }

    /** @return {@code true} when the gateway rejects the call without an {@code Idempotency-Key} */
    public boolean idempotencyKey() {
        return keyRule == KeyRule.REQUIRED;
    }

    /** @return this route with the {@code Idempotency-Key} required */
    public Route keyed() {
        return withKeyRule(KeyRule.REQUIRED);
    }

    /** @return this route with the {@code Idempotency-Key} accepted but not required */
    public Route optionalKey() {
        return withKeyRule(KeyRule.OPTIONAL);
    }

    /** @return this route signed per request */
    public Route signed() {
        return new Route(method, path, keyRule, true, screening, binary, free, optionalBody, anyQuery, required,
                optional, anyOf, query);
    }

    /** @return this route marked as a screening */
    public Route asScreening() {
        return new Route(method, path, keyRule, stepUp, true, binary, free, optionalBody, anyQuery, required,
                optional, anyOf, query);
    }

    /** @return this route with a binary answer */
    public Route asBinary() {
        return new Route(method, path, keyRule, stepUp, screening, true, free, optionalBody, anyQuery, required,
                optional, anyOf, query);
    }

    /** @return this route with a free-form object body */
    public Route asFree() {
        return new Route(method, path, keyRule, stepUp, screening, binary, true, optionalBody, anyQuery, required,
                optional, anyOf, query);
    }

    /** @return this route sending an empty body as no body */
    public Route withOptionalBody() {
        return new Route(method, path, keyRule, stepUp, screening, binary, free, true, anyQuery, required, optional,
                anyOf, query);
    }

    /** @return this route accepting any scalar filter */
    public Route withAnyQuery() {
        return new Route(method, path, keyRule, stepUp, screening, binary, free, optionalBody, true, required,
                optional, anyOf, query);
    }

    /** @return this route with the given required body fields */
    public Route withRequired(String... fields) {
        return new Route(method, path, keyRule, stepUp, screening, binary, free, optionalBody, anyQuery,
                List.of(fields), optional, anyOf, query);
    }

    /** @return this route with the given optional body fields */
    public Route withOptional(String... fields) {
        return withOptional(List.of(fields));
    }

    /** @return this route with the given optional body fields */
    public Route withOptional(List<String> fields) {
        return new Route(method, path, keyRule, stepUp, screening, binary, free, optionalBody, anyQuery, required,
                fields, anyOf, query);
    }

    /** @return this route with the given "at least one of" body fields */
    public Route withAnyOf(String... fields) {
        return new Route(method, path, keyRule, stepUp, screening, binary, free, optionalBody, anyQuery, required,
                optional, List.of(fields), query);
    }

    /** @return this route with the given query parameters */
    public Route withQuery(List<String> first, String... more) {
        List<String> fields = new ArrayList<>(first);
        fields.addAll(Arrays.asList(more));
        return new Route(method, path, keyRule, stepUp, screening, binary, free, optionalBody, anyQuery, required,
                optional, anyOf, fields);
    }

    /** @return this route with the given query parameters */
    public Route withQuery(String... fields) {
        return withQuery(List.of(fields));
    }

    private Route withKeyRule(KeyRule rule) {
        return new Route(method, path, rule, stepUp, screening, binary, free, optionalBody, anyQuery, required,
                optional, anyOf, query);
    }

    /** @return the required and optional body fields, in table order */
    public Set<String> bodyFields() {
        Set<String> fields = new LinkedHashSet<>(required);
        fields.addAll(optional);
        return fields;
    }

    /** @return {@code true} when the body is checked against a field list */
    public boolean hasFieldList() {
        return !required.isEmpty() || !optional.isEmpty();
    }

    /** @return {@code true} when the call sends a JSON body (not a {@code GET}; body fields listed or free-form) */
    public boolean hasBody() {
        return !"GET".equals(method) && (free || hasFieldList());
    }

    /** @return the {@code :name} parameters of the path template, in order */
    public List<String> pathParameters() {
        List<String> names = new ArrayList<>();
        Matcher matcher = PATH_PARAM.matcher(path);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    /**
     * Fills the path template. Each value is trimmed and URL-encoded as one segment.
     *
     * @param parameters the path parameter values by name
     * @return the path
     * @throws ConfigurationException when a parameter is missing or blank
     */
    public String fill(Map<String, String> parameters) {
        Matcher matcher = PATH_PARAM.matcher(path);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            String value = parameters == null ? null : parameters.get(name);
            if (value == null || value.isBlank()) {
                throw new ConfigurationException(name + " is required.");
            }
            String segment = URLEncoder.encode(value.trim(), StandardCharsets.UTF_8).replace("+", "%20");
            matcher.appendReplacement(out, Matcher.quoteReplacement(segment));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
