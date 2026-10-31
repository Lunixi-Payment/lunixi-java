package com.lunixi.sdk.payment;

import static org.junit.jupiter.api.Assertions.*;

import com.lunixi.sdk.LunixiClient;
import com.lunixi.sdk.http.HttpResult;
import com.lunixi.sdk.http.HttpTransport;
import com.lunixi.sdk.json.Json;
import com.lunixi.sdk.webhook.WebhookEvent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Independent financial boundaries: authenticated totals are necessary, native application remains atomic. */
class PaymentBoundaryReviewTest {
    @TempDir Path directory;
    private final OrderSnapshot order = new OrderSnapshot("merchant", "order", 1990, "TRY");

    private Map<String, Object> response(String status, Object amount, Object captured, Object refunded) {
        Map<String, Object> payment = new LinkedHashMap<>();
        payment.put("id", "pi"); payment.put("merchantId", "merchant"); payment.put("orderId", "order");
        payment.put("currency", "TRY"); payment.put("amount", amount); payment.put("status", status);
        return Map.of("data", payment, "details", Map.of("capturedAmount", captured, "refundedAmount", refunded));
    }

    @Test void installmentGrossRequiresOriginalBaseOrderFingerprint() {
        Map<String, Object> response = response("CAPTURED", 2090L, "2090", "0");
        assertThrows(IllegalStateException.class, () -> PaymentEvidence.evaluate(order, "pi", response, null));
        Map<String, Object> payment = PaymentEvidence.data(response);
        payment.put("metadata", Map.of("lunixiCommerce", Map.of("fingerprint", "unrelated-order")));
        assertThrows(IllegalStateException.class, () -> PaymentEvidence.evaluate(order, "pi", response, null));
        payment.put("metadataJson", Json.stringify(Map.of("lunixiCommerce", Map.of("fingerprint", order.fingerprint()))));
        payment.remove("metadata");
        PaymentDecision decision = PaymentEvidence.evaluate(order, "pi", response, null);
        assertTrue(decision.isPaid()); assertEquals(2090, decision.amount()); assertEquals(1990, order.amount());
    }

    @Test void authenticatedSettlementCannotUseFloatingPointOrImpossibleRefundBalances() {
        assertThrows(IllegalArgumentException.class, () -> PaymentEvidence.evaluate(order, "pi", response("CAPTURED", 1990L, 1990.0, "0"), null));
        assertThrows(IllegalArgumentException.class, () -> PaymentEvidence.evaluate(order, "pi", response("CAPTURED", 1990L, "1990", "-1"), null));
        assertThrows(IllegalStateException.class, () -> PaymentEvidence.evaluate(order, "pi", response("REFUNDED", 1990L, "990", "1990"), null));
        assertEquals("review", PaymentEvidence.evaluate(order, "pi", response("CAPTURED", 1990L, "1990", "1"), null).state());
    }

    @Test void aCompletedButCompensatedSplitCannotFulfillAnOrder() {
        Map<String, Object> response = response("SETTLED_BY_SPLIT", 1990L, "0", "0");
        PaymentEvidence.data(response).put("split", Map.of("role", "PARENT", "splitPaymentId", "split"));
        Map<String, Object> aggregate = new LinkedHashMap<>(Map.of("totalAmount", 1990L, "legsAmount", 1990L,
                "amountsBalanced", true, "capturedAmount", 1990L, "compensatedAmount", 1L));
        Map<String, Object> parent = Map.of("id", "split", "orderId", "order", "currency", "TRY",
                "status", "COMPLETED", "needsReconciliation", false, "aggregate", aggregate);
        assertFalse(PaymentEvidence.evaluate(order, "pi", response, parent).isPaid());
        aggregate.put("compensatedAmount", 0L);
        assertTrue(PaymentEvidence.evaluate(order, "pi", response, parent).isPaid());
        aggregate.put("legsAmount", 1989L);
        assertThrows(IllegalStateException.class, () -> PaymentEvidence.evaluate(order, "pi", response, parent));
    }

    @Test void presentMalformedIdentifierAliasesFailClosedEvenWhenAnotherAliasIsValid() throws Exception {
        LunixiClient client = DurableCommerceFixture.client((method, url, headers, body, timeout) -> {
            throw new AssertionError("Identifier validation must not call a gateway.");
        });
        for (Object invalid : new Object[]{123L, Boolean.TRUE, Map.of("id", "another"), ""}) {
            Map<String, Object> data = new LinkedHashMap<>(); data.put("intentId", "pi_valid"); data.put("paymentId", invalid);
            WebhookEvent event = signed(client, data);
            assertThrows(IllegalStateException.class, () -> CommerceService.eventPaymentId(event));
        }
        Map<String, Object> nullAlias = new LinkedHashMap<>(); nullAlias.put("intentId", "pi_valid"); nullAlias.put("paymentId", null);
        WebhookEvent event = signed(client, nullAlias);
        assertThrows(IllegalStateException.class, () -> CommerceService.eventPaymentId(event));
    }

    @Test void authenticatedStaleGetCannotReducePersistedSettlementOrApplyNativeAgain() {
        DurableCommerceFixture.FileLedger store = new DurableCommerceFixture.FileLedger(directory.resolve("ledger.json"));
        DurableCommerceFixture.Gateway gateway = new DurableCommerceFixture.Gateway(store, false);
        AtomicBoolean stale = new AtomicBoolean();
        HttpTransport transport = (method, url, headers, body, timeout) -> {
            HttpResult result = gateway.send(method, url, headers, body, timeout);
            if (stale.get() && method.equals("GET")) {
                Map<String, Object> response = Json.parseObject(result.body());
                PaymentEvidence.data(response).put("status", "PARTIALLY_CAPTURED");
                response.put("details", Map.of("capturedAmount", "990", "refundedAmount", "0"));
                return new HttpResult(200, Map.of(), Json.stringify(response));
            }
            return result;
        };
        CommerceService commerce = new CommerceService(DurableCommerceFixture.client(transport), store, DurableCommerceFixture.box());
        commerce.start(DurableCommerceFixture.ORDER, DurableCommerceFixture.SNAPSHOT, "https://shop.invalid/return", true, false, "en");
        assertTrue(commerce.reconcile(DurableCommerceFixture.ORDER, null, null, store::applyNative).isPaid());
        stale.set(true);
        assertThrows(IllegalStateException.class, () -> commerce.reconcile(DurableCommerceFixture.ORDER, null, null, store::applyNative));
        assertEquals(1, store.applications()); assertEquals("paid", store.record().get("state"));
        assertEquals(1990L, store.record().get("captured"));
    }

    @Test void futureDatedReservationCannotReplayAFinancialPost() {
        DurableCommerceFixture.FileLedger store = new DurableCommerceFixture.FileLedger(directory.resolve("future.json"));
        DurableCommerceFixture.Gateway gateway = new DurableCommerceFixture.Gateway(store, true);
        CommerceService commerce = new CommerceService(DurableCommerceFixture.client(gateway), store, DurableCommerceFixture.box());
        assertThrows(RuntimeException.class, () -> commerce.start(DurableCommerceFixture.ORDER, DurableCommerceFixture.SNAPSHOT, "https://shop.invalid/return", true, false, "en"));
        String key = (String) store.record().get("key");
        store.edit(record -> record.put("createdAt", Instant.now().getEpochSecond() + 60));
        assertThrows(IllegalStateException.class, () -> commerce.start(DurableCommerceFixture.ORDER, DurableCommerceFixture.SNAPSHOT, "https://shop.invalid/return", true, false, "en"));
        assertEquals(1, gateway.bodies.size()); assertEquals(key, store.record().get("key"));
    }

    private static WebhookEvent signed(LunixiClient client, Map<String, Object> data) throws Exception {
        String body = Json.stringify(Map.of("id", "evt_alias_review", "type", "payment.captured", "data", data));
        String timestamp = Instant.now().toString();
        Mac hmac = Mac.getInstance("HmacSHA256");
        hmac.init(new SecretKeySpec(DurableCommerceFixture.SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = HexFormat.of().formatHex(hmac.doFinal(("evt_alias_review." + timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
        return client.webhooks().verifyMerchant(body, Map.of("x-lunixi-event-id", "evt_alias_review", "x-lunixi-signature-timestamp", timestamp,
                "x-lunixi-signature", "v2=" + signature), DurableCommerceFixture.SECRET);
    }
}
