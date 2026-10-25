package com.lunixi.sdk.fraud;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.lunixi.sdk.internal.ResponseMaps;

/**
 * One decision — the answer to an evaluation, or a persisted decision record.
 *
 * <p>The typed accessors cover the fields the surface documents;
 * {@link #get(String)} and {@link #raw()} reach everything else (the
 * explainability tree, live signals, the actions a flow took), so a field added
 * to the response is readable without an SDK release.
 */
public final class FraudDecision {

    private final Map<String, Object> data;

    /**
     * @param data the response payload for one decision
     */
    public FraudDecision(Map<String, Object> data) {
        this.data = ResponseMaps.immutableCopy(data);
    }

    /**
     * The outcome.
     *
     * @return the outcome, or empty when the response carried a value this SDK
     *         release does not know
     */
    public Optional<FraudDecisionValue> decision() {
        return FraudDecisionValue.fromWire(string("decision").orElse(null));
    }

    /** @return the outcome exactly as it arrived on the wire */
    public Optional<String> decisionValue() {
        return string("decision");
    }

    /** @return the risk score; higher is riskier */
    public Optional<Double> score() {
        Object value = data.get("score");
        return value instanceof Number ? Optional.of(((Number) value).doubleValue()) : Optional.empty();
    }

    /** @return the trace id, which is how a single decision record is fetched back */
    public Optional<String> traceId() {
        return string("traceId");
    }

    /** @return the payment identifier, when the decision was about one */
    public Optional<String> paymentId() {
        return string("paymentId");
    }

    /** @return the reason codes that contributed to the outcome */
    public List<String> reasonCodes() {
        return strings("reasonCodes");
    }

    /** @return the ids of the rules that matched */
    public List<String> matchedRules() {
        return strings("matchedRules");
    }

    /** @return the rule groups that matched */
    public List<String> matchedRuleGroups() {
        return strings("matchedRuleGroups");
    }

    /** @return the tags attached to the decision */
    public List<String> tags() {
        return strings("tags");
    }

    /** @return how long the evaluation took, in milliseconds */
    public Optional<Long> latencyMs() {
        Object value = data.get("latencyMs");
        return value instanceof Number ? Optional.of(((Number) value).longValue()) : Optional.empty();
    }

    /** @return the human-readable explanation summary, when the response carried one */
    @SuppressWarnings("unchecked")
    public Optional<String> explainabilitySummary() {
        Object explainability = data.get("explainability");
        if (!(explainability instanceof Map)) {
            return Optional.empty();
        }
        Object summary = ((Map<String, Object>) explainability).get("summary");
        return summary instanceof String && !((String) summary).isEmpty()
                ? Optional.of((String) summary)
                : Optional.empty();
    }

    /** @return when the decision was recorded, for a persisted record */
    public Optional<String> createdAt() {
        return string("createdAt");
    }

    /** @return {@code true} when the outcome is {@code allow} */
    public boolean isAllow() {
        return decision().filter(FraudDecisionValue.ALLOW::equals).isPresent();
    }

    /** @return {@code true} when the outcome is {@code review} */
    public boolean isReview() {
        return decision().filter(FraudDecisionValue.REVIEW::equals).isPresent();
    }

    /** @return {@code true} when the outcome is {@code block} */
    public boolean isBlock() {
        return decision().filter(FraudDecisionValue.BLOCK::equals).isPresent();
    }

    /** @return {@code true} when the outcome is {@code force_3d} */
    public boolean isForce3d() {
        return decision().filter(FraudDecisionValue.FORCE_3D::equals).isPresent();
    }

    /**
     * Reads any field of the payload.
     *
     * @param key the field name
     * @return the value, when present
     */
    public Optional<Object> get(String key) {
        return Optional.ofNullable(data.get(key));
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

    private List<String> strings(String key) {
        Object value = data.get(key);
        if (!(value instanceof List)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object item : (List<?>) value) {
            if (item != null) {
                out.add(String.valueOf(item));
            }
        }
        return Collections.unmodifiableList(out);
    }
}
