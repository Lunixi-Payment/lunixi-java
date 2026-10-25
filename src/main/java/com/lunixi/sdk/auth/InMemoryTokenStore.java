package com.lunixi.sdk.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** The default in-process {@link TokenStore}. */
public final class InMemoryTokenStore implements TokenStore {

    private static final class Entry {
        private final String value;
        private final Instant expiresAt;

        Entry(String value, Instant expiresAt) {
            this.value = value;
            this.expiresAt = expiresAt;
        }
    }

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    @Override
    public Optional<String> get(String key) {
        Entry entry = entries.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (!entry.expiresAt.isAfter(Instant.now())) {
            entries.remove(key, entry);
            return Optional.empty();
        }
        return Optional.of(entry.value);
    }

    @Override
    public void put(String key, String value, Duration ttl) {
        Duration effective = ttl == null || ttl.isNegative() ? Duration.ZERO : ttl;
        entries.put(key, new Entry(value, Instant.now().plus(effective)));
    }

    @Override
    public void remove(String key) {
        entries.remove(key);
    }
}
