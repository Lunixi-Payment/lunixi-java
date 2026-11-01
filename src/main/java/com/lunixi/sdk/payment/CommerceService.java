package com.lunixi.sdk.payment;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import com.lunixi.sdk.LunixiClient;
import com.lunixi.sdk.json.Json;
import com.lunixi.sdk.webhook.WebhookEvent;

/** Durable form checkout and GET-based reconciliation shared by native framework adapters. */
public final class CommerceService {
    private final LunixiClient client;
    private final LedgerStore store;
    private final SecretBox box;
    private final String context;
    public CommerceService(LunixiClient client, LedgerStore store, SecretBox box) { this.client = client; this.store = store; this.box = box; this.context = OrderSnapshot.hash(client.config().baseUrl() + "|" + client.config().environment().name()); }
    public CheckoutSession start(String orderKey, OrderSnapshot order, String callback, boolean hosted, boolean preauth, String locale) {
        PaymentsClient.httpsUrl(callback);
        Map<String, Object> proof = new LinkedHashMap<>(); proof.put("fingerprint", order.fingerprint());
        Map<String, Object> metadata = new LinkedHashMap<>(); metadata.put("lunixiCommerce", proof);
        if (hosted) { metadata.put("allowedOrigin", "https://pay.lunixi.com"); }
        Map<String, Object> request = new LinkedHashMap<>(); request.put("amount", order.amount()); request.put("currency", order.currency()); request.put("orderId", order.orderId()); request.put("callbackUrl", callback); request.put("preauth", preauth); request.put("locale", locale); request.put("metadata", metadata);
        String descriptor = OrderSnapshot.hash(Json.stringify(request));
        Map<String, Object> record = store.transaction(orderKey, access -> {
            Map<String, Object> previous = access.load();
            if (previous != null) {
                assertContext(previous);
                if (!descriptor.equals(previous.get("descriptor"))) { throw new IllegalStateException("Order or checkout settings changed; reconcile the existing attempt."); }
                return previous;
            }
            Map<String, Object> reserved = new LinkedHashMap<>(); reserved.put("snapshot", order.toMap()); reserved.put("context", context); reserved.put("descriptor", descriptor); reserved.put("key", "lunixi-" + UUID.randomUUID()); reserved.put("createdAt", Instant.now().getEpochSecond()); reserved.put("state", "reserved"); reserved.put("paymentId", ""); reserved.put("token", ""); reserved.put("captured", 0L); reserved.put("refunded", 0L); access.save(reserved); return reserved;
        });
        if (!java.util.Set.of("reserved", "pending").contains(record.get("state")) || Money.storedInteger(record.get("captured")) > 0) { throw new IllegalStateException("This attempt has concluded; reconcile it before another checkout."); }
        long age = Instant.now().getEpochSecond() - Money.storedInteger(record.get("createdAt"));
        if (!"".equals(record.get("paymentId")) && !"".equals(record.get("token"))) {
            if (age < 0 || age >= 840) { throw new IllegalStateException("Checkout token expired; reconcile before creating another attempt."); }
            Object storedHostedUrl = record.get("paymentPageUrl");
            if (storedHostedUrl != null && !(storedHostedUrl instanceof String)) { throw new IllegalStateException("Invalid persisted hosted checkout URL; reconcile the original attempt."); }
            String encryptedHostedUrl = (String) storedHostedUrl;
            String paymentPageUrl = encryptedHostedUrl != null && !encryptedHostedUrl.isEmpty() ? box.decrypt(encryptedHostedUrl) : null;
            return new CheckoutSession((String) record.get("paymentId"), box.decrypt((String) record.get("token")), paymentPageUrl);
        }
        if (age < 0 || age >= 23 * 3600) { throw new IllegalStateException("Financial replay window expired; reconcile without changing the key."); }
        // Map.of has randomized iteration order between JVMs. Financial retry
        // bytes must keep the same field order after a process restart.
        proof.put("attempt", OrderSnapshot.hash((String) record.get("key")));
        CheckoutSession session = client.payments().createIntent(request, (String) record.get("key"));
        store.transaction(orderKey, access -> {
            Map<String, Object> current = required(access.load());
            if (!record.get("key").equals(current.get("key")) || (!"".equals(current.get("paymentId")) && !session.paymentId().equals(current.get("paymentId")))) { throw new IllegalStateException("Concurrent attempt identity changed."); }
            current.put("paymentId", session.paymentId()); current.put("token", box.encrypt(session.token()));
            current.put("paymentPageUrl", session.paymentPageUrl() == null ? "" : box.encrypt(session.paymentPageUrl()));
            access.save(current); return null;
        });
        return session;
    }
    /** Event must come from verifyMerchant; rawBody is used only for persistent collision detection. */
    public PaymentDecision reconcile(String orderKey, WebhookEvent event, String rawBody, Consumer<PaymentDecision> applyNative) {
        if (event != null && (!event.isMerchantEnvelope() || !event.type().startsWith("payment.") || rawBody == null || !OrderSnapshot.hash(rawBody).equals(event.rawBodyHash()))) { throw new IllegalArgumentException("A strict payment event and its exact authenticated raw body are required."); }
        Map<String, Object> record = store.transaction(orderKey, access -> required(access.load()));
        assertContext(record);
        OrderSnapshot order = OrderSnapshot.from(PaymentEvidence.object(record.get("snapshot")));
        String bound = (String) record.get("paymentId"), candidate = bound;
        if (event != null) {
            candidate = eventPaymentId(event);
            if (!bound.isEmpty() && !bound.equals(candidate)) { throw new IllegalStateException("Event refers to another payment."); }
            if (rawBody == null) { throw new IllegalArgumentException("Original authenticated bytes are required for event receipt."); }
        }
        if (candidate.isEmpty()) { throw new IllegalStateException("A bound payment or authenticated recovery event is required."); }
        Map<String, Object> response = client.payments().get(candidate), payment = PaymentEvidence.data(response);
        if (bound.isEmpty()) {
            Map<String, Object> proof = PaymentEvidence.object(PaymentEvidence.metadata(payment).getOrDefault("lunixiCommerce", Map.of()));
            if (!order.fingerprint().equals(proof.get("fingerprint")) || !OrderSnapshot.hash((String) record.get("key")).equals(proof.get("attempt"))) { throw new IllegalStateException("Lost-response recovery requires the original opaque attempt binding."); }
        }
        Map<String, Object> split = null;
        if (payment.get("split") instanceof Map<?, ?> ref) { split = client.payments().getSplit((String) ref.get("splitPaymentId")); }
        PaymentDecision decision = PaymentEvidence.evaluate(order, candidate, response, split);
        String paymentId = candidate;
        return store.transaction(orderKey, access -> {
            Map<String, Object> current = required(access.load());
            if (!record.get("key").equals(current.get("key")) || (!"".equals(current.get("paymentId")) && !paymentId.equals(current.get("paymentId")))) { throw new IllegalStateException("Payment binding changed during reconciliation."); }
            if (decision.captured() < Money.storedInteger(current.get("captured")) || decision.refunded() < Money.storedInteger(current.get("refunded"))) { throw new IllegalStateException("Stale settlement evidence cannot downgrade an order."); }
            if (event != null && !access.claimEvent(order.merchantId(), event.id(), OrderSnapshot.hash(rawBody))) { return decision; }
            applyNative.accept(decision);
            current.put("paymentId", paymentId); current.put("captured", decision.captured()); current.put("refunded", decision.refunded()); current.put("state", decision.state()); access.save(current); return decision;
        });
    }
    public static String eventPaymentId(WebhookEvent event) {
        String candidate = null;
        for (String key : new String[]{"intentId", "paymentId", "paymentIntentId"}) {
            if (!event.data().containsKey(key)) { continue; }
            if (!(event.data().get(key) instanceof String id) || id.isBlank() || id.length() > 191 || (candidate != null && !candidate.equals(id))) { throw new IllegalStateException("Conflicting payment event identifiers."); }
            candidate = id;
        }
        if (candidate == null) { throw new IllegalStateException("A signed payment event identifier is required."); } return candidate;
    }
    private static Map<String, Object> required(Map<String, Object> value) { if (value == null) { throw new IllegalStateException("No durable payment reservation exists."); } return value; }
    private void assertContext(Map<String, Object> record) { if (!context.equals(record.get("context"))) { throw new IllegalStateException("The original gateway/environment profile is required for this attempt."); } }
}
