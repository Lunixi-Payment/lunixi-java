package com.lunixi.sdk.physicalpos;

import java.util.Map;
import java.util.Optional;

import com.lunixi.sdk.internal.ResponseMaps;

/**
 * The answer of a physical POS write that carries an {@code Idempotency-Key}.
 *
 * <p>Payment writes ({@code createPayment}, {@code createReversal}) answer {@code 202 Accepted} for a new payment
 * — accepted, not paid: follow the outcome by webhook or {@code getPayment} — and {@code 200} with
 * {@link #replayed()} {@code true} when the same key and body were sent again (the payment's CURRENT view, not a
 * copy of the first answer). {@code cancelPayment} always answers {@code 200}. Management creates answer
 * {@code 201} new / {@code 200} replay.
 *
 * <p>{@link #idempotencyKey()} is the key that was sent (yours, or the one the SDK generated). Keep it while the
 * outcome is not known.
 */
public final class PhysicalPosWriteResult {

    private final String entityName;
    private final Map<String, Object> data;
    private final int statusCode;
    private final String idempotencyKey;

    PhysicalPosWriteResult(String entityName, Map<String, Object> data, int statusCode, String idempotencyKey) {
        this.entityName = entityName;
        this.data = ResponseMaps.immutableCopy(data);
        this.statusCode = statusCode;
        this.idempotencyKey = idempotencyKey;
    }

    /** @return the written record ({@code payment}, {@code store}, {@code terminal}, {@code device} …) */
    public Map<String, Object> entity() {
        return PhysicalPosSupport.object(data.get(entityName));
    }

    /** @return the payment of a payment write (empty for a management write) */
    public Map<String, Object> payment() {
        return PhysicalPosSupport.object(data.get("payment"));
    }

    /** @return {@code true} when the same key and body were already recorded */
    public boolean replayed() {
        return Boolean.TRUE.equals(data.get("replayed"));
    }

    /** @return the HTTP status the gateway answered with: 202 / 201 new, 200 replay or plain success */
    public int statusCode() {
        return statusCode;
    }

    /** @return {@code true} for {@code 202 Accepted}: a new payment or reversal was queued to the device */
    public boolean accepted() {
        return statusCode == 202;
    }

    /** @return the {@code Idempotency-Key} that was sent */
    public String idempotencyKey() {
        return idempotencyKey;
    }

    /** @return the whole {@code data} object (sibling fields such as {@code closedAssignment}, {@code link}) */
    public Map<String, Object> data() {
        return data;
    }

    /**
     * @param key a field of {@code data}
     * @return the value, when present
     */
    public Optional<Object> get(String key) {
        return Optional.ofNullable(data.get(key));
    }
}
