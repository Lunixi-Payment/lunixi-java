package com.lunixi.sdk.payment;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/** Exact decimal conversion. Gateway amounts are integer minor units, never floats. */
public final class Money {
    public static final long MAX = Long.MAX_VALUE;
    private Money() { }
    /** Strict wire decoder. JSON numbers are never accepted as public money. */
    public static long integer(Object value) {
        if (!(value instanceof String text) || !text.matches("0|[1-9][0-9]*")) { throw new IllegalArgumentException("Canonical minor-unit string required."); }
        try { return Long.parseLong(text); } catch (NumberFormatException error) { throw new IllegalArgumentException("Amount exceeds signed 64-bit fiat range.", error); }
    }
    /** Encode a trusted internal integer for a public API request. */
    public static String encode(long value) {
        if (value < 0) { throw new IllegalArgumentException("Nonnegative fiat amount required."); }
        return Long.toString(value);
    }
    public static String minor(String decimal, int exponent) {
        if (decimal == null || decimal.length() > 64 || !decimal.matches("(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?") || exponent < 0 || exponent > 3) { throw new IllegalArgumentException("Exact nonnegative decimal money is required."); }
        String text = new BigDecimal(decimal).movePointRight(exponent).setScale(0, RoundingMode.UNNECESSARY).toBigIntegerExact().toString();
        integer(text); return text;
    }
    public static String minor(String decimal, String currency) { return minor(decimal, exponent(currency)); }
    /** Exact integer persisted in the private commerce ledger; never used to decode public money. */
    static long storedInteger(Object value) {
        if (value instanceof Long n) { return n; }
        if (value instanceof Integer n) { return n.longValue(); }
        if (value instanceof BigInteger n) { return n.longValueExact(); }
        throw new IllegalStateException("Invalid internal ledger integer.");
    }
    /** Reject explicit null on optional financial mutation amounts before serialization can erase it. */
    public static void validateOperationAmount(String path, Object body) {
        if (path.matches("/api/v1/payments/(?:split/)?[^/]+/(?:capture|refund)(?:[?].*)?")
                && body instanceof java.util.Map<?, ?> map && map.containsKey("amount") && integer(map.get("amount")) < 1) {
            throw new IllegalArgumentException("Positive minor units are required.");
        }
    }
    public static void validateFields(Object value, boolean response) {
        if (value instanceof java.util.List<?> list) { list.forEach(entry -> validateFields(entry, response)); }
        if (!(value instanceof java.util.Map<?, ?> map)) { return; }
        for (var entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key) || entry.getValue() == null || java.util.Set.of("metadata", "fields", "inputFields", "insuranceData").contains(key)) { continue; }
            if (java.util.Set.of("amount", "amountMinor", "authorizedAmount", "availableBalance", "balanceMinor", "buyerTotalCharge", "cancelledPremiumAmount", "capturedAmount", "capturedAmountMinor", "chargeAmount", "collectedAmountMinor", "collectedPremiumAmount", "compensatedAmount", "disputedAmount", "feeTotal", "fixedFee", "inForcePremiumAmount", "insuranceValueAmount", "legAmount", "legsAmount", "lineTotalMinor", "maxAmount", "maxAmountMinor", "minAmount", "minAmountMinor", "nonRefundableAmount", "paidAmount", "paidPrice", "pendingBalance", "premiumAmount", "price", "refundAmount", "refundOpenPremiumAmount", "refundableAmount", "refundedAmount", "refundedAmountMinor", "refundedPremiumAmount", "requestedAmount", "reservedBalance", "sourceAmountMinor", "targetAmountMinor", "taxAmountMinor", "taxTotal", "totalAmount", "unitPriceMinor").contains(key)) {
                if (key.toLowerCase(java.util.Locale.ROOT).contains("balance")) {
                    if (!(entry.getValue() instanceof String text) || !text.matches("0|-?[1-9][0-9]*")) { throw new IllegalArgumentException("Canonical balance string required."); }
                    Long.parseLong(text);
                } else {
                    long amount = integer(entry.getValue());
                    if (!response && java.util.Set.of("amount", "price", "totalAmount").contains(key) && amount == 0) { throw new IllegalArgumentException("Positive amount required."); }
                }
            } else if (java.util.Set.of("data", "details", "items", "rows", "results", "payments", "operations", "balances", "wallets", "legs", "aggregate", "refund", "refunds", "totals", "charge", "fees", "fee", "balance", "splits", "sellers", "basketItems", "promissoryNote", "schedule", "notes", "lines", "offers", "policies", "reconciliation", "quotes", "payment", "transactions", "accounts", "settlements", "payouts", "limits").contains(key)) { validateFields(entry.getValue(), response); }
        }
    }
    public static int exponent(String currency) {
        if ("JPY".equals(currency)) { return 0; }
        if ("KWD".equals(currency)) { return 3; }
        if (java.util.Set.of("TRY", "USD", "EUR", "GBP", "RUB", "AED", "SAR", "QAR", "CHF", "CAD", "AUD", "CNY", "AZN").contains(currency)) { return 2; }
        throw new IllegalArgumentException("Unsupported gateway currency.");
    }
}
