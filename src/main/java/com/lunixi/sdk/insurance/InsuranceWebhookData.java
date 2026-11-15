package com.lunixi.sdk.insurance;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.lunixi.sdk.internal.WebhookPayloads;
import com.lunixi.sdk.webhook.WebhookEvent;

/**
 * The {@code data} of a verified insurance order webhook ({@link InsuranceEvents#ORDER_EVENTS}). Only the
 * allow-listed fields ({@link #FIELDS}) are kept; {@code premiumAmount} is a minor-unit string. Each
 * {@code policies[]} row is {@code {policyId, basketItemId, policyNumber, policyStatus, documentStatus,
 * documentUrl}}; {@code documentUrl} is the absolute address of the policy document while the policy is in force
 * (empty text otherwise) and downloading it is a signed API call ({@link InsuranceClient#getPolicyDocument}). No
 * personal data is delivered. Dedupe on {@link #eventId()}.
 */
public final class InsuranceWebhookData {

    /** The fields the gateway delivers; no other key is sent. */
    public static final List<String> FIELDS = List.of("eventId", "eventType", "merchantId", "insuranceOrderId",
            "paymentIntentId", "merchantOrderId", "insuranceType", "premiumCollectedBy", "provider", "status",
            "collectionStatus", "policyStatus", "refundStatus", "documentStatus", "premiumAmount", "currency",
            "policies", "errorCode", "createdAt");

    private final String eventId;
    private final String eventType;
    private final Map<String, Object> data;

    private InsuranceWebhookData(String eventId, String eventType, Map<String, Object> data) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.data = data;
    }

    /**
     * @param event a webhook verified with {@link com.lunixi.sdk.webhook.WebhookVerifier}
     * @return the order data
     * @throws com.lunixi.sdk.exception.WebhookVerificationException when it is not an insurance order event or the
     *                                                               premium is malformed
     */
    public static InsuranceWebhookData fromEvent(WebhookEvent event) {
        Map<String, Object> data = WebhookPayloads.read(event, InsuranceEvents.ORDER_EVENTS, FIELDS,
                List.of("premiumAmount"), "an insurance order");
        String eventId = WebhookPayloads.string(data, "eventId").orElse(event.id());
        return new InsuranceWebhookData(eventId, event.type(), data);
    }

    /** @return the event id ({@code data.eventId}, the same as the envelope id) */
    public String eventId() {
        return eventId;
    }

    /** @return the event type ({@link InsuranceEvents}) */
    public String eventType() {
        return eventType;
    }

    /** @return the insurance order id ({@code inso_…}) */
    public String insuranceOrderId() {
        return string("insuranceOrderId");
    }

    /** @return the bound Lunixi payment, when there is one */
    public Optional<String> paymentIntentId() {
        return WebhookPayloads.string(data, "paymentIntentId");
    }

    /** @return your order reference, when given */
    public Optional<String> merchantOrderId() {
        return WebhookPayloads.string(data, "merchantOrderId");
    }

    /** @return the insurance type */
    public String insuranceType() {
        return string("insuranceType");
    }

    /** @return the overall order status ({@link InsuranceValues.OrderStatus}) */
    public String status() {
        return string("status");
    }

    /** @return the policy axis ({@link InsuranceValues.PolicyStatus}) */
    public String policyStatus() {
        return string("policyStatus");
    }

    /** @return the premium in minor units */
    public String premiumAmount() {
        return string("premiumAmount");
    }

    /** @return the currency */
    public String currency() {
        return string("currency");
    }

    /** @return the policies of the order */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> policies() {
        return data.get("policies") instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }

    /** @return the Lunixi error code (only on {@code insurance.policy.failed} and {@code insurance.refund.required}) */
    public Optional<String> errorCode() {
        return WebhookPayloads.string(data, "errorCode");
    }

    /** @return the allow-listed {@code data} (also the provider, the other axes and {@code createdAt}) */
    public Map<String, Object> data() {
        return data;
    }

    private String string(String field) {
        return WebhookPayloads.string(data, field).orElse(null);
    }
}
