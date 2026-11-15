package com.lunixi.sdk.http;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.lunixi.sdk.internal.ResponseMaps;

/**
 * One page of a list of the route-table clients (subscription catalog and portal lists, KYC, identity,
 * marketplace). Every list shape in use is read: {@code data} as the row list, or {@code data.items} /
 * {@code operations} / {@code rows}; the cursor in {@code pageInfo} (a {@code pageInfo} without one is the last
 * page), else {@code nextCursor} or {@code nextPageToken} (in {@code data} or at the root). The cursor is opaque:
 * send {@link #nextCursor()} back unchanged.
 */
public final class RoutePage {

    private final List<Map<String, Object>> items;
    private final String nextCursor;
    private final boolean hasMore;
    private final Long totalCount;
    private final Object raw;

    /**
     * @param response the decoded list envelope
     */
    @SuppressWarnings("unchecked")
    public RoutePage(Map<String, Object> response) {
        Map<String, Object> root = response == null ? Map.of() : response;
        Object data = root.containsKey("data") ? root.get("data") : root;
        Map<String, Object> container = data instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        List<?> rows = data instanceof List<?> list ? list : firstList(container);
        Map<String, Object> pageInfo = container.get("pageInfo") instanceof Map<?, ?> nested
                ? (Map<String, Object>) nested
                : root.get("pageInfo") instanceof Map<?, ?> sibling ? (Map<String, Object>) sibling : null;
        String token = null;
        if (pageInfo != null && (text(pageInfo.get("nextCursor")) != null || text(pageInfo.get("endCursor")) != null)) {
            token = text(pageInfo.get("nextCursor")) != null ? text(pageInfo.get("nextCursor"))
                    : text(pageInfo.get("endCursor"));
        } else if (pageInfo == null || !(pageInfo.containsKey("nextCursor") || pageInfo.containsKey("endCursor"))) {
            for (Object candidate : new Object[] {container.get("nextCursor"), container.get("nextPageToken"),
                    root.get("nextPageToken"), root.get("nextCursor")}) {
                if (text(candidate) != null) {
                    token = text(candidate);
                    break;
                }
            }
        }
        Long total = null;
        for (Object candidate : new Object[] {pageInfo == null ? null : pageInfo.get("totalCount"),
                container.get("totalCount"), container.get("total")}) {
            if (candidate instanceof Number number) {
                total = number.longValue();
                break;
            }
        }
        this.items = rows(rows);
        this.nextCursor = token;
        this.hasMore = pageInfo != null && pageInfo.get("hasMore") instanceof Boolean flag ? flag : token != null;
        this.totalCount = total;
        this.raw = data instanceof Map<?, ?> map ? ResponseMaps.immutableCopy((Map<String, Object>) map) : data;
    }

    private static List<?> firstList(Map<String, Object> container) {
        for (String key : List.of("items", "operations", "rows")) {
            if (container.get(key) instanceof List<?> list) {
                return list;
            }
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> rows(List<?> list) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object row : list) {
            if (row instanceof Map<?, ?>) {
                out.add(ResponseMaps.immutableCopy((Map<String, Object>) row));
            }
        }
        return Collections.unmodifiableList(out);
    }

    private static String text(Object value) {
        return value instanceof String text && !text.isEmpty() ? text : null;
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

    /** @return the total number of matching rows, when the gateway counted them */
    public Optional<Long> totalCount() {
        return Optional.ofNullable(totalCount);
    }

    /** @return the answer's {@code data} as it came (an object, a list, or the envelope when there is none) */
    public Object raw() {
        return raw;
    }
}
