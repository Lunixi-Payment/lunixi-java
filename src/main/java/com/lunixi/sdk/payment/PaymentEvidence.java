package com.lunixi.sdk.payment;

import java.util.Map;
import java.util.Set;
import com.lunixi.sdk.json.Json;

/** Evaluate authenticated GET responses, never a submitted webhook/browser status. */
public final class PaymentEvidence {
    private PaymentEvidence() { }
    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(Object value) {
        if (!(value instanceof Map<?, ?> map) || map.keySet().stream().anyMatch(k -> !(k instanceof String))) { throw new IllegalStateException("Expected a JSON object."); }
        return (Map<String, Object>) value;
    }
    public static Map<String, Object> data(Map<String, Object> envelope) { return envelope.containsKey("data") ? object(envelope.get("data")) : envelope; }
    public static PaymentDecision evaluate(OrderSnapshot order, String paymentId, Map<String, Object> envelope, Map<String, Object> split) {
        Map<String, Object> payment = data(envelope);
        if (!paymentId.equals(payment.get("id")) || !order.merchantId().equals(payment.get("merchantId")) || !order.orderId().equals(payment.get("orderId")) || !order.currency().equals(payment.get("currency"))) { throw new IllegalStateException("Payment identity differs from the persisted order."); }
        long amount = Money.integer(payment.get("amount"));
        Map<String, Object> metadata = metadata(payment);
        if (amount < order.amount() || (amount != order.amount() && !order.fingerprint().equals(object(metadata.getOrDefault("lunixiCommerce", Map.of())).get("fingerprint")))) { throw new IllegalStateException("Payment amount differs from the persisted order."); }
        if (!(payment.get("status") instanceof String status)) { throw new IllegalStateException("Payment status is required."); }
        if (payment.get("split") instanceof Map || "SETTLED_BY_SPLIT".equals(status)) {
            Map<String, Object> ref = object(payment.get("split"));
            if (!"PARENT".equals(ref.get("role")) || !(ref.get("splitPaymentId") instanceof String splitId) || split == null) { throw new IllegalStateException("Split-parent evidence is required."); }
            Map<String, Object> parent = data(split), aggregate = object(parent.get("aggregate"));
            if (!splitId.equals(parent.get("id")) || !order.orderId().equals(parent.get("orderId")) || !order.currency().equals(parent.get("currency")) || !Boolean.FALSE.equals(parent.get("needsReconciliation")) || !Boolean.TRUE.equals(aggregate.get("amountsBalanced")) || Money.integer(aggregate.get("totalAmount")) != amount || Money.integer(aggregate.get("legsAmount")) != amount) { throw new IllegalStateException("Split requires reconciliation."); }
            long captured = Money.integer(aggregate.get("capturedAmount")), compensated = Money.integer(aggregate.get("compensatedAmount"));
            long refunded = Money.integer(object(parent.getOrDefault("refund", Map.of())).getOrDefault("refundedAmount", 0L));
            if (captured > amount || compensated > captured || refunded > captured) { throw new IllegalStateException("Invalid split settlement balance."); }
            String state = "pending";
            if ("SETTLED_BY_SPLIT".equals(status) && "COMPLETED".equals(parent.get("status")) && captured == amount && compensated == 0) { state = refunded == 0 ? "paid" : (refunded == captured ? "refunded" : "partially_refunded"); }
            if ("VOIDED".equals(parent.get("status")) && captured == 0 && compensated == 0 && Money.integer(aggregate.get("authorizedAmount")) == 0) { state = "failed"; }
            return new PaymentDecision(state, paymentId, amount, captured, refunded, splitId, status);
        }
        Map<String, Object> details = object(envelope.getOrDefault("details", payment.getOrDefault("details", Map.of())));
        long captured = Money.integer(details.get("capturedAmount")), refunded = Money.integer(details.get("refundedAmount"));
        if (captured > amount || refunded > captured) { throw new IllegalStateException("Invalid settlement balance."); }
        String state = "review";
        if ("CAPTURED".equals(status) && captured == amount && refunded == 0) { state = "paid"; }
        else if ("AUTHORIZED".equals(status) && captured == 0 && refunded == 0) { state = "authorized"; }
        else if ("PARTIALLY_CAPTURED".equals(status) && captured > 0 && captured < amount && refunded == 0) { state = "partially_captured"; }
        else if ("REFUNDED".equals(status) && captured > 0 && captured == refunded) { state = "refunded"; }
        else if ("PARTIALLY_REFUNDED".equals(status) && refunded > 0 && refunded < captured) { state = "partially_refunded"; }
        else if (Set.of("EXPIRED", "VOIDED").contains(status) && captured == 0) { state = "failed"; }
        else if (Set.of("INITIATED", "PROCESSING", "AWAITING_3D").contains(status) && captured == 0) { state = "pending"; }
        return new PaymentDecision(state, paymentId, amount, captured, refunded, null, status);
    }
    public static Map<String, Object> metadata(Map<String, Object> payment) {
        if (payment.get("metadata") instanceof Map) { return object(payment.get("metadata")); }
        if (payment.get("metadataJson") instanceof String text) { return Json.parseObject(text); }
        return Map.of();
    }
}
