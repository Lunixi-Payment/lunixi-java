package com.lunixi.sdk.payment;

import java.util.List;

/**
 * Field lists and value sets of split payments (one payment funded by several legs):
 *
 * <pre>
 * POST /api/v1/payments/split                          (create, and continue after a step)
 * GET  /api/v1/payments/split/{splitPaymentId}         (detail with legs)
 * GET  /api/v1/payments/split                          (list, forward cursor)
 * POST /api/v1/payments/split/{splitPaymentId}/refund  (refund of the main payment)
 * </pre>
 *
 * <p>A leg is a stored card or, when split payments with other methods are enabled in your form settings, a
 * non-card method carrying one of {@link #SPLIT_LEG_BLOCK_FIELDS}. Webhook types are in {@link SplitPaymentEvents}.
 */
public final class SplitPaymentValues {

    /**
     * Body fields of {@code POST /payments/split}. {@code buyer}, {@code billingAddress} and {@code basketItems}
     * are required ({@code shippingAddress} for a {@code PHYSICAL} item; {@link OrderDetails}).
     */
    public static final List<String> SPLIT_PAYMENT_FIELDS = List.of("totalAmount", "currency", "ownerRef", "legs",
            "orderId", "ownerScope", "callbackUrl", "buyer", "billingAddress", "shippingAddress", "basketItems");

    /**
     * Method blocks a non-card leg can carry. Meal card, open banking, shopping loan and bank transfer cannot be
     * split payment legs.
     */
    public static final List<String> SPLIT_LEG_BLOCK_FIELDS = List.of("mobileBilling", "wallet", "promissoryNote");

    /** Fields of one leg. */
    public static final List<String> SPLIT_PAYMENT_LEG_FIELDS = List.of("paymentMethod", "cardToken", "amount",
            "force3D", "installment", "installmentQuoteToken", "mobileBilling", "wallet", "promissoryNote");

    /** Filters of {@code GET /payments/split}. */
    public static final List<String> LIST_SPLIT_PAYMENTS_FILTERS = List.of("status", "currency", "createdFrom",
            "createdTo", "orderId", "ownerRef", "limit", "after");

    public static final int SPLIT_MIN_LEGS = 1;
    public static final int SPLIT_MAX_LEGS = 5;
    public static final int SPLIT_LEG_MAX_INSTALLMENT = 36;
    /** Largest {@code limit} of {@link PaymentsClient#listSplitPayments}. */
    public static final int SPLIT_LIST_MAX_LIMIT = 100;

    private SplitPaymentValues() {
    }

    /** Status of a split payment ({@code status}). */
    public static final class Status {
        public static final String COMPOSING = "COMPOSING";
        public static final String AUTHORIZING = "AUTHORIZING";
        /** A card leg waits for the buyer's 3D Secure step ({@code pendingStep.redirectPath}). */
        public static final String AWAITING_3DS = "AWAITING_3DS";
        /** A non-card leg waits for a step ({@code pendingStep}). */
        public static final String AWAITING_ACTION = "AWAITING_ACTION";
        /** A credit leg waits for the lender's approval; no money has moved. */
        public static final String AWAITING_CREDIT_APPROVAL = "AWAITING_CREDIT_APPROVAL";
        /** A card capture failed after the non-reversible leg; a new card is asked for the shortfall. */
        public static final String AWAITING_REPLACEMENT_CARD = "AWAITING_REPLACEMENT_CARD";
        public static final String AUTHORIZED = "AUTHORIZED";
        public static final String CAPTURING = "CAPTURING";
        /** Every leg was charged: the order is paid. */
        public static final String COMPLETED = "COMPLETED";
        /** A leg failed: holds are released and charged legs are refunded. */
        public static final String COMPENSATING = "COMPENSATING";
        /** Failed without a charge left on any leg. */
        public static final String VOIDED = "VOIDED";
        public static final String FAILED = "FAILED";
        /** The non-reversible leg was charged but the rest could not be collected; you decide on delivery. */
        public static final String RECONCILIATION_REQUIRED = "RECONCILIATION_REQUIRED";

        /** Every status. */
        public static final List<String> ALL = List.of(COMPOSING, AUTHORIZING, AWAITING_3DS, AWAITING_ACTION,
                AWAITING_CREDIT_APPROVAL, AWAITING_REPLACEMENT_CARD, AUTHORIZED, CAPTURING, COMPLETED, COMPENSATING,
                VOIDED, FAILED, RECONCILIATION_REQUIRED);

        /**
         * Statuses in which the split payment is still running. {@link PaymentsClient#createSplitPayment} returns
         * these answers (HTTP 400 with the status as {@code code}) instead of throwing.
         */
        public static final List<String> IN_PROGRESS = List.of(COMPOSING, AUTHORIZING, AWAITING_3DS,
                AWAITING_ACTION, AWAITING_CREDIT_APPROVAL, AWAITING_REPLACEMENT_CARD, AUTHORIZED, CAPTURING);

        private Status() {
        }
    }
}
