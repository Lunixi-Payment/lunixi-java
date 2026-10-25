package com.lunixi.sdk.auth;

import java.time.Duration;
import java.util.Optional;

/**
 * Holds the short-lived access token between calls.
 *
 * <p>{@link InMemoryTokenStore} is the default and is right for a long-running
 * application. Supply your own — backed by a shared cache — when several
 * instances serve the same account: each process would otherwise sign and fetch
 * its own token, which is an extra round trip per process and needless nonce
 * traffic.
 *
 * <p>Implementations must be safe for concurrent use.
 */
public interface TokenStore {

    /**
     * @param key the cache key
     * @return the cached token, or empty when it is absent or expired
     */
    Optional<String> get(String key);

    /**
     * @param key   the cache key
     * @param value the token
     * @param ttl   how long the token may be served from the cache
     */
    void put(String key, String value, Duration ttl);

    /**
     * Drops a cached token.
     *
     * @param key the cache key
     */
    void remove(String key);
}
