package com.lunixi.sdk.paymentlink;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.lunixi.sdk.exception.ConfigurationException;

/**
 * Value checks of the payment-link bodies, made before anything is sent. Only types and value sets are checked;
 * the business rules (title length, amount ranges, which fields a mode allows) are the gateway's. SDK internals.
 */
final class PaymentLinkRules {

    private static final Pattern MINOR = Pattern.compile("0|[1-9][0-9]*");

    private PaymentLinkRules() {
    }

    static String oneOf(Object value, List<String> allowed, String field) {
        if (!(value instanceof String text) || !allowed.contains(text)) {
            throw new ConfigurationException(field + " must be one of " + String.join(", ", allowed) + "; got "
                    + quote(value) + ".");
        }
        return text;
    }

    static String text(Object value, String field) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw new ConfigurationException(field + " must be a non-empty string.");
        }
        return text;
    }

    /** An amount: a canonical minor-unit string, zero included ({@code "150000"} is 1,500.00 TRY). */
    static String minor(Object value, String field) {
        if (!(value instanceof String text) || !MINOR.matcher(text).matches()) {
            throw new ConfigurationException(field + " must be a canonical minor-unit string such as \"150000\"; got "
                    + quote(value) + ".");
        }
        try {
            Long.parseLong(text);
        } catch (NumberFormatException tooLarge) {
            throw new ConfigurationException(field + " exceeds the signed 64-bit minor-unit range.");
        }
        return text;
    }

    static List<String> minorList(Object value, String field) {
        if (!(value instanceof List<?> values)) {
            throw new ConfigurationException(field + " must be a list of canonical minor-unit strings.");
        }
        List<String> out = new ArrayList<>();
        for (Object entry : values) {
            out.add(minor(entry, field + "[]"));
        }
        return List.copyOf(out);
    }

    static String assetIdOrNull(String value, String field) {
        if (value == null) {
            return null;
        }
        if (!PaymentLinkValues.ASSET_ID_PATTERN.matcher(value).matches()) {
            throw new ConfigurationException(field + " must be an asset id (uuid) or null; got " + quote(value) + ".");
        }
        return value;
    }

    static List<String> stringList(Collection<String> values, String field) {
        if (values == null) {
            throw new ConfigurationException(field + " must be a list of strings.");
        }
        List<String> out = new ArrayList<>();
        for (Object value : values) {
            if (!(value instanceof String text)) {
                throw new ConfigurationException(field + " must be a list of strings.");
            }
            out.add(text);
        }
        return List.copyOf(out);
    }

    /** A map of string values with a closed key set ({@code recipient}, {@code prefill}); {@code null} clears it. */
    static Map<String, Object> stringObject(Map<String, String> value, List<String> allowed, String field) {
        if (value == null) {
            return null;
        }
        assertKeys(value.keySet(), allowed, field);
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : value.entrySet()) {
            if (entry.getValue() == null) {
                throw new ConfigurationException(field + "." + entry.getKey() + " must be a string.");
            }
            out.put(entry.getKey(), entry.getValue());
        }
        return out;
    }

    static Map<String, Object> stringRecord(Map<String, String> value, String field) {
        if (value == null) {
            return null;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : value.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                throw new ConfigurationException(field + " must be a map whose keys and values are strings.");
            }
            out.put(entry.getKey(), entry.getValue());
        }
        return out;
    }

    /** The gateway rejects a key its DTO does not declare with 400; name it here instead. */
    static void assertKeys(Collection<?> keys, Collection<String> allowed, String field) {
        List<String> unknown = new ArrayList<>();
        for (Object key : keys) {
            if (key == null || !allowed.contains(key)) {
                unknown.add(String.valueOf(key));
            }
        }
        if (!unknown.isEmpty()) {
            throw new ConfigurationException("Unknown " + field + " field(s): " + String.join(", ", unknown)
                    + ". Allowed: " + String.join(", ", allowed) + ".");
        }
    }

    /** An instant as ISO 8601: an ISO string, {@link Instant}, {@link OffsetDateTime} or {@link ZonedDateTime}. */
    static String isoOrNull(Object value, String field) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant || value instanceof OffsetDateTime) {
            return value.toString();
        }
        if (value instanceof ZonedDateTime zoned) {
            return zoned.toOffsetDateTime().toString();
        }
        return text(value, field);
    }

    /** A query value: lists as a comma list, {@code java.time} values as ISO 8601, scalars as text. */
    static String queryValue(String key, Object value) {
        if (value instanceof Collection<?> values) {
            List<String> parts = new ArrayList<>();
            for (Object entry : values) {
                parts.add(queryValue(key, entry));
            }
            return String.join(",", parts);
        }
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof Instant || value instanceof OffsetDateTime || value instanceof ZonedDateTime) {
            return isoOrNull(value, key);
        }
        throw new ConfigurationException(key + " must be a string, number, boolean, list or java.time value; got "
                + value.getClass().getSimpleName() + ".");
    }

    static String idempotencyKey(String operation, String key) {
        String value = key == null ? "" : key.trim();
        if (value.isEmpty()) {
            throw new ConfigurationException("A stable Idempotency-Key is required for " + operation
                    + ". Reuse the same key when retrying the same operation.");
        }
        return idempotencyKeyFormat(value);
    }

    static String optionalIdempotencyKey(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        return idempotencyKeyFormat(key.trim());
    }

    private static String idempotencyKeyFormat(String value) {
        if (!PaymentLinkValues.IDEMPOTENCY_KEY_PATTERN.matcher(value).matches()) {
            throw new ConfigurationException("Idempotency-Key must be 8-200 printable ASCII characters without "
                    + "spaces.");
        }
        return value;
    }

    static String quote(Object value) {
        return value instanceof String text ? "\"" + text + "\"" : String.valueOf(value);
    }
}
