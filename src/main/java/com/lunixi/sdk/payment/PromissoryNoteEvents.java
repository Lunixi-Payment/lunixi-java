package com.lunixi.sdk.payment;

import java.util.List;

/**
 * Webhook event types of a promissory note (Senetle Ödeme). The envelope is the one of every {@code payment.*}
 * event; the payload adds {@code promissoryNote: { serialNumber, sequence, amountMinor, dueDate, status,
 * occurredAt }}. Statuses are polled from the provider, so the event arrives some time after the due date.
 */
public final class PromissoryNoteEvents {

    public static final String PAID = "payment.promissory_note.paid";
    public static final String PROTESTED = "payment.promissory_note.protested";
    public static final String CANCELLED = "payment.promissory_note.cancelled";

    /** Every event type. */
    public static final List<String> ALL = List.of(PAID, PROTESTED, CANCELLED);

    private PromissoryNoteEvents() {
    }
}
