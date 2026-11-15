package com.lunixi.sdk.wallet;

import java.util.Map;

import com.lunixi.sdk.http.Envelope;
import com.lunixi.sdk.internal.CursorPages;

/** Wallet operations and receipts ({@code client.wallet().operations()}). */
public final class WalletOperations {

    private final WalletInvoker invoker;

    WalletOperations(WalletInvoker invoker) {
        this.invoker = invoker;
    }

    /** @return the first page of operations */
    public WalletPage list() {
        return list(Map.of());
    }

    /**
     * Operations ({@code GET /wallet/operations}). Filters: {@code endUserId}, {@code walletAccountId},
     * {@code status}, {@code operationType}, {@code destinationWalletAccountId}, {@code limit}, {@code cursor}.
     *
     * @param filters the filters
     * @return the page
     */
    public WalletPage list(Map<String, ?> filters) {
        return new WalletPage(invoker.call("listOperations", Map.of(), filters, null, null));
    }

    /** Every operation matching the filters, following the cursors. */
    public Iterable<Map<String, Object>> iterate(Map<String, ?> filters) {
        return CursorPages.iterate(filters, this::list, WalletPage::items, page -> page.nextCursor().orElse(null));
    }

    /** @return {@code {operation, history}} ({@code GET /wallet/operations/{operationId}}) */
    public Map<String, Object> get(String operationId) {
        return Envelope.data(invoker.call("getOperation", WalletInvoker.path("operationId", operationId)));
    }

    /** @return the operation's receipt ({@code GET /wallet/operations/{operationId}/receipt}) */
    public Map<String, Object> getReceipt(String operationId) {
        return Envelope.data(invoker.call("getOperationReceipt", WalletInvoker.path("operationId", operationId)));
    }
}
