package com.lunixi.sdk.payment;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.lunixi.sdk.exception.ConfigurationException;

/**
 * Request checks of the payment-method endpoints, made before anything is sent: the method block structure, the
 * {@code direct/payment} body, the confirm body and shared value rules. Only shape is checked here; the provider's
 * field declarations ({@code inputFields}) are validated by the gateway. SDK internals.
 */
final class PaymentRules {

    static final int FIELDS_MAX_KEYS = 32;
    static final int FIELD_MAX_VALUE_LENGTH = 1024;
    static final int FIELD_MAX_GROUP_ITEMS = 10;
    static final int FIELD_MAX_NESTED_KEYS = 16;

    private static final Pattern FIELD_KEY = Pattern.compile("[A-Za-z][A-Za-z0-9]{0,63}");
    private static final Pattern PROVIDER_CODE = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final Pattern OTP = Pattern.compile("[0-9]{1,16}");
    private static final Pattern MINOR = Pattern.compile("0|[1-9][0-9]*");
    private static final Pattern CURRENCY = Pattern.compile("[A-Z]{3}");
    private static final Pattern OPERATION_KEY = Pattern.compile("[\\x21-\\x7e]{16,128}");
    private static final List<String> RESERVED_FIELD_KEYS = List.of("__proto__", "constructor", "prototype");
    private static final List<String> REQUIRED_DIRECT_FIELDS = List.of("paymentMethod", "amount", "currency",
            "orderId");
    static final int ORDER_ID_MAX_LENGTH = 128;

    private PaymentRules() {
    }

    // ── Shared value rules ────────────────────────────────────────────────────────────────────────────────────

    static boolean given(Object value) {
        return value != null;
    }

    /** A positive canonical minor-unit string ({@code "10000"}). */
    static String positiveMinor(Object value, String path) {
        if (!(value instanceof String text) || !MINOR.matcher(text).matches()) {
            throw new ConfigurationException(path + " must be a canonical minor-unit string such as \"10000\"; got "
                    + PaymentMethod.quote(value) + ".");
        }
        long amount;
        try {
            amount = Long.parseLong(text);
        } catch (NumberFormatException tooLarge) {
            throw new ConfigurationException(path + " exceeds the signed 64-bit minor-unit range.");
        }
        if (amount < 1) {
            throw new ConfigurationException(path + " must be at least 1 minor unit.");
        }
        return text;
    }

    static String text(Object value, String path) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw new ConfigurationException(path + " must be a non-empty string.");
        }
        return text;
    }

    static String boundedText(Object value, String path, int maxLength) {
        String text = text(value, path);
        if (text.length() > maxLength) {
            throw new ConfigurationException(path + " can be at most " + maxLength + " characters.");
        }
        return text;
    }

    static String currency(Object value) {
        if (!(value instanceof String text) || !CURRENCY.matcher(text).matches()) {
            throw new ConfigurationException("currency must be an ISO 4217 code such as TRY; got "
                    + PaymentMethod.quote(value) + ".");
        }
        return text;
    }

    static String httpsUrl(Object value, String path) {
        String text = boundedText(value, path, 2048);
        try {
            PaymentsClient.httpsUrl(text);
        } catch (IllegalArgumentException invalid) {
            throw new ConfigurationException(path + " must be an HTTPS URL without userinfo or fragment.");
        }
        return text;
    }

    static boolean bool(Object value, String path) {
        if (!(value instanceof Boolean flag)) {
            throw new ConfigurationException(path + " must be a boolean; got " + PaymentMethod.quote(value) + ".");
        }
        return flag;
    }

    /** The persisted {@code Idempotency-Key} of a financial write: 16–128 printable ASCII characters. */
    static String operationKey(String operation, String key) {
        if (key == null || !OPERATION_KEY.matcher(key).matches()) {
            throw new ConfigurationException("A stable Idempotency-Key is required for " + operation
                    + ": a persisted ASCII key of 16–128 characters. Reuse the same key when retrying the same "
                    + "operation.");
        }
        return key;
    }

    /** One URL path segment, encoded. */
    static String segment(String name, String value) {
        if (value == null || value.isBlank() || value.length() > 191 || value.equals(".") || value.equals("..")) {
            throw new ConfigurationException(name + " is required.");
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    // ── Method block structure ────────────────────────────────────────────────────────────────────────────────

    static String requireProviderCode(Object value) {
        if (!(value instanceof String text) || !PROVIDER_CODE.matcher(text).matches()) {
            throw new ConfigurationException("providerCode must be a provider code from "
                    + "payments().listPaymentMethods() (letters, digits, _ and -); got " + PaymentMethod.quote(value)
                    + ".");
        }
        return text;
    }

    /**
     * Structure of a method block or of confirm {@code fields}: a map of at most 32 fields whose names start with a
     * letter and hold only letters and digits; a value is a string (at most 1024 characters), a finite number or a
     * boolean; a map of such values ({@code ADDRESS}, at most 16 fields); or a list of at most 10 maps
     * ({@code GROUP}) whose values are scalars or maps. {@code providerCode} is refused: it is its own argument.
     */
    static Map<String, Object> requireMethodFields(Object value, String path) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new ConfigurationException(path + " must be a map of field values.");
        }
        if (map.size() > FIELDS_MAX_KEYS) {
            throw new ConfigurationException(path + " can carry at most " + FIELDS_MAX_KEYS + " fields.");
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if ("providerCode".equals(entry.getKey())) {
                throw new ConfigurationException(path + " must not carry providerCode; pass it as its own argument.");
            }
            String key = requireFieldKey(entry.getKey(), path);
            copy.put(key, requireFieldValue(entry.getValue(), path + "." + key, true));
        }
        return copy;
    }

    private static String requireFieldKey(Object key, String path) {
        if (!(key instanceof String text) || !FIELD_KEY.matcher(text).matches() || RESERVED_FIELD_KEYS.contains(text)) {
            throw new ConfigurationException(path + " has an invalid field name " + PaymentMethod.quote(key)
                    + ": field names start with a letter and contain only letters and digits.");
        }
        return text;
    }

    private static boolean isScalar(Object value) {
        if (value instanceof String text) {
            return text.length() <= FIELD_MAX_VALUE_LENGTH;
        }
        if (value instanceof Boolean) {
            return true;
        }
        if (value instanceof Double number) {
            return Double.isFinite(number);
        }
        if (value instanceof Float number) {
            return Float.isFinite(number);
        }
        return value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte
                || value instanceof BigInteger || value instanceof BigDecimal;
    }

    private static Map<String, Object> requireNestedObject(Map<?, ?> map, String path) {
        if (map.size() > FIELD_MAX_NESTED_KEYS) {
            throw new ConfigurationException(path + " can carry at most " + FIELD_MAX_NESTED_KEYS + " fields.");
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = requireFieldKey(entry.getKey(), path);
            if (!isScalar(entry.getValue())) {
                throw new ConfigurationException(path + "." + key + " must be a string (at most "
                        + FIELD_MAX_VALUE_LENGTH + " characters), a number or a boolean.");
            }
            copy.put(key, entry.getValue());
        }
        return copy;
    }

    private static Object requireFieldValue(Object value, String path, boolean allowList) {
        if (isScalar(value)) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            return requireNestedObject(map, path);
        }
        if (allowList && value instanceof List<?> list) {
            if (list.size() > FIELD_MAX_GROUP_ITEMS) {
                throw new ConfigurationException(path + " can carry at most " + FIELD_MAX_GROUP_ITEMS + " items.");
            }
            List<Object> items = new ArrayList<>(list.size());
            for (int index = 0; index < list.size(); index++) {
                String itemPath = path + "[" + index + "]";
                if (!(list.get(index) instanceof Map<?, ?> item)) {
                    throw new ConfigurationException(itemPath + " must be a map.");
                }
                if (item.size() > FIELDS_MAX_KEYS) {
                    throw new ConfigurationException(itemPath + " can carry at most " + FIELDS_MAX_KEYS + " fields.");
                }
                Map<String, Object> copy = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : item.entrySet()) {
                    String key = requireFieldKey(entry.getKey(), itemPath);
                    copy.put(key, requireFieldValue(entry.getValue(), itemPath + "." + key, false));
                }
                items.add(copy);
            }
            return items;
        }
        throw new ConfigurationException(path + " must be a string (at most " + FIELD_MAX_VALUE_LENGTH
                + " characters), a number, a boolean, a map or a list of maps.");
    }

    /** A shopping-loan payment needs the basket (1 to 100 items): the bank sets the loan term from it. */
    static void assertShoppingLoanBasket(String paymentMethod, Object basketItems) {
        if (!PaymentMethod.SHOPPING_LOAN.equals(paymentMethod)) {
            return;
        }
        if (!(basketItems instanceof List<?> items) || items.isEmpty()) {
            throw new ConfigurationException("paymentMethod SHOPPING_LOAN requires basketItems: the bank sets the "
                    + "loan term from the basket.");
        }
        if (items.size() > PaymentMethod.SHOPPING_LOAN_MAX_BASKET_ITEMS) {
            throw new ConfigurationException("paymentMethod SHOPPING_LOAN takes at most "
                    + PaymentMethod.SHOPPING_LOAN_MAX_BASKET_ITEMS + " basket items; got " + items.size() + ".");
        }
    }

    // ── direct/payment body ───────────────────────────────────────────────────────────────────────────────────

    /**
     * Checks a {@code POST /payments/direct/payment} body: no {@code price} / {@code paidPrice}, known fields only,
     * the required order fields, card fields only with {@code CARD}, exactly the chosen method's block (its
     * structure, {@code providerCode} pattern; none for {@code BANK_TRANSFER}), the promissory-note plan, the
     * shopping-loan basket and boolean options.
     */
    static void assertDirectMethodPaymentPayload(Map<String, Object> payload) {
        if (payload == null) {
            throw new ConfigurationException("A direct method payment needs a body. Use DirectMethodPaymentRequest "
                    + "to build it.");
        }
        for (String removed : List.of("price", "paidPrice")) {
            if (payload.containsKey(removed)) {
                throw new ConfigurationException(removed + ": removed from the payment body; send the amount only in "
                        + "'amount' (minor units, the basket total without the installment fee).");
            }
        }
        List<String> unknown = new ArrayList<>();
        for (Object key : payload.keySet()) {
            if (key == null || !PaymentMethod.DIRECT_METHOD_PAYMENT_FIELDS.contains(key)) {
                unknown.add(String.valueOf(key));
            }
        }
        if (!unknown.isEmpty()) {
            throw new ConfigurationException("Unknown direct method payment field(s): " + String.join(", ", unknown)
                    + ".");
        }
        for (String field : REQUIRED_DIRECT_FIELDS) {
            Object value = payload.get(field);
            if (value == null || "".equals(value)) {
                throw new ConfigurationException("Direct method payments require '" + field
                        + "'. Use DirectMethodPaymentRequest to build the request.");
            }
        }
        positiveMinor(payload.get("amount"), "amount");
        currency(payload.get("currency"));
        boundedText(payload.get("orderId"), "orderId", ORDER_ID_MAX_LENGTH);
        if (!(payload.get("paymentMethod") instanceof String code)) {
            throw new ConfigurationException("paymentMethod must be a payment method code string.");
        }
        String method = PaymentMethod.normalize(code);
        boolean card = PaymentMethod.CARD.equals(method);
        String ownBlock = card ? "card" : PaymentMethod.blockName(method);
        if (!card && !PaymentMethod.METHOD_BLOCK_FIELDS.contains(ownBlock)) {
            throw new ConfigurationException("paymentMethod " + method + " cannot be paid through direct/payment yet; "
                    + "payments().listPaymentMethods() lists the methods that can.");
        }
        if (!card) {
            for (String field : PaymentMethod.CARD_ONLY_FIELDS) {
                if (given(payload.get(field))) {
                    throw new ConfigurationException(field + " can only be sent with paymentMethod CARD.");
                }
            }
        }
        for (String field : PaymentMethod.METHOD_BLOCK_FIELDS) {
            if (!field.equals(ownBlock) && given(payload.get(field))) {
                throw new ConfigurationException("paymentMethod " + method + " takes only the '" + ownBlock
                        + "' block; '" + field + "' must not be sent.");
            }
        }
        Object block = payload.get(ownBlock);
        if (!(block instanceof Map<?, ?> blockMap) || (card && blockMap.isEmpty())) {
            throw new ConfigurationException("paymentMethod " + method + " requires the '" + ownBlock + "' block.");
        }
        if (!card) {
            assertRawMethodBlock(method, ownBlock, blockMap);
        }
        if (PaymentMethod.PROMISSORY_NOTE.equals(method)) {
            PromissoryNotePlan.assertBlock(blockMap, payload.get("amount"), ownBlock);
        }
        assertShoppingLoanBasket(method, payload.get("basketItems"));
        if (payload.containsKey("preauth")) {
            bool(payload.get("preauth"), "preauth");
        }
        if (payload.containsKey("force3D")) {
            bool(payload.get("force3D"), "force3D");
        }
        if (given(payload.get("callbackUrl"))) {
            httpsUrl(payload.get("callbackUrl"), "callbackUrl");
        }
    }

    /** {@code providerCode} (pattern; none for BANK_TRANSFER) and the structure of the other fields of a raw block. */
    private static void assertRawMethodBlock(String method, String blockName, Map<?, ?> block) {
        Map<Object, Object> fields = new LinkedHashMap<>(block);
        if (fields.containsKey("providerCode")) {
            if (PaymentMethod.BANK_TRANSFER.equals(method)) {
                throw new ConfigurationException("paymentMethod BANK_TRANSFER takes no providerCode in the "
                        + "bankTransfer block.");
            }
            requireProviderCode(fields.remove("providerCode"));
        }
        if (PaymentMethod.PROMISSORY_NOTE.equals(method)) {
            fields.remove("schedule");
        }
        requireMethodFields(fields, blockName);
    }

    // ── confirm body ──────────────────────────────────────────────────────────────────────────────────────────

    /** Body of {@code confirm}: only {@code otp} (digits, 1–16), {@code consentAccepted}, {@code fields}; at least one. */
    static Map<String, Object> confirmBody(Map<String, ?> input) {
        if (input == null) {
            throw new ConfigurationException("confirmPaymentAction input must be a map: { otp, consentAccepted, "
                    + "fields }.");
        }
        List<String> unknown = new ArrayList<>();
        for (Object key : input.keySet()) {
            if (key == null || !PaymentMethod.PAYMENT_ACTION_CONFIRM_FIELDS.contains(key)) {
                unknown.add(String.valueOf(key));
            }
        }
        if (!unknown.isEmpty()) {
            throw new ConfigurationException("Unknown confirm field(s): " + String.join(", ", unknown)
                    + ". A merchant server confirms with its signature and sends only "
                    + String.join(", ", PaymentMethod.PAYMENT_ACTION_CONFIRM_FIELDS) + ".");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        Object otp = input.get("otp");
        if (given(otp)) {
            if (!(otp instanceof String code) || !OTP.matcher(code).matches()) {
                throw new ConfigurationException("otp must be the code the buyer entered, as a string of 1 to 16 "
                        + "digits.");
            }
            body.put("otp", code);
        }
        Object consent = input.get("consentAccepted");
        if (given(consent)) {
            body.put("consentAccepted", bool(consent, "consentAccepted"));
        }
        Object fields = input.get("fields");
        if (given(fields)) {
            body.put("fields", requireMethodFields(fields, "fields"));
        }
        if (body.isEmpty()) {
            throw new ConfigurationException("confirmPaymentAction needs otp, consentAccepted or fields.");
        }
        return body;
    }
}
