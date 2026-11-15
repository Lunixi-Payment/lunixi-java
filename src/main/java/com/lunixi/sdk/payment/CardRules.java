package com.lunixi.sdk.payment;

import java.util.Map;
import java.util.regex.Pattern;

import com.lunixi.sdk.exception.ConfigurationException;

/**
 * Request checks of the card endpoints (BIN lookup, installment table, stored cards), made before anything is sent.
 * A card number or security code never appears in an error message. SDK internals.
 */
final class CardRules {

    private static final Pattern BIN_OR_PAN = Pattern.compile("[0-9]{6,19}");

    private CardRules() {
    }

    /** A 6- or 8-digit BIN or a card number prefix: 6–19 digits. The value is never echoed. */
    static String binOrPan(Object value) {
        if (!(value instanceof String text) || !BIN_OR_PAN.matcher(text).matches()) {
            throw new ConfigurationException("binOrPan must be 6 to 19 digits (a BIN or the card number, without "
                    + "spaces); the given value is not shown.");
        }
        return text;
    }

    /** An installment count of the installment table: 1–36. */
    static int installment(int count) {
        if (count < EnabledInstallments.MIN || count > EnabledInstallments.MAX) {
            throw new ConfigurationException("installment must be an integer from " + EnabledInstallments.MIN
                    + " to " + EnabledInstallments.MAX + "; got " + count + ".");
        }
        return count;
    }

    /** A card field map; its values are never echoed. */
    static Map<String, Object> card(Object value) {
        if (!(value instanceof Map<?, ?> map) || map.isEmpty()) {
            throw new ConfigurationException("card must be a non-empty map of card fields.");
        }
        Map<String, Object> card = new java.util.LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key) || key.isBlank()) {
                throw new ConfigurationException("card field names must be non-empty strings.");
            }
            card.put(key, entry.getValue());
        }
        return card;
    }

    /** Upper-cases a currency code and checks it. */
    static String currency(Object value) {
        Object code = value instanceof String text ? text.toUpperCase(java.util.Locale.ROOT) : value;
        return PaymentRules.currency(code);
    }
}
