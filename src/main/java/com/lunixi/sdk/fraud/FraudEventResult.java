package com.lunixi.sdk.fraud;

import java.util.Map;
import java.util.Optional;

import com.lunixi.sdk.internal.ResponseMaps;

/**
 * The result of recording one event.
 *
 * <p>{@link #rowId()} is present only when a row was actually written. On a
 * duplicate the response carries no row id, which is why
 * {@link #isDeduplicated()} is the field to branch on rather than the absence
 * of an id — an absent id alone cannot tell "already recorded" apart from
 * "something went wrong".
 */
public final class FraudEventResult {

    private final Map<String, Object> data;

    /**
     * @param data the payload for one event
     */
    public FraudEventResult(Map<String, Object> data) {
        this.data = ResponseMaps.immutableCopy(data);
    }

    /** @return the event id you supplied */
    public Optional<String> eventId() {
        return string("eventId");
    }

    /** @return the stored row's id, or empty when the event was a duplicate */
    public Optional<String> rowId() {
        return string("id");
    }

    /** @return {@code true} when an event with this id already existed */
    public boolean isDeduplicated() {
        return Boolean.TRUE.equals(data.get("deduplicated"));
    }

    /** @return how many published flows matched this event and ran */
    public Optional<Long> matchedFlowCount() {
        Object value = data.get("matchedFlowCount");
        return value instanceof Number ? Optional.of(((Number) value).longValue()) : Optional.empty();
    }

    /**
     * The validation message for <i>this</i> event inside a batch response.
     *
     * <p>A batch answers {@code 200} even when some of its events were
     * rejected, so this is the only place a per-event failure is visible.
     *
     * @return the message, when this event failed
     */
    public Optional<String> error() {
        return string("error");
    }

    /** @return {@code true} when this event was rejected */
    public boolean isFailed() {
        return error().isPresent();
    }

    /** @return the whole payload */
    public Map<String, Object> raw() {
        return data;
    }

    private Optional<String> string(String key) {
        Object value = data.get(key);
        return value instanceof String && !((String) value).isEmpty()
                ? Optional.of((String) value)
                : Optional.empty();
    }
}
