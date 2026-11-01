package com.lunixi.sdk.physicalpos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.lunixi.sdk.http.Envelope;
import com.lunixi.sdk.internal.ResponseMaps;

/**
 * One page of a physical POS list ({@code GET /payments}, {@code /transactions}, {@code /settlements},
 * {@code /stores}, {@code /devices} …).
 *
 * <p>The rows are {@code data.items}; the paging metadata is the envelope's {@code pageInfo} (next to {@code data};
 * a copy inside {@code data} is read as a fallback). Lists are newest first and the cursor is opaque: send
 * {@link #nextCursor()} back verbatim as the {@code cursor} filter. {@link #hasMore()} is true only when a
 * non-empty cursor came with it.
 */
public final class PhysicalPosPage {

    private final List<Map<String, Object>> items;
    private final boolean hasMore;
    private final String nextCursor;
    private final Long totalCount;

    /**
     * @param response the decoded list envelope
     */
    public PhysicalPosPage(Map<String, Object> response) {
        List<Map<String, Object>> rows = new ArrayList<>();
        Object raw = Envelope.data(response).get("items");
        if (raw instanceof List<?> list) {
            for (Object row : list) {
                if (row instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> typed = (Map<String, Object>) row;
                    rows.add(ResponseMaps.immutableCopy(typed));
                }
            }
        }
        this.items = Collections.unmodifiableList(rows);
        this.nextCursor = Envelope.nextCursor(response).orElse(null);
        this.hasMore = Envelope.hasMore(response) && nextCursor != null;
        this.totalCount = Envelope.totalCount(response).orElse(null);
    }

    /** @return the rows of this page, in wire order */
    public List<Map<String, Object>> items() {
        return items;
    }

    /** @return how many rows this page holds */
    public int size() {
        return items.size();
    }

    /** @return {@code true} when a further page exists */
    public boolean hasMore() {
        return hasMore;
    }

    /** @return the cursor of the next page, or empty on the last page */
    public Optional<String> nextCursor() {
        return Optional.ofNullable(nextCursor);
    }

    /**
     * @return the filtered total — only when it was asked for ({@code includeTotal}) on an entity list; payment,
     *         transaction and settlement lists never count
     */
    public Optional<Long> totalCount() {
        return Optional.ofNullable(totalCount);
    }
}
