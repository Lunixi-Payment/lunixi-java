package com.lunixi.sdk.fraud;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import com.lunixi.sdk.exception.ConfigurationException;
import com.lunixi.sdk.http.Envelope;
import com.lunixi.sdk.internal.ApiClient;
import com.lunixi.sdk.internal.RequestOptions;

/**
 * Reads back persisted decision records — the reconciliation half of the
 * surface.
 *
 * <p>For a payment taken through the platform a decision has already been made
 * inside authorisation, so {@link #latestForPayment(String)} is how you read
 * the outcome for an order; you do not ask for it again.
 */
public final class FraudDecisionsClient {

    private final ApiClient api;
    private final String basePath;

    FraudDecisionsClient(ApiClient api, String basePath) {
        this.api = api;
        this.basePath = basePath;
    }

    /**
     * Lists decision records, most recent first.
     *
     * @param filter the filters, or {@code null} for none
     * @return one page of records
     */
    public FraudDecisionPage list(DecisionLogFilter filter) {
        RequestOptions.Builder options = RequestOptions.builder();
        if (filter != null) {
            filter.toQuery().forEach(options::query);
        }
        Map<String, Object> response = api.request("GET", basePath + "/decision-logs", null, options.build());
        return new FraudDecisionPage(response);
    }

    /** @return the first page with no filters */
    public FraudDecisionPage list() {
        return list(null);
    }

    /**
     * Reads one decision record by its trace id.
     *
     * @param traceId the trace id from the decision
     * @return the record
     */
    public FraudDecision get(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            throw new ConfigurationException("'traceId' zorunlu / 'traceId' is required.");
        }
        Map<String, Object> response = api.request(
                "GET", basePath + "/decision-logs/" + encodePathSegment(traceId), null, RequestOptions.none());
        return new FraudDecision(Envelope.data(response));
    }

    /**
     * The most recent decision for a payment.
     *
     * @param paymentId the payment identifier
     * @return the decision, or empty when none was recorded for it
     */
    public Optional<FraudDecision> latestForPayment(String paymentId) {
        if (paymentId == null || paymentId.isBlank()) {
            throw new ConfigurationException("'paymentId' zorunlu / 'paymentId' is required.");
        }
        return list(DecisionLogFilter.create().paymentId(paymentId).limit(1)).first();
    }

    /**
     * Walks every record matching the filters, following cursors as it goes.
     *
     * <p>Pages are fetched lazily, one at a time, so a large result set does not
     * have to be held in memory at once. Iteration stops when the server reports
     * no further page.
     *
     * @param filter the filters, or {@code null} for none
     * @return an iterable over every matching record
     */
    public Iterable<FraudDecision> iterate(DecisionLogFilter filter) {
        Map<String, Object> seed = filter == null ? Map.of() : filter.toQuery();
        return () -> new Iterator<>() {

            private String cursor = seed.get("cursor") == null ? null : String.valueOf(seed.get("cursor"));
            private Iterator<FraudDecision> page = java.util.Collections.emptyIterator();
            private boolean exhausted;

            @Override
            public boolean hasNext() {
                while (!page.hasNext() && !exhausted) {
                    FraudDecisionPage fetched = list(rebuild(seed, cursor));
                    page = fetched.items().iterator();
                    Optional<String> nextCursor = fetched.nextCursor();
                    if (!fetched.hasMore() || nextCursor.isEmpty()) {
                        exhausted = true;
                    } else {
                        cursor = nextCursor.get();
                    }
                }
                return page.hasNext();
            }

            @Override
            public FraudDecision next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return page.next();
            }
        };
    }

    private static DecisionLogFilter rebuild(Map<String, Object> seed, String cursor) {
        DecisionLogFilter filter = DecisionLogFilter.create();
        seed.forEach((key, value) -> {
            switch (key) {
                case "paymentId":
                    filter.paymentId(String.valueOf(value));
                    break;
                case "decision":
                    FraudDecisionValue.fromWire(String.valueOf(value)).ifPresent(filter::decision);
                    break;
                case "integrationMode":
                    filter.integrationMode(String.valueOf(value));
                    break;
                case "productContext":
                    for (FraudTaxonomy.ProductContext candidate : FraudTaxonomy.ProductContext.values()) {
                        if (candidate.wireValue().equals(String.valueOf(value))) {
                            filter.productContext(candidate);
                        }
                    }
                    break;
                case "limit":
                    filter.limit(Integer.parseInt(String.valueOf(value)));
                    break;
                default:
                    break;
            }
        });
        if (cursor != null) {
            filter.cursor(cursor);
        }
        return filter;
    }

    /**
     * Encodes a path segment. {@code URLEncoder} is form encoding, where a space
     * becomes {@code +}; in a path that {@code +} is a literal plus, so it is
     * corrected here.
     */
    private static String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
