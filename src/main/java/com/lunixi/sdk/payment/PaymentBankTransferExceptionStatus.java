package com.lunixi.sdk.payment;

import java.util.List;

/** {@code status} of a bank transfer exception; also the {@code status} filter of the exception list. */
public final class PaymentBankTransferExceptionStatus {

    public static final String PENDING_DECISION = "PENDING_DECISION";
    public static final String ACCEPTED = "ACCEPTED";
    public static final String REFUND_PENDING = "REFUND_PENDING";
    public static final String REFUNDED = "REFUNDED";

    /** Every status. */
    public static final List<String> ALL = List.of(PENDING_DECISION, ACCEPTED, REFUND_PENDING, REFUNDED);

    private PaymentBankTransferExceptionStatus() {
    }
}
