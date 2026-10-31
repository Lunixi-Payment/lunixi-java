package com.lunixi.sdk.payment;

import java.util.regex.Pattern;

/** Browser token: return only to the authorized owner; never log this object or URL. */
public record CheckoutSession(String paymentId, String token, String paymentPageUrl) {
    private static final Pattern HOSTED_URL = Pattern.compile("https://pay\\.lunixi\\.com/payment/[A-Za-z0-9._~-]{16,4096}");
    /** Legacy responses remain usable for embedding; no hosted URL is guessed. */
    public CheckoutSession(String paymentId, String token) { this(paymentId, token, null); }
    public CheckoutSession {
        if (paymentId == null || paymentId.isBlank() || token == null || token.isBlank()) { throw new IllegalArgumentException("Uncertain checkout response: paymentId and token are required."); }
        if (paymentPageUrl != null && paymentPageUrl.isEmpty()) { paymentPageUrl = null; }
        if (paymentPageUrl != null) {
            if (!HOSTED_URL.matcher(paymentPageUrl).matches()) { throw new IllegalArgumentException("Invalid backend hosted checkout URL; reconcile the original attempt."); }
        }
    }
    /** Returns the exact validated backend URL, whose token may have another origin binding. */
    public String hostedUrl() {
        if (paymentPageUrl == null) { throw new IllegalStateException("Backend paymentPageUrl is unavailable; do not rebuild a URL from the embedded token or create another payment."); }
        return paymentPageUrl;
    }
    @Override public String toString() { return "CheckoutSession[paymentId=" + paymentId + ", token=[REDACTED], paymentPageUrl=[REDACTED]]"; }
}
