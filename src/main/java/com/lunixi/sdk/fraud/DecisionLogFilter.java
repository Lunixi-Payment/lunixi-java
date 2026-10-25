package com.lunixi.sdk.fraud;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Filters for listing decision records.
 *
 * <pre>
 * FraudDecisionPage page = client.fraud().decisions().list(
 *         DecisionLogFilter.create().paymentId(paymentId).limit(50));
 * </pre>
 */
public final class DecisionLogFilter {

    private final Map<String, Object> filters = new LinkedHashMap<>();

    private DecisionLogFilter() {
    }

    /** @return an empty filter, which lists your most recent records */
    public static DecisionLogFilter create() {
        return new DecisionLogFilter();
    }

    /**
     * @param paymentId list only the records for this payment
     * @return this filter
     */
    public DecisionLogFilter paymentId(String paymentId) {
        return put("paymentId", paymentId);
    }

    /**
     * @param decision list only the records with this outcome
     * @return this filter
     */
    public DecisionLogFilter decision(FraudDecisionValue decision) {
        return put("decision", decision == null ? null : decision.wireValue());
    }

    /**
     * @param integrationMode list only the records recorded for this integration mode
     * @return this filter
     */
    public DecisionLogFilter integrationMode(String integrationMode) {
        return put("integrationMode", integrationMode);
    }

    /**
     * @param productContext list only the records recorded in this product context
     * @return this filter
     */
    public DecisionLogFilter productContext(FraudTaxonomy.ProductContext productContext) {
        return put("productContext", productContext == null ? null : productContext.wireValue());
    }

    /**
     * @param limit how many records to return on the page
     * @return this filter
     */
    public DecisionLogFilter limit(int limit) {
        return put("limit", limit);
    }

    /**
     * The cursor from the previous page.
     *
     * @param cursor the value of {@link FraudDecisionPage#nextCursor()}, sent
     *               back verbatim
     * @return this filter
     */
    public DecisionLogFilter cursor(String cursor) {
        return put("cursor", cursor);
    }

    /** @return the filters as query parameters */
    public Map<String, Object> toQuery() {
        return new LinkedHashMap<>(filters);
    }

    private DecisionLogFilter put(String key, Object value) {
        if (value == null || String.valueOf(value).isEmpty()) {
            filters.remove(key);
        } else {
            filters.put(key, value);
        }
        return this;
    }
}
