package com.lunixi.sdk.payment;

import java.util.List;

/**
 * Webhook event types of a {@link PaymentMethod#BANK_TRANSFER} payment ({@code payment.*} envelope, payload
 * {@code bankTransfer: { expectedMinor, receivedMinor, remainingMinor, excessMinor, currency, late, transfers[] }};
 * events that open an exception also carry {@code exceptionId}, {@code kind} and {@code amountMinor}). They do not
 * change the payment's status: the outcome is still {@code payment.captured} / {@code payment.failed}.
 *
 * <p>Named apart from the Bank Connect {@code bank_transfer.*} events, which report transfers sent from your
 * connected accounts.
 */
public final class PaymentBankTransferEvents {

    public static final String PARTIALLY_RECEIVED = "payment.bank_transfer.partially_received";
    public static final String UNDERPAID = "payment.bank_transfer.underpaid";
    public static final String OVERPAID = "payment.bank_transfer.overpaid";
    public static final String LATE_ARRIVAL = "payment.bank_transfer.late_arrival";

    /** Every event type. */
    public static final List<String> ALL = List.of(PARTIALLY_RECEIVED, UNDERPAID, OVERPAID, LATE_ARRIVAL);

    private PaymentBankTransferEvents() {
    }
}
