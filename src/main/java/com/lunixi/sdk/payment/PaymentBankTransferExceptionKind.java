package com.lunixi.sdk.payment;

import java.util.List;

/** {@code kind} of a bank transfer exception ({@link PaymentsClient#listBankTransferExceptions}). */
public final class PaymentBankTransferExceptionKind {

    /** Arrived after the deadline (within 7 days). */
    public static final String LATE = "LATE";
    /** The deadline passed with less than expected; the payment waits for your decision. */
    public static final String UNDERPAID = "UNDERPAID";
    /** More than expected arrived; refund the excess. */
    public static final String OVERPAID = "OVERPAID";

    /** Every kind. */
    public static final List<String> ALL = List.of(LATE, UNDERPAID, OVERPAID);

    private PaymentBankTransferExceptionKind() {
    }
}
