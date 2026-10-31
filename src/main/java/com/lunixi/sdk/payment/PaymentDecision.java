package com.lunixi.sdk.payment;

/** Authenticated settlement evidence. Authorized/partial capture must not fulfill an order. */
public record PaymentDecision(String state, String paymentId, long amount, long captured, long refunded, String splitId, String gatewayStatus) {
    public boolean isPaid() { return "paid".equals(state); }
}
