package com.lunixi.sdk.subscription;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.lunixi.sdk.http.RoutePage;

/**
 * One page of a subscription, plan, customer, card, invoice or audit-log list. These lists carry
 * {@code nextPageToken} (and {@code pageSize}) at the envelope root; send {@link #nextPageToken()} back unchanged as
 * the {@code pageToken} filter.
 */
public final class SubscriptionPage {

    private final RoutePage page;
    private final long pageSize;

    /**
     * @param response the decoded list envelope
     */
    public SubscriptionPage(Map<String, Object> response) {
        this.page = new RoutePage(response);
        Object size = response == null ? null : response.get("pageSize");
        this.pageSize = size instanceof Number number ? number.longValue() : page.items().size();
    }

    /** @return the rows of this page */
    public List<Map<String, Object>> items() {
        return page.items();
    }

    /** @return the token of the next page, or empty on the last page */
    public Optional<String> nextPageToken() {
        return page.nextCursor();
    }

    /** @return {@code true} when a further page exists */
    public boolean hasMore() {
        return page.hasMore();
    }

    /** @return the page size the gateway reported (the row count when it reported none) */
    public long pageSize() {
        return pageSize;
    }

    /** @return the total number of matching rows, when the gateway counted them */
    public Optional<Long> totalCount() {
        return page.totalCount();
    }

    /** @return the answer's {@code data} as it came */
    public Object raw() {
        return page.raw();
    }
}
