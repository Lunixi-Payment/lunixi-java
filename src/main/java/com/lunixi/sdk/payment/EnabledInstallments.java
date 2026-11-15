package com.lunixi.sdk.payment;

import java.util.List;
import java.util.TreeSet;

import com.lunixi.sdk.exception.ConfigurationException;

/**
 * The {@code enabledInstallments} rule: the installment counts one payment may offer, as a non-empty list of
 * integers 1–36 without duplicates that contains 1 ({@code List.of(1)} = single payment only). Leaving the field
 * out means no restriction, so an empty list is refused (the gateway answers it with 400). Used by
 * {@link InstallmentOptionsRequest} and the payment link checkout.
 */
public final class EnabledInstallments {

    /** Smallest count (single payment). */
    public static final int MIN = 1;
    /** Largest count. */
    public static final int MAX = 36;

    private EnabledInstallments() {
    }

    /**
     * Checks a list and returns it in ascending order. A duplicate is an error, not removed.
     *
     * @param values the counts
     * @param field  the field name for the error message
     * @return an unmodifiable copy in ascending order
     * @throws ConfigurationException when the list breaks the rule
     */
    public static List<Integer> require(Object values, String field) {
        if (!(values instanceof List<?> counts) || counts.isEmpty()) {
            throw new ConfigurationException(field + " must be a non-empty list of installment counts; leave it out "
                    + "for no restriction, or send List.of(1) for a single payment only.");
        }
        TreeSet<Integer> seen = new TreeSet<>();
        for (Object entry : counts) {
            if (!(entry instanceof Integer count) || count < MIN || count > MAX) {
                throw new ConfigurationException(field + " values must be integers from " + MIN + " to " + MAX
                        + "; got " + PaymentMethod.quote(entry) + ".");
            }
            if (!seen.add(count)) {
                throw new ConfigurationException(field + " has " + count + " more than once.");
            }
        }
        if (!seen.contains(MIN)) {
            throw new ConfigurationException(field + " must contain 1 (single payment).");
        }
        return List.copyOf(seen);
    }
}
