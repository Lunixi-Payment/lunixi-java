package com.lunixi.sdk.payment;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Browser token: return only to the authorized owner; never log this object or URL. */
public record CheckoutSession(String paymentId, String token) {
    public CheckoutSession {
        if (paymentId == null || paymentId.isBlank() || token == null || token.isBlank()) { throw new IllegalArgumentException("Uncertain checkout response: paymentId and token are required."); }
    }
    public String hostedUrl() { return "https://pay.lunixi.com/payment/" + URLEncoder.encode(token, StandardCharsets.UTF_8).replace("+", "%20"); }
    @Override public String toString() { return "CheckoutSession[paymentId=" + paymentId + ", token=[REDACTED]]"; }
}
