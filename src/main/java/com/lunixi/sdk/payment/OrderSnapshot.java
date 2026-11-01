package com.lunixi.sdk.payment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import com.lunixi.sdk.json.Json;

/** Persisted server order totals, independent of browser fields. */
public record OrderSnapshot(String merchantId, String orderId, String amount, String currency) {
    public OrderSnapshot {
        if (merchantId == null || merchantId.isBlank() || orderId == null || orderId.isBlank() || Money.integer(amount) < 1) { throw new IllegalArgumentException("A merchant, order and positive amount are required."); }
        Money.exponent(currency);
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
}
