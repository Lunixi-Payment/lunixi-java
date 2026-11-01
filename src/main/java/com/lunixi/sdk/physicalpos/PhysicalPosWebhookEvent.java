package com.lunixi.sdk.physicalpos;

import java.util.Map;
import java.util.Optional;

import com.lunixi.sdk.internal.ResponseMaps;

/**
 * A verified physical POS webhook ({@code physical_pos.*}), read by {@link PhysicalPosClient#parseWebhookEvent}.
 *
 * <p>Payment payload ({@link #data()}): {@code paymentId, rootPaymentId, original{paymentId, origin}, operation,
 * paymentStatus, statusReason, amountMinor, approvedAmountMinor, currency, installmentCount, deviceId,
 * merchantReference, tender{cardMasked, cardBrand, entryMode}, historySeq, resolvedBy}; a device sale
 * ({@link #isDeviceSale()}) also carries {@code origin} ({@code DEVICE_DIRECT}) and {@code deviceOccurredAt}.
 * Amounts are minor-unit STRINGS here and in the HTTP API. Delivery order is not guaranteed and a delivery can
 * repeat: deduplicate on {@link #id()} and
 * ignore an event whose {@link #historySeq()} is not greater than the last one you applied for that payment. When
 * in doubt, read the payment with {@code getPayment}.
 */
public final class PhysicalPosWebhookEvent {

    /** {@link #category()} of {@code physical_pos.payment.*}. */
    public static final String CATEGORY_PAYMENT = "payment";
    /** {@link #category()} of {@code physical_pos.fiscal_document.*}. */
    public static final String CATEGORY_FISCAL_DOCUMENT = "fiscal_document";
    /** {@link #category()} of any other {@code physical_pos.*} type. */
    public static final String CATEGORY_OTHER = "other";

    private final String id;
    private final String type;
    private final String category;
    private final String environment;
    private final String createdAt;
    private final Long historySeq;
    private final Map<String, Object> data;
    private final Map<String, Object> raw;

    PhysicalPosWebhookEvent(String id, String type, String category, String environment, String createdAt,
                            Long historySeq, Map<String, Object> data, Map<String, Object> raw) {
        this.id = id;
        this.type = type;
        this.category = category;
        this.environment = environment;
        this.createdAt = createdAt;
        this.historySeq = historySeq;
        this.data = ResponseMaps.immutableCopy(data);
        this.raw = ResponseMaps.immutableCopy(raw);
    }

    /** @return the event id ({@code x-lunixi-event-id}); the same transition keeps the same id on every retry */
    public String id() {
        return id;
    }

    /** @return the event type, for example {@link PhysicalPosEvents#PAYMENT_APPROVED} */
    public String type() {
        return type;
    }

    /** @return {@code payment}, {@code fiscal_document} or {@code other} */
    public String category() {
        return category;
    }

    /** @return {@code LIVE} or {@code TEST}, when the envelope carried it */
    public Optional<String> environment() {
        return Optional.ofNullable(environment);
    }

    /** @return when the event occurred, when the envelope carried it */
    public Optional<String> createdAt() {
        return Optional.ofNullable(createdAt);
    }

    /** @return the per-payment ordering number ({@code data.historySeq}), when present */
    public Optional<Long> historySeq() {
        return Optional.ofNullable(historySeq);
    }

    /** @return {@code true} for {@link PhysicalPosEvents#PAYMENT_DEVICE_SALE}, a sale made directly on the device */
    public boolean isDeviceSale() {
        return PhysicalPosEvents.PAYMENT_DEVICE_SALE.equals(type);
    }

    /**
     * @return a reversal's original payment {@code {paymentId, origin}} ({@code origin}: {@code API} or
     *         {@code DEVICE_DIRECT}); empty on a root payment
     */
    public Optional<Map<String, String>> original() {
        return PhysicalPosSupport.webhookOriginal(data);
    }

    /** @return the event payload */
    public Map<String, Object> data() {
        return java.util.Collections.unmodifiableMap(data);
    }

    /** @return the whole delivered envelope */
    public Map<String, Object> raw() {
        return java.util.Collections.unmodifiableMap(raw);
    }
}
