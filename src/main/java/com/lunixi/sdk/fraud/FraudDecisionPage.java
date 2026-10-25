package com.lunixi.sdk.fraud;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.lunixi.sdk.http.Envelope;

/**
 * One page of persisted decision records.
 *
 * <p>Paging is cursor-based: hand {@link #nextCursor()} back as the cursor
 * filter to read the following page. The paging metadata arrives beside the
 * rows rather than inside them, which is what {@link Envelope} resolves.
 */
public final class FraudDecisionPage {

    private final List<FraudDecision> items;
    private final boolean hasMore;
    private final String nextCursor;
    private final Long totalCount;

    /**
     * @param response the decoded list envelope
     */
    public FraudDecisionPage(Map<String, Object> response) {
        List<FraudDecision> decisions = new ArrayList<>();
        for (Map<String, Object> row : Envelope.rows(response)) {
            decisions.add(new FraudDecision(row));
        }
        this.items = Collections.unmodifiableList(decisions);
        this.hasMore = Envelope.hasMore(response);
        this.nextCursor = Envelope.nextCursor(response).orElse(null);
        this.totalCount = Envelope.totalCount(response).orElse(null);
    }

    /** @return the records on this page */
    public List<FraudDecision> items() {
        return items;
    }

    /** @return how many records this page holds */
    public int size() {
        return items.size();
    }

    /** @return the first record on the page */
    public Optional<FraudDecision> first() {
        return items.isEmpty() ? Optional.empty() : Optional.of(items.get(0));
    }

    /** @return {@code true} when a further page exists */
    public boolean hasMore() {
        return hasMore;
    }

    /**
     * The cursor for the next page.
     *
     * <p>It is opaque: send it back verbatim, never decode or rebuild it.
     *
     * @return the cursor, or empty on the last page
     */
    public Optional<String> nextCursor() {
        return Optional.ofNullable(nextCursor);
    }

    /**
     * The total number of matching records — only when the server counted them,
     * which it does not do by default.
     *
     * @return the total, when it was returned
     */
    public Optional<Long> totalCount() {
        return Optional.ofNullable(totalCount);
    }
}
