package com.lunixi.sdk.payment;

import java.util.Map;
import java.util.function.Function;

/** Native adapter: exclusive order lock, same connection for inbox/order writes, rollback on failure. */
public interface LedgerStore {
    <T> T transaction(String orderKey, Function<RecordAccess, T> operation);
    interface RecordAccess {
        Map<String, Object> load();
        void save(Map<String, Object> record);
        boolean claimEvent(String merchantId, String eventId, String bodyHash);
    }
}
