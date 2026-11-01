package com.lunixi.sdk.payment;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EvidenceTest {
    OrderSnapshot order = new OrderSnapshot("merchant", "order", "1990", "TRY");
    Map<String, Object> response(String status, long captured, long refunded) { return Map.of("data", Map.of("id", "pi", "merchantId", "merchant", "orderId", "order", "amount", "1990", "currency", "TRY", "status", status), "details", Map.of("capturedAmount", Long.toString(captured), "refundedAmount", Long.toString(refunded))); }
    @Test void onlyAuthenticatedFullCapturePaysAnOrder() {
        assertTrue(PaymentEvidence.evaluate(order, "pi", response("CAPTURED", 1990, 0), null).isPaid());
        assertFalse(PaymentEvidence.evaluate(order, "pi", response("AUTHORIZED", 0, 0), null).isPaid());
        assertFalse(PaymentEvidence.evaluate(order, "pi", response("PARTIALLY_CAPTURED", 990, 0), null).isPaid());
        assertEquals("review", PaymentEvidence.evaluate(order, "pi", response("FAILED", 0, 0), null).state());
        assertEquals("partially_refunded", PaymentEvidence.evaluate(order, "pi", response("PARTIALLY_REFUNDED", 1990, 990), null).state());
    }
    @Test void identitiesAndActualSettlementBalanceMustMatch() {
        assertThrows(IllegalStateException.class, () -> PaymentEvidence.evaluate(order, "other", response("CAPTURED", 1990, 0), null));
        assertThrows(IllegalStateException.class, () -> PaymentEvidence.evaluate(new OrderSnapshot("another", "order", "1990", "TRY"), "pi", response("CAPTURED", 1990, 0), null));
        assertThrows(IllegalStateException.class, () -> PaymentEvidence.evaluate(order, "pi", response("CAPTURED", 1990, 2000), null));
    }
    @Test void aSingleSplitLegOrUnreconciledParentCannotPayAnOrder() {
        var payment = new java.util.LinkedHashMap<>(PaymentEvidence.data(response("SETTLED_BY_SPLIT", 0, 0))); payment.put("split", Map.of("role", "LEG", "splitPaymentId", "split"));
        assertThrows(IllegalStateException.class, () -> PaymentEvidence.evaluate(order, "pi", Map.of("data", payment), Map.of()));
        payment.put("split", Map.of("role", "PARENT", "splitPaymentId", "split"));
        var aggregate = new java.util.LinkedHashMap<String, Object>(Map.of("totalAmount", "1990", "legsAmount", "1990", "amountsBalanced", true, "capturedAmount", "990", "compensatedAmount", "0"));
        var parent = Map.<String, Object>of("id", "split", "orderId", "order", "currency", "TRY", "needsReconciliation", false, "status", "PROCESSING", "aggregate", aggregate);
        assertFalse(PaymentEvidence.evaluate(order, "pi", Map.of("data", payment), parent).isPaid());
        aggregate.put("capturedAmount", "1990"); var completed = new java.util.LinkedHashMap<>(parent); completed.put("status", "COMPLETED");
        assertTrue(PaymentEvidence.evaluate(order, "pi", Map.of("data", payment), completed).isPaid());
        completed.put("needsReconciliation", true); assertThrows(IllegalStateException.class, () -> PaymentEvidence.evaluate(order, "pi", Map.of("data", payment), completed));
    }
    @Test void tokenStorageAuthenticatesCiphertextAndDoesNotExposePlaintext() {
        byte[] key = new byte[32]; new java.security.SecureRandom().nextBytes(key); SecretBox box = new SecretBox(key); String encrypted = box.encrypt("browser-secret");
        assertEquals("browser-secret", box.decrypt(encrypted)); assertFalse(encrypted.contains("browser-secret"));
        byte[] bytes = java.util.Base64.getDecoder().decode(encrypted); bytes[14] ^= 1;
        assertThrows(IllegalStateException.class, () -> box.decrypt(java.util.Base64.getEncoder().encodeToString(bytes)));
        assertThrows(IllegalArgumentException.class, () -> new SecretBox(new byte[16]));
    }
}
