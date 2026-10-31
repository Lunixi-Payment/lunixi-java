package com.lunixi.sdk.json;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Minimal JSON reader/writer written against one requirement: the bytes this
 * class emits for a request body must be the bytes the gateway hashes.
 *
 * <p><b>Why the SDK does not use a JSON library.</b> The gateway's signature
 * guard recomputes the body digest as {@code JSON.stringify(parsedBody)} — it
 * parses the body it received and re-serialises it with JavaScript's own rules,
 * then compares that hash to the {@code Digest} header the client sent. A
 * general-purpose mapper is free to pretty-print, to escape {@code /} or
 * non-ASCII characters, or to reorder keys; each of those produces a different
 * hash and the request fails with {@code INVALID_DIGEST} — for the caller, an
 * unexplainable 401 on a body that looks correct. So the serialiser is part of
 * the signing contract and lives here, locked by tests.
 *
 * <p>Rules this writer follows, all of them taken from {@code JSON.stringify}:
 * <ul>
 *   <li>no whitespace between tokens (compact);</li>
 *   <li>escapes only {@code "} {@code \} and {@code U+0000}–{@code U+001F}
 *       (with the short forms {@code \b \t \n \f \r}); {@code /} and every
 *       other printable character — Turkish letters included — are emitted
 *       raw as UTF-8;</li>
 *   <li>a lone surrogate is emitted as a {@code &#92;uXXXX} escape, which is what
 *       well-formed {@code JSON.stringify} does;</li>
 *   <li>object keys that are JavaScript array indices come first in ascending
 *       numeric order, then the remaining keys in insertion order — see
 *       {@link #orderedKeys(Map)}.</li>
 * </ul>
 *
 * <p>The reader is the matching half: it returns {@link LinkedHashMap} for
 * objects (so wire order survives), {@link ArrayList} for arrays,
 * {@link String}, {@link Boolean}, {@code null}, and {@link Long} or
 * {@link Double} for numbers.
 */
public final class Json {

    /** Largest JavaScript array index, {@code 2^32 - 2}. */
    private static final long MAX_ARRAY_INDEX = 4294967294L;

    private Json() {
    }

    // ───────────────────────────── writing ─────────────────────────────

    /**
     * Serialises a value to compact JSON.
     *
     * @param value a {@link Map}, {@link Collection}, array, {@link String},
     *              {@link Number}, {@link Boolean}, {@link Enum} or {@code null}
     * @return the JSON text, byte-identical to what the gateway re-serialises
     * @throws IllegalArgumentException for a type that has no JSON form, or a
     *                                  non-finite number ({@code NaN} /
     *                                  infinity have no JSON representation)
     */
    public static String stringify(Object value) {
        StringBuilder out = new StringBuilder(256);
        write(value, out);
        return out.toString();
    }

    private static void write(Object value, StringBuilder out) {
        if (value == null) {
            out.append("null");
            return;
        }
        if (value instanceof String) {
            writeString((String) value, out);
            return;
        }
        if (value instanceof Boolean) {
            out.append(((Boolean) value) ? "true" : "false");
            return;
        }
        if (value instanceof Number) {
            writeNumber((Number) value, out);
            return;
        }
        if (value instanceof Enum<?>) {
            writeString(((Enum<?>) value).name(), out);
            return;
        }
        if (value instanceof Map<?, ?>) {
            writeObject((Map<?, ?>) value, out);
            return;
        }
        if (value instanceof Collection<?>) {
            writeArray((Collection<?>) value, out);
            return;
        }
        if (value instanceof Object[]) {
            writeArray(List.of((Object[]) value), out);
            return;
        }
        throw new IllegalArgumentException(
                "Değer JSON'a çevrilemez / value is not JSON-serialisable: " + value.getClass().getName()
                        + ". Map, Collection, String, Number, Boolean ya da null kullanın.");
    }

    private static void writeObject(Map<?, ?> map, StringBuilder out) {
        out.append('{');
        boolean first = true;
        for (Object key : orderedKeys(map)) {
            if (!first) {
                out.append(',');
            }
            first = false;
            writeString(String.valueOf(key), out);
            out.append(':');
            write(map.get(key), out);
        }
        out.append('}');
    }

    private static void writeArray(Collection<?> items, StringBuilder out) {
        out.append('[');
        boolean first = true;
        for (Object item : items) {
            if (!first) {
                out.append(',');
            }
            first = false;
            write(item, out);
        }
        out.append(']');
    }

    /**
     * Returns a map's keys in the order {@code JSON.stringify} emits them after
     * {@code JSON.parse}.
     *
     * <p>JavaScript objects keep integer-like keys separately: a key that is the
     * canonical decimal form of an integer in {@code 0 … 2^32 - 2} ("0", "7",
     * "2024") is an <i>array index</i> and iterates first, in ascending numeric
     * order; every other key follows in insertion order. A body such as
     * {@code {"metadata":{"crmId":"A","2024":"B"}}} is therefore hashed by the
     * gateway as {@code {"2024":…,"crmId":…}}. Emitting it in insertion order
     * would hash differently and the signed request would be rejected with
     * {@code INVALID_DIGEST} — a failure that looks like a bad key, not like a
     * key-order problem.
     *
     * @param map the object whose keys to order
     * @return the keys, indices first
     */
    static List<Object> orderedKeys(Map<?, ?> map) {
        TreeMap<Long, Object> indices = new TreeMap<>();
        List<Object> names = new ArrayList<>();
        for (Object key : map.keySet()) {
            Long index = asArrayIndex(String.valueOf(key));
            if (index != null) {
                indices.put(index, key);
            } else {
                names.add(key);
            }
        }
        if (indices.isEmpty()) {
            return names;
        }
        List<Object> ordered = new ArrayList<>(indices.size() + names.size());
        ordered.addAll(indices.values());
        ordered.addAll(names);
        return ordered;
    }

    /** Returns the numeric value when {@code key} is a JavaScript array index, else {@code null}. */
    private static Long asArrayIndex(String key) {
        int length = key.length();
        if (length == 0 || length > 10) {
            return null;
        }
        if (key.charAt(0) == '0' && length > 1) {
            return null; // "01" is not the canonical form of 1, so it is a plain name
        }
        long value = 0;
        for (int i = 0; i < length; i++) {
            char c = key.charAt(i);
            if (c < '0' || c > '9') {
                return null;
            }
            value = value * 10 + (c - '0');
        }
        return value <= MAX_ARRAY_INDEX ? value : null;
    }

    private static void writeNumber(Number number, StringBuilder out) {
        if (number instanceof Byte || number instanceof Short || number instanceof Integer
                || number instanceof Long || number instanceof BigInteger) {
            out.append(number.toString());
            return;
        }
        if (number instanceof BigDecimal) {
            out.append(((BigDecimal) number).stripTrailingZeros().toPlainString());
            return;
        }
        double d = number.doubleValue();
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            throw new IllegalArgumentException(
                    "NaN ve sonsuz değerin JSON karşılığı yok / NaN and infinity have no JSON form.");
        }
        // JavaScript prints an integral double without a fractional part ("25000",
        // not "25000.0"). Matching that keeps the digest identical for whole
        // numbers, which is what every amount field carries (minor units).
        if (d == Math.rint(d) && Math.abs(d) < 1e21) {
            out.append((long) d);
            return;
        }
        out.append(Double.toString(d));
    }

    private static void writeString(String value, StringBuilder out) {
        out.append('"');
        int length = value.length();
        for (int i = 0; i < length; i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\b':
                    out.append("\\b");
                    break;
                case '\f':
                    out.append("\\f");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        appendUnicodeEscape(c, out);
                    } else if (Character.isSurrogate(c) && !isPairedSurrogate(value, i, length)) {
                        // Well-formed JSON.stringify escapes an unpaired surrogate
                        // instead of emitting invalid UTF-8.
                        appendUnicodeEscape(c, out);
                    } else {
                        out.append(c);
                    }
                    break;
            }
        }
        out.append('"');
    }

    private static boolean isPairedSurrogate(String value, int index, int length) {
        char c = value.charAt(index);
        if (Character.isHighSurrogate(c)) {
            return index + 1 < length && Character.isLowSurrogate(value.charAt(index + 1));
        }
        return index > 0 && Character.isHighSurrogate(value.charAt(index - 1));
    }

    private static void appendUnicodeEscape(char c, StringBuilder out) {
        out.append("\\u");
        for (int shift = 12; shift >= 0; shift -= 4) {
            out.append("0123456789abcdef".charAt((c >> shift) & 0xF));
        }
    }

    // ───────────────────────────── reading ─────────────────────────────

    /**
     * Parses JSON text.
     *
     * @param text the JSON document
     * @return {@link LinkedHashMap}, {@link ArrayList}, {@link String},
     *         {@link Long}, {@link Double}, {@link Boolean} or {@code null}
     * @throws JsonParseException when the text is not valid JSON
     */
    public static Object parse(String text) {
        Parser parser = new Parser(text);
        parser.skipWhitespace();
        Object value = parser.readValue();
        parser.skipWhitespace();
        if (!parser.atEnd()) {
            throw new JsonParseException("JSON belgesinden sonra fazladan karakter var (konum " + parser.position + ").");
        }
        return value;
    }

    /**
     * Parses a JSON document that must be an object.
     *
     * @param text the JSON document
     * @return the parsed object, or an empty map when {@code text} is blank
     * @throws JsonParseException when the text is not a valid JSON object
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        if (text == null || text.isBlank()) {
            return new LinkedHashMap<>();
        }
        Object value = parse(text);
        if (!(value instanceof Map)) {
            throw new JsonParseException("JSON gövdesi bir nesne değil / JSON body is not an object.");
        }
        return (Map<String, Object>) value;
    }

    private static final class Parser {
        private final String text;
        private int position;
        private int depth;

        Parser(String text) {
            this.text = text == null ? "" : text;
        }

        boolean atEnd() {
            return position >= text.length();
        }

        void skipWhitespace() {
            while (position < text.length()) {
                char c = text.charAt(position);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    position++;
                } else {
                    return;
                }
            }
        }

        Object readValue() {
            if (++depth > 64) { throw new JsonParseException("JSON nesting exceeds the supported limit."); }
            try { return readLimitedValue(); } finally { depth--; }
        }

        private Object readLimitedValue() {
            if (atEnd()) {
                throw new JsonParseException("JSON belgesi beklenmedik şekilde bitti.");
            }
            char c = text.charAt(position);
            switch (c) {
                case '{':
                    return readObject();
                case '[':
                    return readArray();
                case '"':
                    return readString();
                case 't':
                    expect("true");
                    return Boolean.TRUE;
                case 'f':
                    expect("false");
                    return Boolean.FALSE;
                case 'n':
                    expect("null");
                    return null;
                default:
                    return readNumber();
            }
        }

        private Map<String, Object> readObject() {
            Map<String, Object> result = new LinkedHashMap<>();
            position++; // '{'
            skipWhitespace();
            if (!atEnd() && text.charAt(position) == '}') {
                position++;
                return result;
            }
            for (;;) {
                skipWhitespace();
                if (atEnd() || text.charAt(position) != '"') {
                    throw new JsonParseException("Nesne anahtarı beklendi (konum " + position + ").");
                }
                String key = readString();
                skipWhitespace();
                if (atEnd() || text.charAt(position) != ':') {
                    throw new JsonParseException("':' beklendi (konum " + position + ").");
                }
                position++;
                skipWhitespace();
                if (result.containsKey(key)) { throw new JsonParseException("Duplicate JSON object member."); }
                result.put(key, readValue());
                skipWhitespace();
                if (atEnd()) {
                    throw new JsonParseException("Nesne kapanmadı.");
                }
                char c = text.charAt(position++);
                if (c == '}') {
                    return result;
                }
                if (c != ',') {
                    throw new JsonParseException("',' ya da '}' beklendi (konum " + (position - 1) + ").");
                }
            }
        }

        private List<Object> readArray() {
            List<Object> result = new ArrayList<>();
            position++; // '['
            skipWhitespace();
            if (!atEnd() && text.charAt(position) == ']') {
                position++;
                return result;
            }
            for (;;) {
                skipWhitespace();
                result.add(readValue());
                skipWhitespace();
                if (atEnd()) {
                    throw new JsonParseException("Dizi kapanmadı.");
                }
                char c = text.charAt(position++);
                if (c == ']') {
                    return result;
                }
                if (c != ',') {
                    throw new JsonParseException("',' ya da ']' beklendi (konum " + (position - 1) + ").");
                }
            }
        }

        private String readString() {
            position++; // opening quote
            StringBuilder out = new StringBuilder();
            while (position < text.length()) {
                char c = text.charAt(position++);
                if (c == '"') {
                    return out.toString();
                }
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                if (position >= text.length()) {
                    break;
                }
                char escape = text.charAt(position++);
                switch (escape) {
                    case '"':
                        out.append('"');
                        break;
                    case '\\':
                        out.append('\\');
                        break;
                    case '/':
                        out.append('/');
                        break;
                    case 'b':
                        out.append('\b');
                        break;
                    case 'f':
                        out.append('\f');
                        break;
                    case 'n':
                        out.append('\n');
                        break;
                    case 'r':
                        out.append('\r');
                        break;
                    case 't':
                        out.append('\t');
                        break;
                    case 'u':
                        if (position + 4 > text.length()) {
                            throw new JsonParseException("Eksik \\u kaçışı (konum " + position + ").");
                        }
                        out.append((char) Integer.parseInt(text.substring(position, position + 4), 16));
                        position += 4;
                        break;
                    default:
                        throw new JsonParseException("Bilinmeyen kaçış dizisi '\\" + escape + "'.");
                }
            }
            throw new JsonParseException("Metin kapanmadı.");
        }

        private Number readNumber() {
            int start = position;
            if (!atEnd() && text.charAt(position) == '-') {
                position++;
            }
            boolean fractional = false;
            while (position < text.length()) {
                char c = text.charAt(position);
                if (c >= '0' && c <= '9') {
                    position++;
                } else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                    fractional = true;
                    position++;
                } else {
                    break;
                }
            }
            String literal = text.substring(start, position);
            if (literal.isEmpty() || "-".equals(literal)) {
                throw new JsonParseException("Sayı beklendi (konum " + start + ").");
            }
            try {
                if (!fractional) {
                    return Long.valueOf(literal);
                }
            } catch (NumberFormatException overflow) {
                return Double.valueOf(literal);
            }
            try {
                return Double.valueOf(literal);
            } catch (NumberFormatException invalid) {
                throw new JsonParseException("Geçersiz sayı '" + literal + "'.");
            }
        }

        private void expect(String literal) {
            if (!text.startsWith(literal, position)) {
                throw new JsonParseException("'" + literal + "' beklendi (konum " + position + ").");
            }
            position += literal.length();
        }
    }
}
