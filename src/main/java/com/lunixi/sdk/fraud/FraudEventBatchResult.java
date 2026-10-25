package com.lunixi.sdk.fraud;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.lunixi.sdk.internal.ResponseMaps;

/**
 * The result of a batch event submission.
 *
 * <h2>A batch can succeed partially</h2>
 * The endpoint answers {@code 200} and keeps writing the remaining events when
 * one of them fails validation, so the HTTP status alone never proves the batch
 * landed. {@link #failures()} is the field to check: an integration that reads
 * only the status code loses events without ever seeing an error.
 */
public final class FraudEventBatchResult {

    private final Map<String, Object> data;
    private final List<FraudEventResult> results;

    /**
     * @param data the batch payload
     */
    @SuppressWarnings("unchecked")
    public FraudEventBatchResult(Map<String, Object> data) {
        this.data = ResponseMaps.immutableCopy(data);
        List<FraudEventResult> parsed = new ArrayList<>();
        Object rows = this.data.get("results");
        if (rows instanceof List) {
            for (Object row : (List<Object>) rows) {
                if (row instanceof Map) {
                    parsed.add(new FraudEventResult((Map<String, Object>) row));
                }
            }
        }
        this.results = Collections.unmodifiableList(parsed);
    }

    /** @return how many events the batch carried */
    public long total() {
        return number("total");
    }

    /** @return how many events were written */
    public long inserted() {
        return number("inserted");
    }

    /** @return how many events were recognised as duplicates */
    public long deduplicated() {
        return number("deduplicated");
    }

    /** @return how many events were rejected */
    public long failed() {
        return number("failed");
    }

    /** @return the per-event results, in the order the batch was sent */
    public List<FraudEventResult> results() {
        return results;
    }

    /**
     * Only the events that were rejected. Correct the body and resend them with
     * the same event ids — a resend of an accepted event is deduplicated, so
     * retrying the whole batch is safe too.
     *
     * @return the rejected events
     */
    public List<FraudEventResult> failures() {
        List<FraudEventResult> out = new ArrayList<>();
        for (FraudEventResult result : results) {
            if (result.isFailed()) {
                out.add(result);
            }
        }
        return Collections.unmodifiableList(out);
    }

    /** @return the whole payload */
    public Map<String, Object> raw() {
        return data;
    }

    private long number(String key) {
        Object value = data.get(key);
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }
}
