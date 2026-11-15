package com.lunixi.sdk.bankconnect;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.lunixi.sdk.internal.WebhookPayloads;
import com.lunixi.sdk.webhook.WebhookEvent;

/**
 * The {@code data} of a verified {@link BankConnectEvents#TRANSACTION_RECEIVED} webhook. Only the allow-listed
 * fields ({@link #FIELDS}) are kept; {@code amountMinor} is a positive minor-unit string. Dedupe on the event id
 * ({@link WebhookEvent#id()}): it is derived from the transaction and the same on every redelivery.
 */
public final class BankAccountTransactionReceived {

    /** The fields the gateway delivers; no other key is sent. */
    public static final List<String> FIELDS = List.of("transactionId", "accountId", "connectionId", "bankEftCode",
            "bankName", "direction", "amountMinor", "currency", "bookedAt", "valueAt", "receivedAt", "canonicalType",
            "description", "counterpartyNameMasked", "counterpartyIbanMasked", "receiptNo");

    private final String eventId;
    private final Map<String, Object> data;

    private BankAccountTransactionReceived(String eventId, Map<String, Object> data) {
        this.eventId = eventId;
        this.data = data;
    }

    /**
     * @param event a webhook verified with {@link com.lunixi.sdk.webhook.WebhookVerifier}
     * @return the transaction data
     * @throws com.lunixi.sdk.exception.WebhookVerificationException when it is another event type or the amount is
     *                                                               malformed
     */
    public static BankAccountTransactionReceived fromEvent(WebhookEvent event) {
        Map<String, Object> data = WebhookPayloads.read(event, BankConnectEvents.ALL, FIELDS, List.of("amountMinor"),
                "a bank account");
        return new BankAccountTransactionReceived(event.id(), data);
    }

    /** @return the event id */
    public String eventId() {
        return eventId;
    }

    /** @return the transaction id */
    public String transactionId() {
        return string("transactionId");
    }

    /** @return the account id */
    public String accountId() {
        return string("accountId");
    }

    /** @return the connection id */
    public String connectionId() {
        return string("connectionId");
    }

    /** @return the bank's EFT code */
    public String bankEftCode() {
        return string("bankEftCode");
    }

    /** @return the bank's name */
    public String bankName() {
        return string("bankName");
    }

    /** @return {@code CREDIT} or {@code DEBIT} ({@link BankConnectValues.TransactionDirection}) */
    public String direction() {
        return string("direction");
    }

    /** @return the positive amount in minor units */
    public String amountMinor() {
        return string("amountMinor");
    }

    /** @return the currency */
    public String currency() {
        return string("currency");
    }

    /** @return the bank's booking time */
    public String bookedAt() {
        return string("bookedAt");
    }

    /** @return the value date, when the bank gave one */
    public Optional<String> valueAt() {
        return WebhookPayloads.string(data, "valueAt");
    }

    /** @return when Lunixi recorded the transaction */
    public String receivedAt() {
        return string("receivedAt");
    }

    /** @return the bank-independent type ({@link BankConnectValues.TransactionType}) */
    public String canonicalType() {
        return string("canonicalType");
    }

    /** @return the bank's text, unchanged */
    public Optional<String> description() {
        return WebhookPayloads.string(data, "description");
    }

    /** @return the masked counterparty name */
    public Optional<String> counterpartyNameMasked() {
        return WebhookPayloads.string(data, "counterpartyNameMasked");
    }

    /** @return the masked counterparty IBAN */
    public Optional<String> counterpartyIbanMasked() {
        return WebhookPayloads.string(data, "counterpartyIbanMasked");
    }

    /** @return the bank's receipt number */
    public Optional<String> receiptNo() {
        return WebhookPayloads.string(data, "receiptNo");
    }

    /** @return the allow-listed {@code data} */
    public Map<String, Object> data() {
        return data;
    }

    private String string(String field) {
        return WebhookPayloads.string(data, field).orElse(null);
    }
}
