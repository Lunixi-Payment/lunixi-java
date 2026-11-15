package com.lunixi.sdk.internal;

import java.math.BigInteger;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import com.lunixi.sdk.exception.ConfigurationException;

/**
 * Value checks shared by the feature clients that validate a call before sending it (wallet, bank accounts,
 * sanction screening, insurance). Every failure is a {@link ConfigurationException}; nothing is sent.
 *
 * <p>SDK internals; this type's signature may change in a minor release.
 */
public final class Checks {

    private static final Pattern CURRENCY = Pattern.compile("^[A-Z]{3}$");
    private static final Pattern UUID = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Pattern MINOR_POSITIVE = Pattern.compile("^[1-9][0-9]*$");
    private static final Pattern MINOR_NON_NEGATIVE = Pattern.compile("^(0|[1-9][0-9]*)$");
    private static final BigInteger SIGNED_64_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    private Checks() {
    }

    /** @return {@code false} for {@code null} and the empty string */
    public static boolean present(Object value) {
        return value != null && !"".equals(value);
    }

    /** @return {@code false} for {@code null}, the empty string and an empty list */
    public static boolean presentNonEmpty(Object value) {
        return present(value) && !(value instanceof Collection<?> list && list.isEmpty());
    }

    /**
     * A copy of a map with string keys, without its {@code null} values.
     *
     * @param value the value
     * @param label the name used in the error message
     * @return the copy, in the given order
     */
    public static Map<String, Object> plainMap(Object value, String label) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new ConfigurationException(label + " must be an object (a map).");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new ConfigurationException(label + " keys must be strings.");
            }
            if (entry.getValue() != null) {
                out.put(key, entry.getValue());
            }
        }
        return out;
    }

    /**
     * Refuses keys outside {@code allowed}.
     *
     * @param keys    the given keys
     * @param allowed the accepted keys
     * @param label   the name used in the error message
     */
    public static void assertKeys(Collection<String> keys, Collection<String> allowed, String label) {
        List<String> unknown = new ArrayList<>();
        for (String key : keys) {
            if (!allowed.contains(key)) {
                unknown.add(key);
            }
        }
        if (!unknown.isEmpty()) {
            throw new ConfigurationException("Unknown " + label + " field(s): " + String.join(", ", unknown)
                    + ". Allowed: " + String.join(", ", allowed) + ". The gateway rejects unknown fields with 400.");
        }
    }

    /** @return the trimmed text; a missing, blank or non-string value is refused */
    public static String text(Object value, String field) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw new ConfigurationException(field + " is required.");
        }
        return text.trim();
    }

    /** @return the trimmed text of at most {@code max} characters */
    public static String text(Object value, String field, int max) {
        String text = text(value, field);
        if (text.length() > max) {
            throw new ConfigurationException(field + " must be at most " + max + " characters.");
        }
        return text;
    }

    /** @return the upper-cased ISO 4217 code */
    public static String currency(Object value, String field) {
        String code = value instanceof String text ? text.trim().toUpperCase(Locale.ROOT) : "";
        if (!CURRENCY.matcher(code).matches()) {
            throw new ConfigurationException("'" + field + "' must be an ISO 4217 code such as TRY.");
        }
        return code;
    }

    /** @return the trimmed UUID */
    public static String uuid(Object value, String field) {
        String id = value instanceof String text ? text.trim() : "";
        if (!UUID.matcher(id).matches()) {
            throw new ConfigurationException("'" + field + "' must be a Lunixi id (UUID).");
        }
        return id;
    }

    /**
     * A point in time as ISO 8601 with a zone: an {@link Instant} (sent in UTC), an {@link OffsetDateTime}, a
     * {@link ZonedDateTime}, a {@link Date}, or a non-blank string sent as given (trimmed).
     *
     * @param value the value
     * @param field the name used in the error message
     * @return the ISO 8601 text
     */
    public static String isoTime(Object value, String field) {
        if (value instanceof Instant instant) {
            return instant.toString();
        }
        if (value instanceof OffsetDateTime time) {
            return time.toString();
        }
        if (value instanceof ZonedDateTime time) {
            return time.toOffsetDateTime().toString();
        }
        if (value instanceof Date date) {
            return date.toInstant().toString();
        }
        if (value instanceof String text && !text.isBlank()) {
            return text.trim();
        }
        throw new ConfigurationException("'" + field + "' must be an Instant, an OffsetDateTime or an ISO-8601 "
                + "string with a time zone.");
    }

    /** @return the epoch milliseconds of a readable time value, or {@code null} when it cannot be read */
    public static Long epochMillis(Object value) {
        if (value instanceof Instant instant) {
            return instant.toEpochMilli();
        }
        if (value instanceof OffsetDateTime time) {
            return time.toInstant().toEpochMilli();
        }
        if (value instanceof ZonedDateTime time) {
            return time.toInstant().toEpochMilli();
        }
        if (value instanceof Date date) {
            return date.getTime();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return OffsetDateTime.parse(text.trim()).toInstant().toEpochMilli();
            } catch (DateTimeParseException unreadable) {
                return null;
            }
        }
        return null;
    }

    /**
     * A canonical minor-unit amount string ({@code "12550"} = 125.50 TRY) within the signed 64-bit range. A JSON
     * number is refused, never converted.
     *
     * @param value    the value
     * @param field    the name used in the error message
     * @param positive {@code true} to refuse {@code "0"}
     * @return the amount text
     */
    public static String minor(Object value, String field, boolean positive) {
        Pattern pattern = positive ? MINOR_POSITIVE : MINOR_NON_NEGATIVE;
        if (!(value instanceof String text) || !pattern.matcher(text).matches()
                || new BigInteger(text).compareTo(SIGNED_64_MAX) > 0) {
            throw new ConfigurationException(field + " must be a canonical string in minor units (e.g. \"12550\" "
                    + "for 125.50).");
        }
        return text;
    }

    /**
     * An integer page size.
     *
     * @param value the value ({@link Integer} or {@link Long})
     * @param max   the largest accepted value
     * @return the size
     */
    public static long limit(Object value, int max) {
        if (!(value instanceof Integer || value instanceof Long) || ((Number) value).longValue() < 1
                || ((Number) value).longValue() > max) {
            throw new ConfigurationException("'limit' must be an integer from 1 to " + max + ".");
        }
        return ((Number) value).longValue();
    }

    /** @return the cursor, unchanged */
    public static String cursor(Object value, int maxLength) {
        if (!(value instanceof String text) || text.length() > maxLength) {
            throw new ConfigurationException("'cursor' must be the nextCursor of the previous page, passed back "
                    + "unchanged.");
        }
        return text;
    }

    /** @return the value when it is one of {@code allowed} */
    public static String oneOf(Object value, Collection<String> allowed, String field) {
        if (!(value instanceof String text) || !allowed.contains(text)) {
            throw new ConfigurationException("'" + field + "' must be one of " + String.join(", ", allowed) + ".");
        }
        return text;
    }

    /** @return one value or a list of values, each one of {@code allowed}, joined with commas */
    public static String listOf(Object value, Collection<String> allowed, String field) {
        List<String> out = new ArrayList<>();
        for (Object entry : value instanceof Collection<?> list ? list : List.of(value)) {
            out.add(oneOf(entry, allowed, field));
        }
        return String.join(",", out);
    }
}
