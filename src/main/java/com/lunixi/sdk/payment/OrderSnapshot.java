package com.lunixi.sdk.payment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import com.lunixi.sdk.json.Json;

/**
 * Persisted server order totals, independent of browser fields.
 *
 * <p>{@code buyer}, {@code billingAddress}, {@code shippingAddress} (for a
 * {@code PHYSICAL} item) and {@code basketItems} are the order details every
 * payment requires ({@link OrderDetails}); {@link CommerceService#start} sends them
 * with the intent. Set them with {@link #withOrderDetails}. They are not part of
 * the {@link #fingerprint()}, {@link #toMap()}, equality or {@code toString()}.
 */
public record OrderSnapshot(
    String merchantId,
    String orderId,
    String amount,
    String currency,
    Map<String, Object> buyer,
    Map<String, Object> billingAddress,
    Map<String, Object> shippingAddress,
    List<Map<String, Object>> basketItems
) {
    public OrderSnapshot {
        if (merchantId == null || merchantId.isBlank() || orderId == null || orderId.isBlank() || Money.integer(amount) < 1) { throw new IllegalArgumentException("A merchant, order and positive amount are required."); }
        Money.exponent(currency);
        buyer = copy(buyer);
        billingAddress = copy(billingAddress);
        shippingAddress = copy(shippingAddress);
        basketItems = basketItems == null ? List.of() : basketItems.stream().map(OrderSnapshot::copy).toList();
    }

    /** The order identity and total; add the order details with {@link #withOrderDetails}. */
    public OrderSnapshot(String merchantId, String orderId, String amount, String currency) {
        this(merchantId, orderId, amount, currency, null, null, null, List.of());
    }

    /** The same order with its buyer, billing address, shipping address ({@code null} unless an item is {@code PHYSICAL}) and basket items. */
    public OrderSnapshot withOrderDetails(Map<String, Object> buyer, Map<String, Object> billingAddress, Map<String, Object> shippingAddress, List<Map<String, Object>> basketItems) {
        return new OrderSnapshot(merchantId, orderId, amount, currency, buyer, billingAddress, shippingAddress, basketItems);
    }

    /** The order details as body fields of the intent ({@code buyer}, {@code billingAddress}, {@code shippingAddress}, {@code basketItems}). */
    public Map<String, Object> orderDetails() {
        Map<String, Object> out = new LinkedHashMap<>();
        if (buyer != null) { out.put("buyer", buyer); }
        if (billingAddress != null) { out.put("billingAddress", billingAddress); }
        if (shippingAddress != null) { out.put("shippingAddress", shippingAddress); }
        if (!basketItems.isEmpty()) { out.put("basketItems", basketItems); }
        return out;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> out = new LinkedHashMap<>(); out.put("merchantId", merchantId); out.put("orderId", orderId); out.put("amount", amount); out.put("currency", currency); return out;
    }
    public static OrderSnapshot from(Map<String, Object> map) { return new OrderSnapshot((String) map.get("merchantId"), (String) map.get("orderId"), (String) map.get("amount"), (String) map.get("currency")); }
    public String fingerprint() { return hash(Json.stringify(toMap())); }
    public static String hash(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException error) { throw new IllegalStateException("SHA-256 is required.", error); }
    }

    /** Equality is the order identity and total; the order details do not take part. */
    @Override
    public boolean equals(Object other) {
        return other instanceof OrderSnapshot that && merchantId.equals(that.merchantId) && orderId.equals(that.orderId)
            && amount.equals(that.amount) && currency.equals(that.currency);
    }

    @Override
    public int hashCode() { return Objects.hash(merchantId, orderId, amount, currency); }

    /** Never shows the buyer or the addresses. */
    @Override
    public String toString() { return "OrderSnapshot[merchantId=" + merchantId + ", orderId=" + orderId + ", amount=" + amount + ", currency=" + currency + "]"; }

    /**
     * Fields in sorted order: a {@code Map.of} iterates differently in each JVM,
     * and a retried create must send byte-identical bodies after a restart.
     */
    private static Map<String, Object> copy(Map<String, Object> value) {
        return value == null ? null : Collections.unmodifiableMap(new TreeMap<>(value));
    }
}
