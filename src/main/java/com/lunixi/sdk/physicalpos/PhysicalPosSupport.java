package com.lunixi.sdk.physicalpos;

import java.math.BigInteger;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.lunixi.sdk.exception.ConfigurationException;
import com.lunixi.sdk.exception.LunixiApiException;
import com.lunixi.sdk.exception.LunixiException;
import com.lunixi.sdk.exception.PhysicalPosCardDataRejectedException;
import com.lunixi.sdk.exception.PhysicalPosOutcomeUnknownException;
import com.lunixi.sdk.exception.PhysicalPosRateLimitException;
import com.lunixi.sdk.exception.PhysicalPosVoidAfterSettlementException;
import com.lunixi.sdk.exception.RateLimitException;
import com.lunixi.sdk.exception.ServiceUnavailableException;
import com.lunixi.sdk.http.Envelope;
import com.lunixi.sdk.internal.ApiClient;
import com.lunixi.sdk.internal.RequestOptions;

/**
 * Shared request checks of the physical POS clients: field whitelists, the card-data key rule, filters,
 * {@code Idempotency-Key} handling, path segments and error mapping.
 *
 * <p>Only shape is checked here (known fields, JSON types, integers that are real integers). Value rules (operation
 * set, ranges, item totals, reason codes …) belong to the gateway, which answers with typed
 * {@code physical_pos.*} codes. SDK internals.
 */
final class PhysicalPosSupport {

    static final String BASE = "/api/v1/physical-pos";
    static final int MAX_LIMIT = 100;
    static final long MAX_SAFE_INTEGER = 9_007_199_254_740_991L;
    static final long ROW_VERSION_MAX = 2_147_483_647L;
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[\\x21-\\x7E]{8,200}");
    private static final SecureRandom RANDOM = new SecureRandom();

    private PhysicalPosSupport() {
    }

    // ── Card data key rule (port of the gateway's isPhysicalPosCardDataKey) ──────────────────────────────────

    private static final List<String> CARD_NUMBER_TERMS = List.of(
            "pan", "pannumber", "cardpan", "cardnumber", "cardno", "cardnum", "cardnr", "ccnumber", "ccnum",
            "primaryaccountnumber");

    private static final List<String> CARD_SECRET_TERMS = List.of(
            "cvv", "cvv2", "cvc", "cvc2", "cvn", "cvn2", "cav2", "cvvcode", "cvccode", "cardcode", "securitycode",
            "cardverificationcode", "cardverificationvalue", "track1", "track2", "track3", "tracki", "trackii",
            "trackiii", "trackdata", "magstripe", "magneticstripe", "pinblock", "pincode", "pinnumber", "pindata",
            "cardpin", "cardholderpin", "encryptedpin", "expdate", "expmonth", "expyear", "expmmyy", "expirydate",
            "expirymonth", "expiryyear", "expirymmyy", "expirationdate", "expirationmonth", "expirationyear",
            "expiremonth", "expireyear", "cardexpiry", "cardexpiration");

    private static final Set<String> CARD_DATA_TERMS;

    static {
        Set<String> terms = new HashSet<>(CARD_NUMBER_TERMS);
        terms.addAll(CARD_SECRET_TERMS);
        CARD_DATA_TERMS = Collections.unmodifiableSet(terms);
    }

    private static final List<String> CARD_DATA_SUFFIX_TERMS = List.of(
            "cardnumber", "pannumber", "primaryaccountnumber", "cvv", "cvv2", "cvc", "cvc2", "cvvcode",
            "securitycode", "track1", "track2", "trackdata", "pinblock", "expmonth", "expyear", "expirydate",
            "expirymonth", "expiryyear", "expirationdate", "cardexpiry");

    private static final Set<String> WHOLE_KEY_TERMS = Set.of("track", "expiry", "expiration", "pin", "cid", "csc");
    private static final Set<String> MASK_TOKENS = Set.of("masked", "mask");
    private static final Set<String> NUMBER_TERMS = Set.copyOf(CARD_NUMBER_TERMS);
    private static final Pattern DIGIT_RUN_KEY = Pattern.compile("[0-9](?:[ -]?[0-9]){12,}");
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");

    /** Splits a key into lower-case tokens at camelCase / acronym / letter-digit boundaries and separators. */
    static List<String> keyTokens(String key) {
        String spaced = key
                .replaceAll("([a-z0-9])([A-Z])", "$1 $2")
                .replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2")
                .replaceAll("([A-Za-z])([0-9])", "$1 $2")
                .replaceAll("([0-9])([A-Za-z])", "$1 $2")
                .toLowerCase(Locale.ROOT);
        List<String> tokens = new ArrayList<>();
        for (String token : spaced.split("[^a-z0-9]+")) {
            if (!token.isEmpty()) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    /** Whether a key name is card data (the gateway's token rule). The value is never read. */
    static boolean isCardDataKey(String key) {
        List<String> tokens = keyTokens(key);
        if (tokens.isEmpty()) {
            return false;
        }
        if (WHOLE_KEY_TERMS.contains(String.join("", tokens))) {
            return true;
        }
        boolean masked = tokens.stream().anyMatch(MASK_TOKENS::contains);
        for (int start = 0; start < tokens.size(); start++) {
            StringBuilder joined = new StringBuilder();
            for (int end = start; end < tokens.size(); end++) {
                joined.append(tokens.get(end));
                String run = joined.toString();
                if (CARD_DATA_TERMS.contains(run) && !(masked && NUMBER_TERMS.contains(run))) {
                    return true;
                }
                for (String term : CARD_DATA_SUFFIX_TERMS) {
                    if (run.endsWith(term) && !(masked && NUMBER_TERMS.contains(term))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static String child(String path, String key) {
        if (!IDENTIFIER.matcher(key).matches()) {
            return path + "[?]";
        }
        return path.isEmpty() ? key : path + "." + key;
    }

    /**
     * The path of the first key that looks like card data, or {@code null}. A key holding a long digit run (a card
     * number used as a key) is reported at its parent path, so the number itself is never echoed.
     */
    static String findCardDataPath(Object value, String root) {
        return findCardDataPath(value, "", root);
    }

    private static String findCardDataPath(Object value, String path, String root) {
        if (value instanceof Map<?, ?> map) {
            for (Object rawKey : map.keySet()) {
                String key = String.valueOf(rawKey);
                if (DIGIT_RUN_KEY.matcher(key).find()) {
                    return path.isEmpty() ? root : path;
                }
                if (isCardDataKey(key)) {
                    return child(path, key);
                }
            }
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String found = findCardDataPath(entry.getValue(), child(path, String.valueOf(entry.getKey())), root);
                if (found != null) {
                    return found;
                }
            }
            return null;
        }
        if (value instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                String found = findCardDataPath(list.get(i), path + "[" + i + "]", root);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    static PhysicalPosCardDataRejectedException cardDataRejected(String field) {
        return new PhysicalPosCardDataRejectedException(
                "Kart verisi gönderilmez; kart cihazda okunur / the request must not contain card data (card number,"
                        + " expiry, security code, track or PIN data); the card is read on the device. Field: " + field,
                0, Map.of(), null, field);
    }

    // ── PAN value scan (port of the service's containsPanLikeValue) ──────────────────────────────────────────

    /**
     * A 13–19 digit candidate (plain, space or dash separated) that is a whole token: no letter or digit right
     * before or after it, and no "letter/digit + dash" joining it to an identifier (UUIDs, {@code ORD-4111…}).
     */
    private static final Pattern PAN_CANDIDATE = Pattern.compile(
            "(?<![\\p{L}\\p{N}])(?<![\\p{L}\\p{N}]-)[0-9](?:[ -]?[0-9]){12,18}(?![\\p{L}\\p{N}])(?!-[\\p{L}\\p{N}])");

    /**
     * Whether the text holds a Luhn-valid 13–19 digit card number. {@code itemText} (item names only): an
     * unseparated 13 or 14 digit candidate with a valid GTIN check digit is a barcode, not a card number.
     */
    static boolean containsPanLikeValue(String text, boolean itemText) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        Matcher matcher = PAN_CANDIDATE.matcher(text);
        while (matcher.find()) {
            String candidate = matcher.group();
            String digits = candidate.replace(" ", "").replace("-", "");
            if (digits.length() < 13 || digits.length() > 19) {
                continue;
            }
            if (itemText && digits.equals(candidate) && digits.length() <= 14 && isValidGtin(digits)) {
                continue;
            }
            if (luhnValid(digits)) {
                return true;
            }
        }
        return false;
    }

    private static boolean luhnValid(String digits) {
        int sum = 0;
        for (int i = 0; i < digits.length(); i++) {
            int digit = digits.charAt(digits.length() - 1 - i) - '0';
            if ((i & 1) != 0) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
        }
        return sum % 10 == 0;
    }

    private static boolean isValidGtin(String digits) {
        if (!digits.matches("[0-9]{8}|[0-9]{12,14}")) {
            return false;
        }
        int sum = 0;
        int weight = 3;
        for (int i = digits.length() - 2; i >= 0; i--) {
            sum += (digits.charAt(i) - '0') * weight;
            weight = weight == 3 ? 1 : 3;
        }
        return (10 - (sum % 10)) % 10 == digits.charAt(digits.length() - 1) - '0';
    }

    /** Refuses a free-text value holding a card number; only the field path is reported, never the value. */
    static void rejectPanValue(Object value, String field, boolean itemText) {
        if (value instanceof String text && containsPanLikeValue(text, itemText)) {
            throw cardDataRejected(field);
        }
    }

    /** Free-text filters scanned for card numbers before they reach the URL. */
    static final Set<String> PAN_SCANNED_FILTERS = Set.of("merchantReference", "q");

    // ── Request shapes ───────────────────────────────────────────────────────────────────────────────────────

    enum Kind { TEXT, MONEY, INT, BOOL, TEXT_LIST, OBJECT, OBJECT_LIST }

    /** One body field: JSON kind, whether it must be present, whether {@code null} is accepted. */
    record Field(String name, Kind kind, boolean required, boolean nullable, Shape nested, long min, long max) {
    }

    /** A request body shape (whitelist). */
    static final class Shape {
        private final String label;
        private final Map<String, Field> fields = new LinkedHashMap<>();

        Shape(String label) {
            this.label = label;
        }

        Shape text(String name, boolean required, boolean nullable) {
            return add(new Field(name, Kind.TEXT, required, nullable, null, 0, 0));
        }

        Shape integer(String name, boolean required, boolean nullable, long min, long max) {
            return add(new Field(name, Kind.INT, required, nullable, null, min, max));
        }

        Shape money(String name, boolean required, boolean zeroAllowed) {
            return add(new Field(name, Kind.MONEY, required, false, null, zeroAllowed ? 0 : 1, Long.MAX_VALUE));
        }

        Shape bool(String name) {
            return add(new Field(name, Kind.BOOL, false, false, null, 0, 0));
        }

        Shape textList(String name, boolean required) {
            return add(new Field(name, Kind.TEXT_LIST, required, false, null, 0, 0));
        }

        Shape object(String name, Shape nested, boolean required, boolean nullable) {
            return add(new Field(name, Kind.OBJECT, required, nullable, nested, 0, 0));
        }

        Shape objectList(String name, Shape nested) {
            return add(new Field(name, Kind.OBJECT_LIST, false, false, nested, 0, 0));
        }

        Shape rowVersion() {
            return integer("rowVersion", true, false, 1, ROW_VERSION_MAX);
        }

        private Shape add(Field field) {
            fields.put(field.name(), field);
            return this;
        }

        Set<String> names() {
            return fields.keySet();
        }
    }

    /**
     * Checks a body against its shape and returns the copy that is sent. Card-data keys are reported first (as
     * {@link PhysicalPosCardDataRejectedException}), then unknown fields, missing fields and wrong JSON types (as
     * {@link ConfigurationException}). Nothing is sent when a check fails.
     */
    static Map<String, Object> body(Map<String, ?> request, Shape shape) {
        if (request == null) {
            throw new ConfigurationException("'" + shape.label + "' zorunlu / '" + shape.label + "' is required.");
        }
        String cardField = findCardDataPath(request, "(body)");
        if (cardField != null) {
            throw cardDataRejected(cardField);
        }
        return checkObject(request, shape, "");
    }

    private static Map<String, Object> checkObject(Map<?, ?> source, Shape shape, String path) {
        Set<String> unknown = new LinkedHashSet<>();
        for (Object key : source.keySet()) {
            if (!shape.fields.containsKey(String.valueOf(key))) {
                unknown.add(String.valueOf(key));
            }
        }
        if (!unknown.isEmpty()) {
            throw new ConfigurationException("Bilinmeyen alan / unknown " + shape.label + " field(s): "
                    + String.join(", ", prefixed(path, unknown)) + ". Allowed: " + String.join(", ", shape.names())
                    + ". The gateway rejects unknown fields with 400.");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Field field : shape.fields.values()) {
            String fieldPath = path.isEmpty() ? field.name() : path + "." + field.name();
            boolean present = source.containsKey(field.name());
            Object value = source.get(field.name());
            if (present && field.kind() == Kind.MONEY) {
                out.put(field.name(), checkValue(field, value, fieldPath));
                continue;
            }
            boolean emptyText = field.kind() == Kind.TEXT && value instanceof String text && text.isEmpty();
            if (!present || (value == null && !field.nullable()) || emptyText) {
                // An empty optional string is left out, never sent; an empty required one is an error.
                if (field.required()) {
                    throw new ConfigurationException("'" + fieldPath + "' zorunlu / '" + fieldPath + "' is required.");
                }
                continue;
            }
            out.put(field.name(), value == null ? null : checkValue(field, value, fieldPath));
        }
        return out;
    }

    private static List<String> prefixed(String path, Set<String> keys) {
        List<String> out = new ArrayList<>();
        for (String key : keys) {
            out.add(path.isEmpty() ? key : path + "." + key);
        }
        return out;
    }

    private static Object checkValue(Field field, Object value, String path) {
        switch (field.kind()) {
            case TEXT:
                if (!(value instanceof String)) {
                    throw typeError(path, "a string");
                }
                return value;
            case MONEY:
                return money(value, path, field.min() == 0);
            case INT:
                long number = integer(value, path);
                if (number < field.min() || number > field.max()) {
                    throw new ConfigurationException("'" + path + "' " + field.min() + ".." + field.max()
                            + " aralığında olmalı / must be between " + field.min() + " and " + field.max() + ".");
                }
                return number;
            case BOOL:
                if (!(value instanceof Boolean)) {
                    throw typeError(path, "a boolean");
                }
                return value;
            case TEXT_LIST:
                if (!(value instanceof List<?> list)) {
                    throw typeError(path, "a list of strings");
                }
                List<Object> texts = new ArrayList<>();
                for (Object item : list) {
                    if (!(item instanceof String)) {
                        throw typeError(path, "a list of strings");
                    }
                    texts.add(item);
                }
                return texts;
            case OBJECT:
                if (!(value instanceof Map<?, ?> map)) {
                    throw typeError(path, "an object");
                }
                return checkObject(map, field.nested(), path);
            case OBJECT_LIST:
                if (!(value instanceof List<?> rows) || rows.isEmpty()) {
                    throw new ConfigurationException("'" + path + "' boş olmayan bir liste olmalı; kalemsiz istekte alanı"
                            + " göndermeyin / must be a non-empty list; omit the field for a request without items.");
                }
                List<Object> checked = new ArrayList<>();
                for (int i = 0; i < rows.size(); i++) {
                    if (!(rows.get(i) instanceof Map<?, ?> row)) {
                        throw typeError(path + "[" + i + "]", "an object");
                    }
                    checked.add(checkObject(row, field.nested(), path + "[" + i + "]"));
                }
                return checked;
            default:
                throw new IllegalStateException("unknown kind");
        }
    }

    /** A real JSON integer within the safe range; numeric strings and fractions are refused. */
    static long integer(Object value, String path) {
        if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte) {
            long number = ((Number) value).longValue();
            if (Math.abs(number) > MAX_SAFE_INTEGER) {
                throw typeError(path, "a safe integer (|n| <= 2^53-1)");
            }
            return number;
        }
        if (value instanceof BigInteger big && big.abs().compareTo(BigInteger.valueOf(MAX_SAFE_INTEGER)) <= 0) {
            return big.longValue();
        }
        throw typeError(path, "an integer (a numeric string or a fraction is rejected by the gateway)");
    }

    private static ConfigurationException typeError(String path, String expected) {
        return new ConfigurationException("'" + path + "' türü hatalı / must be " + expected + ".");
    }

    // ── Filters ──────────────────────────────────────────────────────────────────────────────────────────────

    static String money(Object value, String field, boolean zeroAllowed) {
        try {
            long amount = com.lunixi.sdk.payment.Money.integer(value);
            if (!zeroAllowed && amount == 0) throw new IllegalArgumentException("Positive minor units required.");
            return (String) value;
        } catch (IllegalArgumentException error) {
            throw new ConfigurationException("'" + field + "' must be a canonical minor-unit string in the signed64 range.");
        }
    }

    private static final Set<String> INTEGER_FILTERS = Set.of("limit");

    /**
     * Builds the query of a list / report call. Only the listed filters are accepted (the gateway rejects an
     * unknown query field with 400); null and empty values are dropped.
     */
    static RequestOptions.Builder query(Map<String, ?> filters, List<String> allowed, String route) {
        RequestOptions.Builder options = RequestOptions.builder().stepUp(true);
        if (filters == null || filters.isEmpty()) {
            return options;
        }
        String cardField = findCardDataPath(filters, "(query)");
        if (cardField != null) {
            throw cardDataRejected(cardField);
        }
        List<String> unknown = new ArrayList<>();
        for (Object key : filters.keySet()) {
            if (!allowed.contains(String.valueOf(key))) {
                unknown.add(String.valueOf(key));
            }
        }
        if (!unknown.isEmpty()) {
            throw new ConfigurationException("Bilinmeyen süzgeç / unknown " + route + " filter(s): "
                    + String.join(", ", unknown) + ". Allowed: " + String.join(", ", allowed)
                    + ". The gateway rejects unknown query fields with 400.");
        }
        for (String key : allowed) {
            Object value = filters.get(key);
            if (filters.containsKey(key) && ("amountFromMinor".equals(key) || "amountToMinor".equals(key))) {
                options.query(key, money(value, key, false));
                continue;
            }
            if (value == null) {
                continue;
            }
            if (INTEGER_FILTERS.contains(key)) {
                long number = integer(value, key);
                if ("limit".equals(key) && (number < 1 || number > MAX_LIMIT)) {
                    throw new ConfigurationException("'limit' 1.." + MAX_LIMIT + " olmalı / must be between 1 and "
                            + MAX_LIMIT + ".");
                }
                options.query(key, Long.toString(number));
            } else if ("includeTotal".equals(key)) {
                if (!(value instanceof Boolean)) {
                    throw typeError(key, "a boolean");
                }
                options.query(key, value.toString());
            } else {
                String text = filterText(key, value);
                if (text.isEmpty()) {
                    continue;
                }
                if (PAN_SCANNED_FILTERS.contains(key)) {
                    rejectPanValue(text, key, false);
                }
                options.query(key, text);
            }
        }
        return options;
    }

    private static final DateTimeFormatter UTC_MILLIS =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);
    private static final Set<String> DAY_FILTERS = Set.of("dateFrom", "dateTo");
    private static final Set<String> TIME_FILTERS = Set.of("createdFrom", "createdTo");

    /**
     * A filter value as sent. {@code dateFrom} / {@code dateTo} accept a {@link LocalDate} (or a zoned / offset
     * date-time, by its own calendar day — never converted to Europe/Istanbul) and are sent as {@code YYYY-MM-DD};
     * {@code createdFrom} / {@code createdTo} accept an {@link Instant}, {@link OffsetDateTime} or
     * {@link ZonedDateTime} and are sent as ISO-8601 UTC with milliseconds and {@code Z}.
     */
    private static String filterText(String key, Object value) {
        if (value instanceof String text) {
            return text;
        }
        if (DAY_FILTERS.contains(key)) {
            if (value instanceof LocalDate day) {
                return day.toString();
            }
            if (value instanceof OffsetDateTime time) {
                return time.toLocalDate().toString();
            }
            if (value instanceof ZonedDateTime time) {
                return time.toLocalDate().toString();
            }
            throw typeError(key, "a YYYY-MM-DD string or a LocalDate");
        }
        if (TIME_FILTERS.contains(key)) {
            if (value instanceof Instant instant) {
                return UTC_MILLIS.format(instant);
            }
            if (value instanceof OffsetDateTime time) {
                return UTC_MILLIS.format(time.toInstant());
            }
            if (value instanceof ZonedDateTime time) {
                return UTC_MILLIS.format(time.toInstant());
            }
            throw typeError(key, "an ISO-8601 UTC string or an Instant");
        }
        throw typeError(key, "a string");
    }

    // ── Idempotency-Key ──────────────────────────────────────────────────────────────────────────────────────

    /** A new random (version 4) UUID — the recommended {@code Idempotency-Key} for one operation. */
    static String newIdempotencyKey() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        long most = 0;
        long least = 0;
        for (int i = 0; i < 8; i++) {
            most = (most << 8) | (bytes[i] & 0xff);
        }
        for (int i = 8; i < 16; i++) {
            least = (least << 8) | (bytes[i] & 0xff);
        }
        most = (most & 0xffffffffffff0fffL) | 0x0000000000004000L;
        least = (least & 0x3fffffffffffffffL) | 0x8000000000000000L;
        return new UUID(most, least).toString();
    }

    /**
     * The key of a payment start or reversal: REQUIRED. There is no generated fallback — a key the caller did not
     * keep cannot be used to retry an unknown outcome, and a new key can charge twice.
     */
    static String requiredIdempotencyKey(String given, String operation) {
        if (given == null) {
            throw new ConfigurationException("Idempotency-Key zorunlu / a stable Idempotency-Key is required for "
                    + operation + ". Create one with PhysicalPosClient.newIdempotencyKey(), store it with your order,"
                    + " then call; when the outcome is unknown call again with the SAME key.");
        }
        return idempotencyKey(given);
    }

    /**
     * The given key, checked exactly as given (never trimmed; empty and blank keys are refused) or, only when
     * {@code null}, a generated one — used for the whole call, retries included.
     */
    static String idempotencyKey(String given) {
        if (given == null) {
            return newIdempotencyKey();
        }
        if (!IDEMPOTENCY_KEY.matcher(given).matches()) {
            throw new ConfigurationException("Idempotency-Key 8-200 boşluksuz yazdırılabilir ASCII karakter olmalı /"
                    + " must be 8-200 printable ASCII characters without spaces. Use a new UUID per operation.");
        }
        return given;
    }

    // ── Paths ────────────────────────────────────────────────────────────────────────────────────────────────

    static String segment(String id, String name) {
        if (id == null || id.isBlank()) {
            throw new ConfigurationException("'" + name + "' zorunlu / '" + name + "' is required.");
        }
        if (".".equals(id) || "..".equals(id)) {
            throw new ConfigurationException("'" + name + "' geçersiz / is not a valid identifier.");
        }
        return URLEncoder.encode(id, StandardCharsets.UTF_8).replace("+", "%20");
    }

    // ── Calls and error mapping ──────────────────────────────────────────────────────────────────────────────

    /** Sends one physical POS request (read or management write) and maps the errors with a physical POS meaning. */
    static Map<String, Object> send(ApiClient api, String method, String path, Object body, RequestOptions options,
                                    String idempotencyKey) {
        try {
            Map<String, Object> response = api.request(method, path, body, options);
            PhysicalPosMoney.validate(response);
            return response;
        } catch (LunixiApiException error) {
            throw map(error, idempotencyKey);
        }
    }

    /**
     * Gateway codes that say the payment write started nothing (no record was written): a {@code 5xx} with one of
     * these is a definite failure that may be retried with the same key. Every other {@code 5xx} is ambiguous.
     */
    static final Set<String> NOT_STARTED_CODES = Set.of(
            "physical_pos.presence.unavailable",
            "physical_pos.signing.unavailable",
            "physical_pos.service.busy",
            "physical_pos.service.clock_skew",
            "physical_pos.service.not_configured");

    private enum Attempt { AMBIGUOUS, NOT_STARTED, DEFINITE }

    private static Attempt classify(LunixiApiException error) {
        int status = error.statusCode();
        if (status >= 400 && status < 500) {
            return Attempt.DEFINITE;
        }
        if (status >= 500 && error.code().map(NOT_STARTED_CODES::contains).orElse(false)) {
            return Attempt.NOT_STARTED;
        }
        // No HTTP answer (0), a 2xx that reported a failure, any other 5xx.
        return Attempt.AMBIGUOUS;
    }

    /**
     * Sends a payment write (start, reversal, cancel) with its own attempt loop: the transport's automatic retry is
     * off, so the outcome of every attempt is known. Every attempt sends the same key and body.
     *
     * <ul>
     *   <li>success = a {@code 2xx} whose {@code data.payment} has a {@code paymentId};</li>
     *   <li>ambiguous = no HTTP answer, a {@code 2xx} without a readable payment, a {@code 5xx} not in
     *       {@link #NOT_STARTED_CODES} — retried while attempts remain;</li>
     *   <li>not started = a {@code 5xx} in {@link #NOT_STARTED_CODES} — retried, not ambiguous;</li>
     *   <li>definite = any {@code 4xx} — stops.</li>
     * </ul>
     *
     * When any attempt was ambiguous the call fails with {@link PhysicalPosOutcomeUnknownException} (key + last
     * error as the cause), whatever the last error was — an earlier attempt may have created the payment.
     */
    static Map<String, Object> paymentWrite(ApiClient api, String path, Map<String, Object> body, String key) {
        RequestOptions options = RequestOptions.builder().stepUp(true).idempotencyKey(key).retry(false).build();
        int attempts = Math.max(0, api.maxRetries()) + 1;
        boolean ambiguous = false;
        RuntimeException last = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            boolean retryable;
            try {
                ApiClient.Exchange exchange = api.exchange("POST", path, body, options);
                Map<String, Object> envelope = exchange.envelope();
                if (hasPayment(envelope)) {
                    return envelope;
                }
                // The real HTTP status of the answer — never inferred from (or invented for) an unreadable body.
                last = new LunixiApiException("Yanıtta ödeme yok / the 2xx answer carried no readable payment.",
                        exchange.statusCode(), null, envelope, null);
                ambiguous = true;
                retryable = true;
            } catch (LunixiApiException error) {
                last = error;
                Attempt kind = classify(error);
                ambiguous |= kind == Attempt.AMBIGUOUS;
                retryable = kind != Attempt.DEFINITE;
            } catch (LunixiException local) {
                // Configuration / signing: raised before this attempt was sent.
                if (ambiguous) {
                    throw outcomeUnknown(local, key);
                }
                throw local;
            } catch (RuntimeException unexpected) {
                // A custom transport or the body read failed in a way the SDK cannot place.
                last = unexpected;
                ambiguous = true;
                retryable = true;
            }
            if (!retryable || attempt == attempts || !backoff(attempt)) {
                break;
            }
        }
        if (ambiguous) {
            throw outcomeUnknown(last, key);
        }
        throw map((LunixiApiException) last, key);
    }

    private static boolean hasPayment(Map<String, Object> envelope) {
        try { PhysicalPosMoney.validate(envelope); } catch (ConfigurationException invalid) { return false; }
        Object paymentId = object(data(envelope).get("payment")).get("paymentId");
        return paymentId instanceof String id && !id.isEmpty();
    }

    private static boolean backoff(int attempt) {
        long delayMs = Math.min(2000L, 100L * (1L << (attempt - 1)));
        try {
            Thread.sleep(delayMs);
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static PhysicalPosOutcomeUnknownException outcomeUnknown(RuntimeException last, String key) {
        LunixiApiException api = last instanceof LunixiApiException error ? error : null;
        Duration retryAfter = null;
        if (api instanceof RateLimitException limited) {
            retryAfter = limited.retryAfter().orElse(null);
        } else if (api instanceof ServiceUnavailableException unavailable) {
            retryAfter = unavailable.retryAfter().orElse(null);
        }
        if (retryAfter == null && api != null) {
            retryAfter = seconds(details(api.response()).get("retryAfterSeconds"));
        }
        PhysicalPosOutcomeUnknownException unknown = new PhysicalPosOutcomeUnknownException(
                "Sonuç belirsiz; aynı Idempotency-Key ile tekrar deneyin / the outcome is unknown; call again with the"
                        + " same Idempotency-Key or find the payment by merchantReference. Never retry with a new key.",
                api == null ? 0 : api.statusCode(), api == null ? null : api.code().orElse(null),
                api == null ? Map.of() : api.response(), api == null ? null : api.requestId().orElse(null), key,
                retryAfter);
        if (last != null) {
            unknown.initCause(last);
        }
        return unknown;
    }

    /**
     * Maps a definite error: card-data rejection, void after end-of-day and {@code 429} get their typed exception
     * (with the key).
     */
    static LunixiApiException map(LunixiApiException error, String idempotencyKey) {
        String code = error.code().orElse(null);
        Map<String, Object> details = details(error.response());
        if (PhysicalPosCardDataRejectedException.CODE.equals(code)) {
            Object field = details.get("field");
            PhysicalPosCardDataRejectedException mapped = new PhysicalPosCardDataRejectedException(
                    error.getMessage(), error.statusCode(), error.response(), error.requestId().orElse(null),
                    field instanceof String ? (String) field : null, idempotencyKey);
            mapped.initCause(error);
            return mapped;
        }
        if (PhysicalPosVoidAfterSettlementException.CODE.equals(code)) {
            Object allowed = details.get("allowedOperation");
            PhysicalPosVoidAfterSettlementException mapped = new PhysicalPosVoidAfterSettlementException(
                    error.getMessage(), error.statusCode(), error.response(), error.requestId().orElse(null),
                    allowed instanceof String ? (String) allowed : null, idempotencyKey);
            mapped.initCause(error);
            return mapped;
        }
        if (error.statusCode() == 429) {
            Duration retryAfter = error instanceof RateLimitException limited
                    ? limited.retryAfter().orElse(null) : null;
            if (retryAfter == null) {
                retryAfter = seconds(details.get("retryAfterSeconds"));
            }
            PhysicalPosRateLimitException mapped = new PhysicalPosRateLimitException(error.getMessage(),
                    error.statusCode(), code, error.response(), error.requestId().orElse(null), retryAfter,
                    idempotencyKey);
            mapped.initCause(error);
            return mapped;
        }
        return error;
    }

    /**
     * The original payment of a reversal webhook payload: {@code {paymentId, origin}} when {@code data.original}
     * carries both as non-empty strings (other keys are dropped); empty otherwise (a root payment).
     */
    static Optional<Map<String, String>> webhookOriginal(Map<String, Object> data) {
        Object original = data == null ? null : data.get("original");
        if (!(original instanceof Map<?, ?> map)) {
            return Optional.empty();
        }
        Object paymentId = map.get("paymentId");
        Object origin = map.get("origin");
        if (!(paymentId instanceof String id) || id.isEmpty() || !(origin instanceof String source)
                || source.isEmpty()) {
            return Optional.empty();
        }
        Map<String, String> normalised = new LinkedHashMap<>();
        normalised.put("paymentId", id);
        normalised.put("origin", source);
        return Optional.of(Collections.unmodifiableMap(normalised));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> details(Map<String, Object> response) {
        Object details = response == null ? null : response.get("details");
        return details instanceof Map ? (Map<String, Object>) details : Map.of();
    }

    private static Duration seconds(Object value) {
        if (value instanceof Number number && number.longValue() >= 0) {
            return Duration.ofSeconds(number.longValue());
        }
        if (value instanceof String text) {
            try {
                return Duration.ofSeconds(Long.parseLong(text.trim()));
            } catch (NumberFormatException notSeconds) {
                return null;
            }
        }
        return null;
    }

    /** The envelope's {@code statusCode}, or the fallback when it carried none. */
    static int statusCode(Map<String, Object> envelope, int fallback) {
        Object value = envelope == null ? null : envelope.get("statusCode");
        return value instanceof Number number && number.intValue() >= 100 ? number.intValue() : fallback;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> object(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : Map.of();
    }

    // ── Paging ───────────────────────────────────────────────────────────────────────────────────────────────

    /** Walks every page lazily, following {@code nextCursor} until the last page. */
    static Iterable<Map<String, Object>> iterate(Map<String, ?> filters,
                                                 Function<Map<String, Object>, PhysicalPosPage> fetch) {
        Map<String, Object> seed = filters == null ? new LinkedHashMap<>() : new LinkedHashMap<>(filters);
        return () -> new Iterator<>() {
            private Object cursor = seed.get("cursor");
            private Iterator<Map<String, Object>> page = Collections.emptyIterator();
            private boolean exhausted;

            @Override
            public boolean hasNext() {
                while (!page.hasNext() && !exhausted) {
                    Map<String, Object> request = new LinkedHashMap<>(seed);
                    if (cursor == null) {
                        request.remove("cursor");
                    } else {
                        request.put("cursor", cursor);
                    }
                    PhysicalPosPage fetched = fetch.apply(request);
                    page = fetched.items().iterator();
                    if (!fetched.hasMore() || fetched.nextCursor().isEmpty()) {
                        exhausted = true;
                    } else {
                        cursor = fetched.nextCursor().get();
                    }
                }
                return page.hasNext();
            }

            @Override
            public Map<String, Object> next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return page.next();
            }
        };
    }

    static List<String> list(String... names) {
        return List.copyOf(Arrays.asList(names));
    }

    static Map<String, Object> data(Map<String, Object> envelope) {
        return Envelope.data(envelope);
    }
}
