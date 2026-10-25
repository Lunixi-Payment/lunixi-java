package com.lunixi.sdk.internal;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Copies a decoded response map defensively.
 *
 * <p>{@code Map.copyOf} cannot be used for this: it rejects a null value, and
 * JSON null is ordinary in a gateway response — an error envelope carries
 * {@code "data": null}, and an optional field often arrives as null rather
 * than absent. Using {@code Map.copyOf} turns such a response into a
 * {@link NullPointerException} raised while building the object that was
 * supposed to report the error, so the caller sees a null-pointer failure
 * instead of the error the server actually sent.
 *
 * <p>SDK internals.
 */
public final class ResponseMaps {

    private ResponseMaps() {
    }

    /**
     * @param source the decoded map, possibly {@code null} and possibly holding
     *               null values
     * @return an unmodifiable copy that preserves wire order
     */
    public static Map<String, Object> immutableCopy(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
