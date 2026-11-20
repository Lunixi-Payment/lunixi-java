package com.lunixi.sdk.payment;

import java.math.BigInteger;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.lunixi.sdk.exception.ConfigurationException;

/**
 * Checks of a {@code POST /payments/split} body and of the {@code GET /payments/split} filters, made before
 * anything is sent. SDK internals.
 */
final class SplitPaymentRules {

    private static final List<String> CARD_LEG_ONLY_FIELDS = List.of("cardToken", "force3D",
            "installmentQuoteToken");
    private static final List<String> OWNER_SCOPES = List.of("MERCHANT_CUSTOMER");
    private static final int CARD_TOKEN_MAX_LENGTH = 256;
    private static final int QUOTE_TOKEN_MAX_LENGTH = 4096;
    private static final int LIST_MAX_VALUES = 20;
    private static final int FILTER_TEXT_MAX_LENGTH = 200;
    private static final int CURSOR_MAX_LENGTH = 2048;
    private static final Pattern ISO_8601 = Pattern.compile(
            "\\d{4}-\\d{2}-\\d{2}(T\\d{2}:\\d{2}(:\\d{2}(\\.\\d{1,9})?)?(Z|[+-]\\d{2}:?\\d{2})?)?");
    private static final Pattern CURRENCY = Pattern.compile("[A-Z]{3}");
    private static final Pattern HTTP_URL = Pattern.compile("(?i)https?://.*");

    private SplitPaymentRules() {
    }

    private static boolean isCardLeg(Map<?, ?> leg) {
        Object method = leg.get("paymentMethod");
        return method == null || "".equals(method) || PaymentMethod.CARD.equals(method);
    }

    private static void assertCardLeg(Map<?, ?> leg, String path) {
        PaymentRules.boundedText(leg.get("cardToken"), path + ".cardToken", CARD_TOKEN_MAX_LENGTH);
        for (String block : SplitPaymentValues.SPLIT_LEG_BLOCK_FIELDS) {
            if (leg.get(block) != null) {
                throw new ConfigurationException(path + " is a card leg and cannot carry the '" + block + "' block.");
            }
        }
        if (leg.get("force3D") != null) {
            PaymentRules.bool(leg.get("force3D"), path + ".force3D");
        }
        Object installment = leg.get("installment");
        if (installment != null && !isInstallment(installment)) {
            throw new ConfigurationException(path + ".installment must be an integer from 1 to "
                    + SplitPaymentValues.SPLIT_LEG_MAX_INSTALLMENT + "; got " + PaymentMethod.quote(installment) + ".");
        }
        if (leg.get("installmentQuoteToken") != null) {
            PaymentRules.boundedText(leg.get("installmentQuoteToken"), path + ".installmentQuoteToken",
                    QUOTE_TOKEN_MAX_LENGTH);
        }
        if (installment != null && ((Number) installment).longValue() >= 2 && leg.get("installmentQuoteToken") == null) {
            throw new ConfigurationException(path + ": an installment of 2 or more needs installmentQuoteToken "
                    + "(POST /bin/installments with this leg's amount).");
        }
    }

    private static boolean isInstallment(Object value) {
        if (!(value instanceof Integer) && !(value instanceof Long)) {
            return false;
        }
        long count = ((Number) value).longValue();
        return count >= 1 && count <= SplitPaymentValues.SPLIT_LEG_MAX_INSTALLMENT;
    }

    /** Returns the leg's {@code providerCode}. */
    private static String assertMethodLeg(Map<?, ?> leg, String path) {
        if (!(leg.get("paymentMethod") instanceof String code)) {
            throw new ConfigurationException(path + ".paymentMethod must be a payment method code string.");
        }
        String method = PaymentMethod.normalize(code);
        String blockName = PaymentMethod.blockName(method);
        if (!SplitPaymentValues.SPLIT_LEG_BLOCK_FIELDS.contains(blockName)) {
            throw new ConfigurationException(path + ": paymentMethod " + method + " cannot be a split payment leg; a "
                    + "non-card leg carries one of the blocks "
                    + String.join(", ", SplitPaymentValues.SPLIT_LEG_BLOCK_FIELDS) + ".");
        }
        for (String field : CARD_LEG_ONLY_FIELDS) {
            if (leg.get(field) != null) {
                throw new ConfigurationException(path + ": " + field + " can only be sent with a card leg.");
            }
        }
        Object installment = leg.get("installment");
        if (installment != null && !(isInstallment(installment) && ((Number) installment).longValue() == 1)) {
            throw new ConfigurationException(path + ": installment can only be sent with a card leg.");
        }
        for (String other : SplitPaymentValues.SPLIT_LEG_BLOCK_FIELDS) {
            if (!other.equals(blockName) && leg.get(other) != null) {
                throw new ConfigurationException(path + ": paymentMethod " + method + " takes only the '" + blockName
                        + "' block; '" + other + "' must not be sent.");
            }
        }
        if (!(leg.get(blockName) instanceof Map<?, ?> block)) {
            throw new ConfigurationException(path + ": paymentMethod " + method + " requires the '" + blockName
                    + "' block.");
        }
        Map<Object, Object> fields = new LinkedHashMap<>(block);
        Object providerCode = fields.remove("providerCode");
        if (providerCode == null) {
            throw new ConfigurationException(path + "." + blockName + ".providerCode is required in a split payment "
                    + "leg (a provider with splitEligible: true from payments().listPaymentMethods()).");
        }
        String provider = PaymentRules.requireProviderCode(providerCode);
        PaymentRules.requireMethodFields(fields, path + "." + blockName);
        if (PaymentMethod.PROMISSORY_NOTE.equals(method)) {
            if (fields.get("schedule") != null) {
                throw new ConfigurationException(path + "." + blockName + ".schedule cannot be sent in a split "
                        + "payment leg; give the number of notes with noteCount.");
            }
            PromissoryNotePlan.requireNoteCount(fields.get("noteCount"), path + "." + blockName + ".noteCount");
        }
        return provider;
    }

    /**
     * Known fields only, 1–5 legs, minor-unit amounts that add up to {@code totalAmount}, a card leg with
     * {@code cardToken} and no method block, a non-card leg with exactly its own block (with {@code providerCode})
     * and no card fields, one leg per provider and per card, {@code callbackUrl} when a card leg uses 3D Secure,
     * the order details ({@link OrderDetails}).
     */
    static void assertPayload(Map<String, Object> body) {
        if (body == null) {
            throw new ConfigurationException("A split payment needs a body.");
        }
        List<String> unknown = new ArrayList<>();
        for (Object key : body.keySet()) {
            if (key == null || !SplitPaymentValues.SPLIT_PAYMENT_FIELDS.contains(key)) {
                unknown.add(String.valueOf(key));
            }
        }
        if (!unknown.isEmpty()) {
            throw new ConfigurationException("Unknown split payment field(s): " + String.join(", ", unknown) + ".");
        }
        String totalAmount = PaymentRules.positiveMinor(body.get("totalAmount"), "totalAmount");
        PaymentRules.currency(body.get("currency"));
        PaymentRules.text(body.get("ownerRef"), "ownerRef");
        OrderDetails.assertComplete(body, "A split payment", true, false);
        if (body.get("orderId") != null) {
            PaymentRules.text(body.get("orderId"), "orderId");
        }
        Object ownerScope = body.get("ownerScope");
        if (ownerScope != null && !OWNER_SCOPES.contains(ownerScope)) {
            throw new ConfigurationException("ownerScope can only be " + String.join(", ", OWNER_SCOPES) + "; got "
                    + PaymentMethod.quote(ownerScope) + ".");
        }
        if (body.get("callbackUrl") != null) {
            String url = PaymentRules.boundedText(body.get("callbackUrl"), "callbackUrl", 2048);
            if (!HTTP_URL.matcher(url).matches()) {
                throw new ConfigurationException("callbackUrl must be an http(s) URL; got " + PaymentMethod.quote(url)
                        + ".");
            }
        }
        if (!(body.get("legs") instanceof List<?> legs) || legs.size() < SplitPaymentValues.SPLIT_MIN_LEGS
                || legs.size() > SplitPaymentValues.SPLIT_MAX_LEGS) {
            throw new ConfigurationException("legs must be a list of " + SplitPaymentValues.SPLIT_MIN_LEGS + " to "
                    + SplitPaymentValues.SPLIT_MAX_LEGS + " legs.");
        }
        Set<String> providers = new HashSet<>();
        Set<Object> cardTokens = new HashSet<>();
        BigInteger sum = BigInteger.ZERO;
        boolean threeDs = false;
        for (int index = 0; index < legs.size(); index++) {
            String path = "legs[" + index + "]";
            if (!(legs.get(index) instanceof Map<?, ?> leg)) {
                throw new ConfigurationException(path + " must be a map.");
            }
            List<String> unknownLeg = new ArrayList<>();
            for (Object key : leg.keySet()) {
                if (key == null || !SplitPaymentValues.SPLIT_PAYMENT_LEG_FIELDS.contains(key)) {
                    unknownLeg.add(String.valueOf(key));
                }
            }
            if (!unknownLeg.isEmpty()) {
                throw new ConfigurationException("Unknown " + path + " field(s): " + String.join(", ", unknownLeg)
                        + ". A non-card leg carries one of the blocks "
                        + String.join(", ", SplitPaymentValues.SPLIT_LEG_BLOCK_FIELDS) + ".");
            }
            sum = sum.add(new BigInteger(PaymentRules.positiveMinor(leg.get("amount"), path + ".amount")));
            threeDs |= Boolean.TRUE.equals(leg.get("force3D"));
            if (isCardLeg(leg)) {
                assertCardLeg(leg, path);
                if (!cardTokens.add(leg.get("cardToken"))) {
                    throw new ConfigurationException(path + ": the same card is used in more than one leg.");
                }
                continue;
            }
            String provider = assertMethodLeg(leg, path);
            if (!providers.add(provider)) {
                throw new ConfigurationException(path + ": provider " + provider + " is used in more than one leg; "
                        + "one leg per provider.");
            }
        }
        if (!sum.equals(new BigInteger(totalAmount))) {
            throw new ConfigurationException("The legs add up to " + sum + " but totalAmount is " + totalAmount
                    + "; they must be equal.");
        }
        if (threeDs && body.get("callbackUrl") == null) {
            throw new ConfigurationException("callbackUrl is required when a card leg uses 3D Secure (force3D: true).");
        }
    }

    /**
     * Query string of {@code GET /payments/split} (without {@code ?}). {@code status} and {@code currency} may be a
     * value or a list; a list is sent as repeated parameters ({@code status=COMPLETED&status=VOIDED}).
     */
    static String listQuery(Map<String, ?> filters) {
        if (filters == null || filters.isEmpty()) {
            return "";
        }
        List<String> unknown = new ArrayList<>();
        for (Object key : filters.keySet()) {
            if (key == null || !SplitPaymentValues.LIST_SPLIT_PAYMENTS_FILTERS.contains(key)) {
                unknown.add(String.valueOf(key));
            }
        }
        if (!unknown.isEmpty()) {
            throw new ConfigurationException("listSplitPayments does not take " + String.join(", ", unknown)
                    + "; filters: " + String.join(", ", SplitPaymentValues.LIST_SPLIT_PAYMENTS_FILTERS) + ".");
        }
        List<String[]> pairs = new ArrayList<>();
        for (String key : SplitPaymentValues.LIST_SPLIT_PAYMENTS_FILTERS) {
            Object value = filters.get(key);
            if (value == null || "".equals(value)) {
                continue;
            }
            switch (key) {
                case "status" -> {
                    for (Object entry : filterValues(value, key)) {
                        if (!(entry instanceof String status) || !SplitPaymentValues.Status.ALL.contains(status)) {
                            throw new ConfigurationException("status must be one of "
                                    + String.join(", ", SplitPaymentValues.Status.ALL) + "; got "
                                    + PaymentMethod.quote(entry) + ".");
                        }
                        pairs.add(new String[]{key, status});
                    }
                }
                case "currency" -> {
                    for (Object entry : filterValues(value, key)) {
                        if (!(entry instanceof String code) || !CURRENCY.matcher(code).matches()) {
                            throw new ConfigurationException("currency must be an ISO 4217 code; got "
                                    + PaymentMethod.quote(entry) + ".");
                        }
                        pairs.add(new String[]{key, code});
                    }
                }
                case "createdFrom", "createdTo" -> {
                    if (!(value instanceof String date) || !ISO_8601.matcher(date).matches()) {
                        throw new ConfigurationException(key + " must be an ISO 8601 date-time; got "
                                + PaymentMethod.quote(value) + ".");
                    }
                    pairs.add(new String[]{key, date});
                }
                case "limit" -> {
                    if (!(value instanceof Integer || value instanceof Long)
                            || ((Number) value).longValue() < 1
                            || ((Number) value).longValue() > SplitPaymentValues.SPLIT_LIST_MAX_LIMIT) {
                        throw new ConfigurationException("limit must be an integer from 1 to "
                                + SplitPaymentValues.SPLIT_LIST_MAX_LIMIT + "; got " + PaymentMethod.quote(value) + ".");
                    }
                    pairs.add(new String[]{key, String.valueOf(value)});
                }
                default -> pairs.add(new String[]{key, PaymentRules.boundedText(value, key,
                        "after".equals(key) ? CURSOR_MAX_LENGTH : FILTER_TEXT_MAX_LENGTH)});
            }
        }
        StringBuilder query = new StringBuilder();
        for (String[] pair : pairs) {
            if (query.length() > 0) {
                query.append('&');
            }
            query.append(encode(pair[0])).append('=').append(encode(pair[1]));
        }
        return query.toString();
    }

    private static List<?> filterValues(Object value, String field) {
        List<?> values = value instanceof List<?> list ? list : List.of(value);
        if (values.isEmpty() || values.size() > LIST_MAX_VALUES) {
            throw new ConfigurationException(field + " takes 1 to " + LIST_MAX_VALUES + " values.");
        }
        return values;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
