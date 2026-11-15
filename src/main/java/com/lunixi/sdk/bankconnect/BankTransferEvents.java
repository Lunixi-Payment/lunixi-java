package com.lunixi.sdk.bankconnect;

import java.util.List;

/**
 * Outgoing bank transfer webhook event types (product {@code BANK_CONNECT}, subscription {@code bank_transfer.*}),
 * delivered in the same signed envelope as every Lunixi webhook; read them with
 * {@link BankTransferWebhookData#fromEvent}. One event per transfer and type; the event id is the same on every
 * redelivery. There is no webhook for currency exchange orders.
 *
 * <p>Not to be confused with the payment method's {@code payment.bank_transfer.*} events
 * ({@link com.lunixi.sdk.payment.PaymentBankTransferEvents}).
 */
public final class BankTransferEvents {

    /** A transfer was created (API: {@code SUBMITTED}; dashboard: {@code PENDING_OTP}). */
    public static final String CREATED = "bank_transfer.created";
    /** The bank completed the transfer. */
    public static final String COMPLETED = "bank_transfer.completed";
    /** The transfer failed; {@code failureCode} says why. */
    public static final String FAILED = "bank_transfer.failed";
    /** The receiving bank sent a completed transfer back. */
    public static final String RETURNED = "bank_transfer.returned";
    /** Subscription pattern covering every transfer event. */
    public static final String PATTERN = "bank_transfer.*";
    /** Every event type known to this release. */
    public static final List<String> ALL = List.of(CREATED, COMPLETED, FAILED, RETURNED);

    private BankTransferEvents() {
    }
}
