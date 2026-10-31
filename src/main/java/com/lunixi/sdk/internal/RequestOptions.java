package com.lunixi.sdk.internal;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-call request options.
 *
 * <p>SDK internals; a feature client builds these, callers do not.
 */
public final class RequestOptions {

    private static final RequestOptions NONE = new RequestOptions(false, null, Map.of());

    private final boolean stepUp;
    private final String idempotencyKey;
    private final Map<String, String> query;

    private RequestOptions(boolean stepUp, String idempotencyKey, Map<String, String> query) {
        this.stepUp = stepUp;
        this.idempotencyKey = idempotencyKey;
        this.query = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(query));
    }

    /** @return options for a plain bearer-authenticated call with no query string */
    public static RequestOptions none() {
        return NONE;
    }

    /** @return a new builder */
    public static Builder builder() {
        return new Builder();
    }

    /** Whether the call carries the per-request Ed25519 signature. */
    public boolean stepUp() {
        return stepUp;
    }

    /** The {@code Idempotency-Key} header value, or {@code null}. */
    public String idempotencyKey() {
        return idempotencyKey;
    }

    /** The query parameters, in insertion order. */
    public Map<String, String> query() {
        return query;
    }

    /** Builder for {@link RequestOptions}. */
    public static final class Builder {

        private boolean stepUp;
        private String idempotencyKey;
        private final Map<String, String> query = new LinkedHashMap<>();

        private Builder() {
        }

        /**
         * @param stepUp {@code true} for a route that is authenticated by the
         *               per-request signature rather than by the bearer token alone
         * @return this builder
         */
        public Builder stepUp(boolean stepUp) {
            this.stepUp = stepUp;
            return this;
        }

        /**
         * @param idempotencyKey the idempotency key, or {@code null}. Setting one
         *                       also makes the call eligible for an automatic retry.
         * @return this builder
         */
        public Builder idempotencyKey(String idempotencyKey) {
            this.idempotencyKey = idempotencyKey == null || idempotencyKey.isBlank() ? null : idempotencyKey;
            return this;
        }

        /**
         * Adds a query parameter, skipping a null or empty value.
         *
         * @param name  the parameter name
         * @param value the value
         * @return this builder
         */
        public Builder query(String name, Object value) {
            if (value != null) {
                String text = String.valueOf(value);
                if (!text.isEmpty()) {
                    query.put(name, text);
                }
            }
            return this;
        }

        /** @return the options */
        public RequestOptions build() {
            return new RequestOptions(stepUp, idempotencyKey, Map.copyOf(query).isEmpty()
                    ? Map.of()
                    : new LinkedHashMap<>(query));
        }
    }
}
