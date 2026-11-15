package com.lunixi.sdk.paymentlink;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.lunixi.sdk.http.Envelope;

/**
 * One page of a payment-link list: links, attempts or payments. Rows are {@code data.items}; paging is the
 * envelope's {@code pageInfo} (a copy inside {@code data} is read as a fallback). Hand {@link #nextCursor()} back as
 * the {@code cursor} filter to read the following page.
 */
public final class PaymentLinkPage {

    private final List<Map<String, Object>> items;
    private final boolean hasMore;
    private final String nextCursor;
    private final Long totalCount;

    /**
     * @param response the decoded list envelope
     */
    @SuppressWarnings("unchecked")
    public PaymentLinkPage(Map<String, Object> response) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (Envelope.data(response).get("items") instanceof List<?> list) {
            for (Object row : list) {
                if (row instanceof Map<?, ?>) {
                    rows.add((Map<String, Object>) row);
                }
            }
        }
        this.items = Collections.unmodifiableList(rows);
        this.hasMore = Envelope.hasMore(response);
        this.nextCursor = Envelope.nextCursor(response).orElse(null);
        this.totalCount = Envelope.totalCount(response).orElse(null);
    }

    /** @return the rows of this page */
    public List<Map<String, Object>> items() {
        return items;
    }

    /** @return {@code true} when a further page exists */
    public boolean hasMore() {
        return hasMore;
    }

    /** @return the opaque cursor of the next page, or empty on the last page */
    public Optional<String> nextCursor() {
        return Optional.ofNullable(nextCursor);
    }

    /** @return the total, only when it was asked for with {@code includeTotal} */
    public Optional<Long> totalCount() {
        return Optional.ofNullable(totalCount);
    }
}
