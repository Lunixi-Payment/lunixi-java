package com.lunixi.sdk.internal;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;

/**
 * The {@code iterate…} helpers of the cursor-paged lists: fetches a page, yields its rows, and follows the next
 * cursor until the last page. Pages are fetched lazily, one at a time, as the rows are consumed.
 *
 * <p>SDK internals; this type's signature may change in a minor release.
 */
public final class CursorPages {

    private CursorPages() {
    }

    /**
     * @param filters  the filters of the first page; its {@code cursor}, when given, is where the walk starts
     * @param fetch    reads one page for the given filters
     * @param rows     the rows of a page
     * @param next     the cursor of the following page, or {@code null} on the last page
     * @param <P>      the page type
     * @return an iterable over every row
     */
    public static <P> Iterable<Map<String, Object>> iterate(Map<String, ?> filters,
                                                            Function<Map<String, Object>, P> fetch,
                                                            Function<P, List<Map<String, Object>>> rows,
                                                            Function<P, String> next) {
        Map<String, Object> seed = filters == null ? Map.of() : new LinkedHashMap<>(filters);
        return () -> new Iterator<>() {
            private String cursor = seed.get("cursor") instanceof String text && !text.isEmpty() ? text : null;
            private Iterator<Map<String, Object>> page = Collections.emptyIterator();
            private boolean exhausted;

            @Override
            public boolean hasNext() {
                while (!page.hasNext() && !exhausted) {
                    Map<String, Object> pageFilters = new LinkedHashMap<>(seed);
                    pageFilters.remove("cursor");
                    if (cursor != null) {
                        pageFilters.put("cursor", cursor);
                    }
                    P fetched = fetch.apply(pageFilters);
                    page = rows.apply(fetched).iterator();
                    cursor = next.apply(fetched);
                    exhausted = cursor == null || cursor.isEmpty();
                }
                return page.hasNext();
            }

            @Override
            public Map<String, Object> next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return page.next();
            }
        };
    }
}
