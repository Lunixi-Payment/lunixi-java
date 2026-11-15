package com.lunixi.sdk.payment;

import java.util.List;

/**
 * Webhook event types of a split payment. The result of the main payment arrives as {@code payment.captured} /
 * {@code payment.failed} with {@code settlementMode: SPLIT} and {@code legs[]}; deliver the order on that event.
 */
public final class SplitPaymentEvents {

    public static final String COMPLETED = "payment.split.completed";
    public static final String VOIDED = "payment.split.voided";
    public static final String COMPENSATING = "payment.split.compensating";
    /** The non-reversible leg's result is being confirmed; card holds are kept until then. */
    public static final String OUTCOME_PENDING = "payment.split.outcome_pending";
    /** The order could not be fully collected ({@code shortfallAmountMinor}, {@code reason}). */
    public static final String RECONCILIATION_REQUIRED = "payment.split.reconciliation_required";
    /** Leg event: a charged leg is being refunded and retried until it succeeds. */
    public static final String LEG_REFUND_PENDING = "payment.split.leg_refund_pending";

    /**
     * Put this key into a webhook endpoint's event types to also receive the events of each leg. It is not an
     * event type and {@code payment.*} does not include it.
     */
    public static final String LEG_EVENTS_SUBSCRIPTION = "payment.split.leg_events";

    /** Every event type (without {@link #LEG_EVENTS_SUBSCRIPTION}). */
    public static final List<String> ALL = List.of(COMPLETED, VOIDED, COMPENSATING, OUTCOME_PENDING,
            RECONCILIATION_REQUIRED, LEG_REFUND_PENDING);

    private SplitPaymentEvents() {
    }
}
