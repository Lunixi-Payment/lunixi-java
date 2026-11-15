package com.lunixi.sdk.bankconnect;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.lunixi.sdk.internal.WebhookPayloads;
import com.lunixi.sdk.webhook.WebhookEvent;

/**
 * The {@code data} of a verified {@code bank_transfer.*} webhook ({@link BankTransferEvents}). Only the allow-listed
 * fields ({@link #FIELDS}) are kept; fields without a value are {@code null} on the wire and empty here. One event
 * per transfer and type; dedupe on the event id.
 */
public final class BankTransferWebhookData {

    /** The fields the gateway delivers; no other key is sent. */
    public static final List<String> FIELDS = List.of("transferId", "connectionId", "accountId", "bankEftCode",
            "bankName", "status", "rail", "amountMinor", "currency", "receiverIbanMasked", "receiverNameMasked",
            "paymentPurpose", "description", "failureCode", "idempotencyKey", "createdAt", "completedAt", "failedAt",
            "returnedAt");

    private final String eventId;
    private final String eventType;
    private final Map<String, Object> data;

    private BankTransferWebhookData(String eventId, String eventType, Map<String, Object> data) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.data = data;
    }

    /**
     * @param event a webhook verified with {@link com.lunixi.sdk.webhook.WebhookVerifier}
     * @return the transfer data
     * @throws com.lunixi.sdk.exception.WebhookVerificationException when it is not a {@code bank_transfer.*} event or
     *                                                               the amount is malformed
     */
    public static BankTransferWebhookData fromEvent(WebhookEvent event) {
        Map<String, Object> data = WebhookPayloads.read(event, BankTransferEvents.ALL, FIELDS,
                List.of("amountMinor"), "a bank transfer");
        return new BankTransferWebhookData(event.id(), event.type(), data);
    }

    /** @return the event id */
    public String eventId() {
        return eventId;
    }

    /** @return the event type ({@link BankTransferEvents}) */
    public String eventType() {
        return eventType;
    }

    /** @return the transfer id */
    public String transferId() {
        return string("transferId");
    }

    /** @return the source account id */
    public String accountId() {
        return string("accountId");
    }

    /** @return the connection id */
    public String connectionId() {
        return string("connectionId");
    }

    /** @return the transfer status ({@link BankConnectValues.TransferStatus}) */
    public String status() {
        return string("status");
    }

    /** @return the rail the bank chose ({@link BankConnectValues.TransferRail}), when known */
    public Optional<String> rail() {
        return WebhookPayloads.string(data, "rail");
    }

    /** @return the amount in minor units */
    public String amountMinor() {
        return string("amountMinor");
    }

    /** @return the currency */
    public String currency() {
        return string("currency");
    }

    /** @return the failure code ({@code bank_connect.transfer.*}) of a failed transfer */
    public Optional<String> failureCode() {
        return WebhookPayloads.string(data, "failureCode");
    }

    /** @return the transfer's {@code Idempotency-Key} */
    public String idempotencyKey() {
        return string("idempotencyKey");
    }

    /** @return the allow-listed {@code data} (also {@code bankEftCode}, {@code bankName}, the masked receiver, the
     *         purpose, the description and the timestamps) */
    public Map<String, Object> data() {
        return data;
    }

    private String string(String field) {
        return WebhookPayloads.string(data, field).orElse(null);
    }
}
