package com.lunixi.sdk.physicalpos;

import java.util.List;

/**
 * Physical POS webhook event types ({@code physical_pos.*}).
 *
 * <p>They arrive in the platform's signed webhook envelope: verify with
 * {@link com.lunixi.sdk.webhook.WebhookVerifier}, then read with {@link PhysicalPosClient#parseWebhookEvent}.
 * Reversals (void, refund, pre-authorisation close / cancel) use the same payment events; tell them apart with
 * {@code operation} and {@code rootPaymentId}, and the original payment's source with
 * {@link PhysicalPosWebhookEvent#original()}. {@code physical_pos.payment.created} / {@code .in_progress} are
 * internal and never delivered. The set is open: an unknown {@code physical_pos.*} type may appear later.
 *
 * <p>{@link #PAYMENT_DEVICE_SALE} reports a sale made directly on the device ({@code origin: DEVICE_DIRECT}): it
 * arrives approved with {@code deviceOccurredAt}; no {@code approved} event is sent for it. Its refund comes with
 * the usual payment events ({@code original.origin = DEVICE_DIRECT}).
 */
public final class PhysicalPosEvents {

    /** The payment (or reversal) was approved: money moved. */
    public static final String PAYMENT_APPROVED = "physical_pos.payment.approved";
    /** Approved for less than requested ({@code approvedAmountMinor}). */
    public static final String PAYMENT_PARTIALLY_APPROVED = "physical_pos.payment.partially_approved";
    /** Declined by the bank or card, or resolved as not charged; no money moved. */
    public static final String PAYMENT_DECLINED = "physical_pos.payment.declined";
    /** It had started and was cancelled; no money moved. */
    public static final String PAYMENT_CANCELLED = "physical_pos.payment.cancelled";
    /** It never started: the start window passed or you cancelled before dispatch ({@code statusReason}). */
    public static final String PAYMENT_EXPIRED = "physical_pos.payment.expired";
    /** It never started: an error (device rejection …). */
    public static final String PAYMENT_FAILED = "physical_pos.payment.failed";
    /** The outcome is unknown and money may have moved: do NOT charge again, it is being verified. */
    public static final String PAYMENT_UNKNOWN = "physical_pos.payment.unknown";
    /** Automatic verification ended without a result; resolve it in the panel with evidence. */
    public static final String PAYMENT_MANUAL_REVIEW = "physical_pos.payment.manual_review";
    /** A sale made directly on the device ({@code origin: DEVICE_DIRECT}), recorded already approved. */
    public static final String PAYMENT_DEVICE_SALE = "physical_pos.payment.device_sale";
    /** A fiscal document was issued. */
    public static final String FISCAL_DOCUMENT_ISSUED = "physical_pos.fiscal_document.issued";
    /** A fiscal document failed. */
    public static final String FISCAL_DOCUMENT_FAILED = "physical_pos.fiscal_document.failed";
    /** A fiscal document was cancelled. */
    public static final String FISCAL_DOCUMENT_CANCELLED = "physical_pos.fiscal_document.cancelled";
    /** A fiscal document needs an action in the panel. */
    public static final String FISCAL_DOCUMENT_ACTION_REQUIRED = "physical_pos.fiscal_document.action_required";

    /** Subscription pattern covering every physical POS event. */
    public static final String PATTERN = "physical_pos.*";

    /** Every event type known to this release. */
    public static final List<String> ALL = List.of(
            PAYMENT_APPROVED, PAYMENT_PARTIALLY_APPROVED, PAYMENT_DECLINED, PAYMENT_CANCELLED, PAYMENT_EXPIRED,
            PAYMENT_FAILED, PAYMENT_UNKNOWN, PAYMENT_MANUAL_REVIEW, PAYMENT_DEVICE_SALE, FISCAL_DOCUMENT_ISSUED,
            FISCAL_DOCUMENT_FAILED,
            FISCAL_DOCUMENT_CANCELLED, FISCAL_DOCUMENT_ACTION_REQUIRED);

    private PhysicalPosEvents() {
    }
}
