package com.lunixi.sdk.payment;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import com.lunixi.sdk.http.Envelope;
import com.lunixi.sdk.internal.ApiClient;
import com.lunixi.sdk.internal.RequestOptions;

/** Server payment endpoints. Persist the exact request and operation key BEFORE sending. */
public final class PaymentsClient {
    private static final String BASE = "/api/v1/payments";
    private final ApiClient api;
    public PaymentsClient(ApiClient api) { this.api = api; }
    public CheckoutSession createIntent(Map<String, Object> request, String idempotencyKey) {
        if (request != null && (request.containsKey("price") || request.containsKey("paidPrice"))) { throw new IllegalArgumentException("Removed top-level amount field; use integer minor-unit amount."); }
        if (request == null || Money.integer(request.get("amount")) < 1 || !(request.get("orderId") instanceof String order) || order.isBlank() || !(request.get("currency") instanceof String currency)) { throw new IllegalArgumentException("Server order, positive amount and currency are required."); }
        Money.exponent(currency);
        if (request.containsKey("callbackUrl")) { httpsUrl(String.valueOf(request.get("callbackUrl"))); }
        Map<String, Object> result = Envelope.data(api.request("POST", BASE + "/intents", request, mutation(idempotencyKey)));
        String id = text(result.get("paymentId"));
        for (String alias : new String[]{"intentId", "id"}) { if (result.containsKey(alias) && !id.equals(result.get(alias))) { throw new IllegalStateException("Conflicting checkout response identities; reconcile the persisted attempt."); } }
        Object paymentPageUrl = result.get("paymentPageUrl");
        if (paymentPageUrl != null && !(paymentPageUrl instanceof String)) { throw new IllegalStateException("Uncertain checkout response: paymentPageUrl must be a string when present."); }
        return new CheckoutSession(id, text(result.get("token")), (String) paymentPageUrl);
    }
    /** Full envelope is retained because settlement balances are in details beside data. */
    public Map<String, Object> get(String paymentId) { return api.request("GET", BASE + "/" + segment(paymentId), null, RequestOptions.none()); }
    public Map<String, Object> getSplit(String splitId) { return api.request("GET", BASE + "/split/" + segment(splitId), null, RequestOptions.none()); }
    /** Capture an explicit positive amount. Null is rejected; use captureRemaining for full capture. */
    public Map<String, Object> capture(String id, String amount, String key) { positiveAmount(amount); return financial(id, "capture", amount, null, key); }
    /** Capture the remaining authorization without an amount field. */
    public Map<String, Object> captureRemaining(String id, String key) { return financial(id, "capture", null, null, key); }
    /** Refund an explicit positive amount. Null is rejected; use refundRemaining for the remaining balance. */
    public Map<String, Object> refund(String id, String amount, String reason, String key) { positiveAmount(amount); return financial(id, "refund", amount, reason, key); }
    /** Refund the remaining balance without an amount field. */
    public Map<String, Object> refundRemaining(String id, String reason, String key) { return financial(id, "refund", null, reason, key); }
    public Map<String, Object> voidPayment(String id, String key) { return api.request("POST", BASE + "/" + segment(id) + "/void", null, mutation(key)); }
    public Map<String, Object> refundSplit(String id, String amount, String reason, String key) {
        if (amount == null) { throw new IllegalArgumentException("A split refund amount is required."); }
        Map<String, Object> body = amountBody(amount, reason);
        return api.request("POST", BASE + "/split/" + segment(id) + "/refund", body, mutation(key));
    }
    private Map<String, Object> financial(String id, String action, String amount, String reason, String key) {
        Map<String, Object> body = amountBody(amount, reason); body.put("paymentIntentId", id);
        return api.request("POST", BASE + "/" + segment(id) + "/" + action, body, mutation(key));
    }
    private static void positiveAmount(String amount) {
        if (Money.integer(amount) < 1) { throw new IllegalArgumentException("Positive minor units are required."); }
    }
    private static Map<String, Object> amountBody(String amount, String reason) {
        if (amount != null && Money.integer(amount) < 1) { throw new IllegalArgumentException("Positive minor units are required."); }
        Map<String, Object> body = new LinkedHashMap<>(); if (amount != null) { body.put("amount", amount); }
        if (reason != null && !reason.isBlank()) { body.put("reason", reason); }
        return body;
    }
    private static RequestOptions mutation(String key) {
        if (key == null || !key.matches("[\\x21-\\x7e]{16,128}")) { throw new IllegalArgumentException("A persisted ASCII idempotency key (16–128 characters) is required."); }
        return RequestOptions.builder().stepUp(true).idempotencyKey(key).build();
    }
    private static String segment(String id) {
        if (id == null || id.isBlank() || id.length() > 191 || id.equals(".") || id.equals("..")) { throw new IllegalArgumentException("A valid resource ID is required."); }
        return URLEncoder.encode(id, StandardCharsets.UTF_8).replace("+", "%20");
    }
    private static String text(Object value) { return value instanceof String ? (String) value : ""; }
    public static void httpsUrl(String value) {
        URI uri;
        try { uri = URI.create(value); } catch (RuntimeException error) { throw new IllegalArgumentException("An HTTPS URL is required."); }
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null) { throw new IllegalArgumentException("An HTTPS URL without userinfo/fragment is required."); }
    }
}
