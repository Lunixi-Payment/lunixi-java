package com.lunixi.sdk.marketplace;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.lunixi.sdk.exception.ConfigurationException;
import com.lunixi.sdk.internal.Checks;
import com.lunixi.sdk.payment.Money;

/**
 * Checks of the split charge bodies, made before sending: no removed amount field, a positive minor-unit
 * {@code amount}, a boolean {@code preauth}, canonical money members, and seller legs adding up to {@code amount}.
 * Error messages name the field, never its value: card data never appears in one. SDK internals.
 */
final class MarketplaceSplit {

    /** Amount fields the gateway removed from the payment bodies ({@code amount} is the only one). */
    private static final List<String> REMOVED_AMOUNT_FIELDS = List.of("price", "paidPrice");

    private MarketplaceSplit() {
    }

    /** The body of {@code chargeMarketplaceCard2d} / {@code 3d}: the commercial context is required. */
    static Map<String, Object> chargeBody(Map<String, ?> request, List<? extends Map<String, ?>> sellers,
                                          String multiSellerPolicy) {
        Map<String, Object> body = body(request, "marketplace charge request");
        List<String> missing = new ArrayList<>();
        for (String field : MarketplaceRoutes.CHARGE_REQUIRED_FIELDS) {
            if (!Checks.present(body.get(field))) {
                missing.add(field);
            }
        }
        if (!missing.isEmpty()) {
            throw new ConfigurationException("A marketplace card charge requires " + String.join(", ", missing) + ".");
        }
        if (!(body.get("basketItems") instanceof Collection<?> items) || items.isEmpty()) {
            throw new ConfigurationException("A marketplace card charge requires at least one basket item.");
        }
        return splitBody(body, sellers, multiSellerPolicy);
    }

    /** Adds the seller legs and the policy; the legs must add up to {@code amount}. */
    static Map<String, Object> splitBody(Map<String, Object> body, List<? extends Map<String, ?>> sellers,
                                         String multiSellerPolicy) {
        if (body.containsKey("sellers") || body.containsKey("multiSellerPolicy")) {
            throw new ConfigurationException("Pass the seller legs and multiSellerPolicy as arguments, not in the "
                    + "request body.");
        }
        assertChargeAmount(body);
        if (multiSellerPolicy == null || !MarketplaceValues.MultiSellerPolicy.ALL.contains(multiSellerPolicy)) {
            throw new ConfigurationException("multiSellerPolicy must be one of "
                    + String.join(", ", MarketplaceValues.MultiSellerPolicy.ALL) + ".");
        }
        if (sellers == null || sellers.isEmpty()) {
            throw new ConfigurationException("A marketplace split requires at least one seller leg.");
        }
        List<Map<String, Object>> legs = new ArrayList<>();
        BigInteger total = BigInteger.ZERO;
        for (int i = 0; i < sellers.size(); i++) {
            Map<String, Object> leg = sellerLeg(sellers.get(i), i);
            total = total.add(new BigInteger((String) leg.get("amountMinor")));
            legs.add(leg);
        }
        String amount = (String) body.get("amount");
        if (!total.equals(new BigInteger(amount))) {
            throw new ConfigurationException("The seller legs add up to " + total + ", not to amount " + amount
                    + "; the gateway refuses any other total.");
        }
        Map<String, Object> out = new LinkedHashMap<>(body);
        out.put("sellers", legs);
        out.put("multiSellerPolicy", multiSellerPolicy);
        return out;
    }

    private static Map<String, Object> sellerLeg(Object seller, int index) {
        String label = "sellers[" + index + "]";
        Map<String, Object> leg = Checks.plainMap(seller, label);
        List<String> unknown = new ArrayList<>();
        for (String key : leg.keySet()) {
            if (!MarketplaceRoutes.SELLER_FIELDS.contains(key)) {
                unknown.add(key);
            }
        }
        if (!unknown.isEmpty()) {
            throw new ConfigurationException(label + " does not accept " + String.join(", ", unknown) + ".");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("subDealerId", Checks.text(leg.get("subDealerId"), label + ".subDealerId"));
        out.put("amountMinor", Checks.minor(leg.get("amountMinor"), label + ".amountMinor", true));
        if (leg.containsKey("scenario")) {
            out.put("scenario", Checks.plainMap(leg.get("scenario"), label + ".scenario"));
        }
        return out;
    }

    /**
     * {@code amount} (the basket total) is a positive minor-unit string; {@code price} / {@code paidPrice} are gone;
     * {@code preauth} is a boolean; every money member of the payment body is a canonical minor-unit string.
     */
    static void assertChargeAmount(Map<String, ?> body) {
        List<String> removed = REMOVED_AMOUNT_FIELDS.stream().filter(body::containsKey).toList();
        if (!removed.isEmpty()) {
            throw new ConfigurationException(String.join(", ", removed) + ": removed from the payment body; send the "
                    + "amount only in 'amount' (minor units, the basket total).");
        }
        Checks.minor(body.get("amount"), "amount", true);
        if (body.containsKey("preauth") && !(body.get("preauth") instanceof Boolean)) {
            throw new ConfigurationException("preauth must be a boolean (true or false).");
        }
        try {
            Money.validateFields(body, false);
        } catch (IllegalArgumentException malformed) {
            throw new ConfigurationException("A money field of the marketplace charge is not a canonical minor-unit "
                    + "string: " + malformed.getMessage());
        }
    }

    static Map<String, Object> body(Map<String, ?> value, String label) {
        if (value == null) {
            throw new ConfigurationException(label + " must be an object (a map).");
        }
        return Checks.plainMap(value, label);
    }
}
