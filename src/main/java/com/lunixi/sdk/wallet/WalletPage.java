package com.lunixi.sdk.wallet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.lunixi.sdk.http.Envelope;
import com.lunixi.sdk.internal.ResponseMaps;

/**
 * One page of a wallet list. Wallet lists answer {@code {items, nextCursor}} (operations: {@code {operations,
 * nextCursor}}, with {@code items} and {@code pageInfo} added by the gateway): the rows are {@code data.items}, or
 * the first list inside {@code data}; the cursor is {@code data.nextCursor}, or the envelope's
 * {@code pageInfo.nextCursor}. Send {@link #nextCursor()} back verbatim as the {@code cursor} filter.
 */
public final class WalletPage {

    private final List<Map<String, Object>> items;
    private final String nextCursor;
    private final boolean hasMore;
    private final Map<String, Object> raw;

    /**
     * @param response the decoded list envelope
     */
    public WalletPage(Map<String, Object> response) {
        Object data = response == null ? null : response.get("data");
        Map<String, Object> payload = Envelope.data(response);
        this.items = rows(data instanceof List<?> list ? list : firstList(payload));
        Map<String, Object> pageInfo = Envelope.pageInfo(response);
        String cursor = payload.get("nextCursor") instanceof String text && !text.isEmpty() ? text : null;
        if (cursor == null && pageInfo.get("nextCursor") instanceof String text && !text.isEmpty()) {
            cursor = text;
        }
        this.nextCursor = cursor;
        this.hasMore = pageInfo.get("hasMore") instanceof Boolean flag ? flag : cursor != null;
        this.raw = ResponseMaps.immutableCopy(payload);
    }

    private static List<?> firstList(Map<String, Object> payload) {
        if (payload.get("items") instanceof List<?> list) {
            return list;
        }
        for (Object value : payload.values()) {
            if (value instanceof List<?> list) {
                return list;
            }
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rows(List<?> list) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object row : list) {
            if (row instanceof Map<?, ?>) {
                out.add(ResponseMaps.immutableCopy((Map<String, Object>) row));
            }
        }
        return Collections.unmodifiableList(out);
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

    /** @return the whole {@code data} object of the answer (for list-level fields) */
    public Map<String, Object> raw() {
        return Collections.unmodifiableMap(raw);
    }
}
