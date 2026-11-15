package com.lunixi.sdk.subscription;

import java.util.List;

/**
 * Subscription webhook event types (the same signed envelope as payment events; {@code data} amounts are minor-unit
 * strings). Verify the delivery with {@link com.lunixi.sdk.webhook.WebhookVerifier} and switch on its type.
 */
public final class SubscriptionEvents {

    public static final String CREATED = "subscription.created";
    public static final String ACTIVATED = "subscription.activated";
    public static final String TRIAL_WILL_END = "subscription.trial_will_end";
    public static final String PAUSED = "subscription.paused";
    public static final String RESUMED = "subscription.resumed";
    public static final String CANCEL_SCHEDULED = "subscription.cancel_scheduled";
    public static final String CANCELED = "subscription.canceled";
    public static final String UPGRADED = "subscription.upgraded";
    public static final String DOWNGRADED = "subscription.downgraded";
    public static final String PLAN_CHANGE_SCHEDULED = "subscription.plan_change_scheduled";
    public static final String PENDING_CHANGE_APPLIED = "subscription.pending_change_applied";
    public static final String PAYMENT_CARD_SWAPPED = "subscription.payment_card_swapped";
    public static final String PAYMENT_METHOD_LOST = "subscription.payment_method_lost";
    public static final String INVOICE_CREATED = "invoice.created";
    public static final String INVOICE_PAID = "invoice.paid";
    public static final String INVOICE_PAYMENT_FAILED = "invoice.payment_failed";
    public static final String INVOICE_VOIDED = "invoice.voided";
    public static final String CARD_ADDED = "card.added";
    public static final String CARD_SET_DEFAULT = "card.set_default";
    public static final String CARD_UPDATED = "card.updated";
    public static final String CARD_DEACTIVATED = "card.deactivated";
    public static final String CARD_EXPIRING_SOON = "card.expiring_soon";
    public static final String CARD_EXPIRED = "card.expired";

    /** Every event type, in catalog order. */
    public static final List<String> ALL = List.of(CREATED, ACTIVATED, TRIAL_WILL_END, PAUSED, RESUMED,
            CANCEL_SCHEDULED, CANCELED, UPGRADED, DOWNGRADED, PLAN_CHANGE_SCHEDULED, PENDING_CHANGE_APPLIED,
            PAYMENT_CARD_SWAPPED, PAYMENT_METHOD_LOST, INVOICE_CREATED, INVOICE_PAID, INVOICE_PAYMENT_FAILED,
            INVOICE_VOIDED, CARD_ADDED, CARD_SET_DEFAULT, CARD_UPDATED, CARD_DEACTIVATED, CARD_EXPIRING_SOON,
            CARD_EXPIRED);

    private SubscriptionEvents() {
    }
}
