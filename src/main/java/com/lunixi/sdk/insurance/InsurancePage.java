package com.lunixi.sdk.insurance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.lunixi.sdk.http.Envelope;
import com.lunixi.sdk.internal.ResponseMaps;

/**
 * One page of an insurance list (reference values, orders, reconciliation orders, leads). The rows are
 * {@code data.items}; paging is the envelope's top-level {@code pageInfo} (a copy inside {@code data} is read as a
 * fallback). Send {@link #nextCursor()} back verbatim as the {@code cursor} filter.
 */
public final class InsurancePage {

    private final List<Map<String, Object>> items;
    private final boolean hasMore;
    private final String nextCursor;

    /**
     * @param response the decoded list envelope
     */
    @SuppressWarnings("unchecked")
    public InsurancePage(Map<String, Object> response) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (Envelope.data(response).get("items") instanceof List<?> list) {
            for (Object row : list) {
                if (row instanceof Map<?, ?>) {
                    rows.add(ResponseMaps.immutableCopy((Map<String, Object>) row));
                }
            }
        }
        this.items = Collections.unmodifiableList(rows);
        this.nextCursor = Envelope.nextCursor(response).orElse(null);
        this.hasMore = Envelope.hasMore(response) && nextCursor != null;
    }

    /** @return the rows of this page, in wire order */
    public List<Map<String, Object>> items() {
        return items;
    }

    /** @return {@code true} when a further page exists */
    public boolean hasMore() {
        return hasMore;
    }

    /** @return the cursor of the next page, or empty on the last page */
    public Optional<String> nextCursor() {
        return Optional.ofNullable(nextCursor);
    }

    String continuation() {
        return hasMore ? nextCursor : null;
    }
}
