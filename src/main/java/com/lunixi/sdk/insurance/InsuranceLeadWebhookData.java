package com.lunixi.sdk.insurance;

import java.util.List;
import java.util.Map;

import com.lunixi.sdk.internal.WebhookPayloads;
import com.lunixi.sdk.webhook.WebhookEvent;

/**
 * The {@code data} of a verified {@link InsuranceEvents#LEAD_SUBMITTED} webhook: a lead summary, not an order. Only
 * the allow-listed fields ({@link #FIELDS}) are kept; {@link #lead()} is {@code {leadId, insuranceType,
 * schemaVersion, status, submittedAt, createdAt}}. No personal data, note or provider reference is delivered.
 */
public final class InsuranceLeadWebhookData {

    /** The fields the gateway delivers; no other key is sent. */
    public static final List<String> FIELDS = List.of("eventId", "eventType", "merchantId", "lead", "createdAt");

    private final String eventId;
    private final Map<String, Object> data;

    private InsuranceLeadWebhookData(String eventId, Map<String, Object> data) {
        this.eventId = eventId;
        this.data = data;
    }

    /**
     * @param event a webhook verified with {@link com.lunixi.sdk.webhook.WebhookVerifier}
     * @return the lead data
     * @throws com.lunixi.sdk.exception.WebhookVerificationException when it is not an insurance lead event
     */
    public static InsuranceLeadWebhookData fromEvent(WebhookEvent event) {
        Map<String, Object> data = WebhookPayloads.read(event, InsuranceEvents.LEAD_EVENTS, FIELDS, List.of(),
                "an insurance lead");
        return new InsuranceLeadWebhookData(WebhookPayloads.string(data, "eventId").orElse(event.id()), data);
    }

    /** @return the event id ({@code data.eventId}, the same as the envelope id) */
    public String eventId() {
        return eventId;
    }

    /** @return the lead summary */
    @SuppressWarnings("unchecked")
    public Map<String, Object> lead() {
        return data.get("lead") instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    /** @return the lead id ({@code insl_…}) */
    public String leadId() {
        return lead().get("leadId") instanceof String id ? id : null;
    }

    /** @return the lead status ({@link InsuranceValues.LeadStatus}) */
    public String status() {
        return lead().get("status") instanceof String status ? status : null;
    }

    /** @return the allow-listed {@code data} */
    public Map<String, Object> data() {
        return data;
    }
}
