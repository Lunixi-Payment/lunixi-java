package com.lunixi.sdk.sanction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.lunixi.sdk.http.Envelope;
import com.lunixi.sdk.internal.ResponseMaps;

/**
 * One page of a sanction list. Sanction lists answer {@code {items, nextCursor}} (history, alerts, entities) or
 * {@code {items}} with the envelope's {@code pageInfo} beside {@code data} (matches, deny list); both are read here.
 * Send {@link #nextCursor()} back verbatim as the {@code cursor} filter.
 */
public final class SanctionPage {

    private final List<Map<String, Object>> items;
    private final String nextCursor;
    private final boolean hasMore;
    private final Long totalCount;
    private final Map<String, Object> raw;

    /**
     * @param response the decoded list envelope
     */
    @SuppressWarnings("unchecked")
    public SanctionPage(Map<String, Object> response) {
        Object data = response == null ? null : response.get("data");
        Map<String, Object> payload = Envelope.data(response);
        Object list = data instanceof List<?> ? data : payload.get("items");
        List<Map<String, Object>> rows = new ArrayList<>();
        if (list instanceof List<?> entries) {
            for (Object row : entries) {
                if (row instanceof Map<?, ?>) {
                    rows.add(ResponseMaps.immutableCopy((Map<String, Object>) row));
                }
            }
        }
        this.items = Collections.unmodifiableList(rows);
        Map<String, Object> pageInfo = Envelope.pageInfo(response);
        Object cursor = payload.get("nextCursor") != null ? payload.get("nextCursor") : pageInfo.get("nextCursor");
        this.nextCursor = cursor instanceof String text && !text.isEmpty() ? text : null;
        this.hasMore = pageInfo.get("hasMore") instanceof Boolean flag ? flag : nextCursor != null;
        Object total = payload.get("totalCount") != null ? payload.get("totalCount") : pageInfo.get("totalCount");
        this.totalCount = total instanceof Number number ? number.longValue() : null;
        this.raw = ResponseMaps.immutableCopy(payload);
    }

    /** @return the rows of this page */
    public List<Map<String, Object>> items() {
        return items;
    }

    /** @return the cursor of the next page, or empty on the last page */
    public Optional<String> nextCursor() {
        return Optional.ofNullable(nextCursor);
    }

    /** @return {@code true} when a further page exists */
    public boolean hasMore() {
        return hasMore;
    }

    /** @return the total, only when it was asked for with {@code includeTotal} */
    public Optional<Long> totalCount() {
        return Optional.ofNullable(totalCount);
    }

    /** @return the whole {@code data} object (history also carries {@code runsToday}, {@code blockedCount} …) */
    public Map<String, Object> raw() {
        return Collections.unmodifiableMap(raw);
    }
}
