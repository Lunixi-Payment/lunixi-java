package com.lunixi.sdk.payment;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import com.lunixi.sdk.exception.ConfigurationException;

/**
 * Checks of the marketplace intent body ({@code POST /api/v1/payments/marketplace/intents}), made before anything
 * is sent: no removed amount field, the order fields, and {@code sellers[]} whose {@code amountMinor} values add up
 * to {@code amount}. SDK internals.
 */
final class MarketplaceIntentRules {

    /** Amount fields the gateway removed from the payment bodies ({@code amount} is the only one). */
    static final List<String> REMOVED_AMOUNT_FIELDS = List.of("price", "paidPrice");

    private MarketplaceIntentRules() {
    }

    static void assertPayload(Map<String, ?> body) {
        if (body == null) {
            throw new ConfigurationException("A marketplace intent needs a body: amount, currency, orderId and "
                    + "sellers.");
        }
        List<String> removed = REMOVED_AMOUNT_FIELDS.stream().filter(body::containsKey).toList();
        if (!removed.isEmpty()) {
            throw new ConfigurationException(String.join(", ", removed) + ": removed from the payment body; send "
                    + "the amount only in 'amount' (minor units, the basket total without the installment fee).");
        }
        String amount = PaymentRules.positiveMinor(body.get("amount"), "amount");
        PaymentRules.currency(body.get("currency"));
        PaymentRules.boundedText(body.get("orderId"), "orderId", PaymentRules.ORDER_ID_MAX_LENGTH);
        if (body.containsKey("callbackUrl")) {
            PaymentRules.httpsUrl(body.get("callbackUrl"), "callbackUrl");
        }
        if (body.containsKey("preauth")) {
            PaymentRules.bool(body.get("preauth"), "preauth");
        }
        BigInteger total = sellersTotal(body.get("sellers"));
        if (!total.equals(new BigInteger(amount))) {
            throw new ConfigurationException("sellers[].amountMinor values add up to " + total + " but amount is "
                    + amount + "; they must be equal.");
        }
    }

    private static BigInteger sellersTotal(Object value) {
        if (!(value instanceof List<?> sellers) || sellers.isEmpty()) {
            throw new ConfigurationException("sellers must be a non-empty list of { subDealerId, amountMinor, "
                    + "scenario? }.");
        }
        BigInteger total = BigInteger.ZERO;
        for (int i = 0; i < sellers.size(); i++) {
            String path = "sellers[" + i + "]";
            if (!(sellers.get(i) instanceof Map<?, ?> seller)) {
                throw new ConfigurationException(path + " must be a map: { subDealerId, amountMinor, scenario? }.");
            }
            PaymentRules.text(seller.get("subDealerId"), path + ".subDealerId");
            total = total.add(new BigInteger(PaymentRules.positiveMinor(seller.get("amountMinor"),
                    path + ".amountMinor")));
        }
        return total;
    }
}
