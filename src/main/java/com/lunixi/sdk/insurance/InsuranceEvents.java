package com.lunixi.sdk.insurance;

import java.util.List;

/**
 * Insurance webhook event types (product {@code INSURANCE_SERVICE}, subscription {@code insurance.*}), delivered in
 * the same signed envelope as every Lunixi webhook. Read the order events with {@link InsuranceWebhookData#fromEvent}
 * and the lead event with {@link InsuranceLeadWebhookData#fromEvent}. Dedupe on the event id ({@code data.eventId} is
 * the same value). The order decision comes from the webhook; the API answers show the state.
 */
public final class InsuranceEvents {

    /** The order's policies were issued. */
    public static final String POLICY_ISSUED = "insurance.policy.issued";
    /** The policy could not be issued or the premium was not collected; {@code data.errorCode} says why. */
    public static final String POLICY_FAILED = "insurance.policy.failed";
    /** The collected premium has to be refunded; {@code data.errorCode} says why. */
    public static final String REFUND_REQUIRED = "insurance.refund.required";
    /** A policy document can be downloaded ({@code policies[].documentUrl}). */
    public static final String POLICY_DOCUMENT_READY = "insurance.policy.document_ready";
    /** The provider confirmed the cancellation. */
    public static final String POLICY_CANCELLED = "insurance.policy.cancelled";
    /** The premium refund completed. */
    public static final String REFUND_COMPLETED = "insurance.refund.completed";
    /** The insurance provider received a lead; {@code data} is a lead summary, not an order. */
    public static final String LEAD_SUBMITTED = "insurance.lead.submitted";
    /** Subscription pattern covering every insurance event. */
    public static final String PATTERN = "insurance.*";
    /** Every event type known to this release. */
    public static final List<String> ALL = List.of(POLICY_ISSUED, POLICY_FAILED, REFUND_REQUIRED,
            POLICY_DOCUMENT_READY, POLICY_CANCELLED, REFUND_COMPLETED, LEAD_SUBMITTED);
    /** The events about an order; their {@code data} is {@link InsuranceWebhookData}. */
    public static final List<String> ORDER_EVENTS = List.of(POLICY_ISSUED, POLICY_FAILED, REFUND_REQUIRED,
            POLICY_DOCUMENT_READY, POLICY_CANCELLED, REFUND_COMPLETED);
    /** The events about a lead; their {@code data} is {@link InsuranceLeadWebhookData}. */
    public static final List<String> LEAD_EVENTS = List.of(LEAD_SUBMITTED);

    private InsuranceEvents() {
    }
}
