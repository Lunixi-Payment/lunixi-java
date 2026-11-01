package com.lunixi.sdk.physicalpos;

import java.util.List;

/**
 * Physical POS wire values. Informative only: the sets are open on the wire, so never reject a value missing here
 * — decide on {@code paymentStatus}, and ignore an unknown {@code statusReason}.
 */
public final class PhysicalPosValues {

    private PhysicalPosValues() {
    }

    /** {@code operation} of a payment. The first three start a payment; the rest reverse one. */
    public static final class PaymentOperation {
        public static final String SALE = "SALE";
        public static final String PREAUTH_OPEN = "PREAUTH_OPEN";
        public static final String REFUND_UNMATCHED = "REFUND_UNMATCHED";
        public static final String VOID = "VOID";
        public static final String REFUND = "REFUND";
        public static final String PREAUTH_CLOSE = "PREAUTH_CLOSE";
        public static final String PREAUTH_CANCEL = "PREAUTH_CANCEL";
        /** Operations of {@code createPayment}. */
        public static final List<String> ROOT = List.of(SALE, PREAUTH_OPEN, REFUND_UNMATCHED);
        /** Operations of {@code createReversal}. */
        public static final List<String> REVERSAL = List.of(VOID, REFUND, PREAUTH_CLOSE, PREAUTH_CANCEL);

        private PaymentOperation() {
        }
    }

    /** {@code paymentStatus}. */
    public static final class PaymentStatus {
        public static final String NOT_STARTED = "NOT_STARTED";
        public static final String IN_PROGRESS = "IN_PROGRESS";
        public static final String APPROVED = "APPROVED";
        public static final String PARTIALLY_APPROVED = "PARTIALLY_APPROVED";
        public static final String DECLINED = "DECLINED";
        public static final String CANCELLED = "CANCELLED";
        public static final String EXPIRED = "EXPIRED";
        public static final String FAILED = "FAILED";
        /** The outcome is unknown and money may have moved: never start the payment again. */
        public static final String UNKNOWN = "UNKNOWN";
        public static final String MANUAL_REVIEW = "MANUAL_REVIEW";

        private PaymentStatus() {
        }
    }

    /** {@code reversalStatus} of a root payment. */
    public static final class ReversalStatus {
        public static final String NONE = "NONE";
        public static final String PARTIALLY_REFUNDED = "PARTIALLY_REFUNDED";
        public static final String REFUNDED = "REFUNDED";
        public static final String VOIDED = "VOIDED";
        public static final String PREAUTH_CLOSED = "PREAUTH_CLOSED";
        public static final String PREAUTH_CANCELLED = "PREAUTH_CANCELLED";
        public static final String PREAUTH_EXPIRED = "PREAUTH_EXPIRED";

        private ReversalStatus() {
        }
    }
}
