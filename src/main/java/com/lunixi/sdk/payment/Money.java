package com.lunixi.sdk.payment;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/** Exact decimal conversion. Gateway amounts are integer minor units, never floats. */
public final class Money {
    public static final long MAX = 9_007_199_254_740_991L;
    private Money() { }
    public static long integer(Object value) {
        String text;
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long || value instanceof BigInteger || value instanceof String) { text = value.toString(); }
        else { throw new IllegalArgumentException("Integer minor units are required."); }
        if (!text.matches("0|[1-9][0-9]{0,15}")) { throw new IllegalArgumentException("Invalid minor units."); }
        long amount = Long.parseLong(text);
        if (amount > MAX) { throw new IllegalArgumentException("Amount exceeds gateway safe integer range."); }
        return amount;
    }
    public static long minor(String decimal, int exponent) {
        if (decimal == null || decimal.length() > 64 || !decimal.matches("(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?") || exponent < 0 || exponent > 3) { throw new IllegalArgumentException("Exact nonnegative decimal money is required."); }
        return integer(new BigDecimal(decimal).movePointRight(exponent).setScale(0, RoundingMode.UNNECESSARY).toBigIntegerExact());
    }
    public static int exponent(String currency) {
        if ("JPY".equals(currency)) { return 0; }
        if ("KWD".equals(currency)) { return 3; }
        if (java.util.Set.of("TRY", "USD", "EUR", "GBP", "RUB", "AED", "SAR", "QAR", "CHF", "CAD", "AUD", "CNY", "AZN").contains(currency)) { return 2; }
        throw new IllegalArgumentException("Unsupported gateway currency.");
    }
}
