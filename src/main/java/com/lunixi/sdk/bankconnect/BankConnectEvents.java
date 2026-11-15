package com.lunixi.sdk.bankconnect;

import java.util.List;

/**
 * Bank account webhook event types (product {@code BANK_CONNECT}, subscription {@code bank_account.*}), delivered in
 * the same signed envelope as every Lunixi webhook: verify with {@link com.lunixi.sdk.webhook.WebhookVerifier}, then
 * read with {@link BankAccountTransactionReceived#fromEvent}.
 */
public final class BankConnectEvents {

    /**
     * A new transaction ({@code CREDIT} or {@code DEBIT}) was read from one of your connected accounts. Sent once per
     * transaction, for transactions first seen by a regular read after the connection became active; the history
     * loaded when an account is first connected does not send it. The event id is the same on every redelivery:
     * dedupe on it.
     */
    public static final String TRANSACTION_RECEIVED = "bank_account.transaction.received";
    /** Subscription pattern covering every bank account event. */
    public static final String PATTERN = "bank_account.*";
    /** Every event type known to this release. */
    public static final List<String> ALL = List.of(TRANSACTION_RECEIVED);

    private BankConnectEvents() {
    }
}
