package com.lunixi.sdk.wallet;

import java.util.Map;

import com.lunixi.sdk.http.Envelope;

/**
 * Withdrawals to the end user's own bank account ({@code client.wallet().withdrawals()}): the same two steps as
 * {@link WalletTransfers}.
 */
public final class WalletWithdrawals {

    private final WalletInvoker invoker;

    WalletWithdrawals(WalletInvoker invoker) {
        this.invoker = invoker;
    }

    /** Quotes a withdrawal ({@code POST /wallet/withdrawals/bank/quote}). */
    public Map<String, Object> quote(Map<String, ?> request) {
        return Envelope.data(invoker.call("quoteBankWithdrawal", Map.of(), null, request, null));
    }

    /** Starts a withdrawal ({@code POST /wallet/withdrawals/bank}; {@code Idempotency-Key} required). */
    public Map<String, Object> initiate(Map<String, ?> request, String idempotencyKey) {
        return Envelope.data(invoker.call("initiateBankWithdrawal", Map.of(), null, request, idempotencyKey));
    }

    /** Commits a withdrawal ({@code POST /wallet/withdrawals/bank/{operationId}/complete}). */
    public Map<String, Object> complete(String operationId, Map<String, ?> request, String idempotencyKey) {
        return Envelope.data(invoker.call("completeBankWithdrawal", WalletInvoker.path("operationId", operationId),
                null, WalletInvoker.withOperationId(operationId, request), idempotencyKey));
    }

    /** @return {@code {operation, history}} ({@code GET /wallet/withdrawals/{operationId}}) */
    public Map<String, Object> get(String operationId) {
        return Envelope.data(invoker.call("getWithdrawal", WalletInvoker.path("operationId", operationId)));
    }
}
