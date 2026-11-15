package com.lunixi.sdk.wallet;

import java.util.Map;

import com.lunixi.sdk.http.Envelope;

/** Card and agent top-ups ({@code client.wallet().topups()}). */
public final class WalletTopups {

    private final WalletInvoker invoker;

    WalletTopups(WalletInvoker invoker) {
        this.invoker = invoker;
    }

    /**
     * Starts a card top-up ({@code POST /wallet/topups/card}). Render {@code checkoutFormContent} (or follow
     * {@code redirectUrl} / {@code threeDsHtml}); the wallet is credited when the payment completes
     * ({@code wallet.topup.completed}).
     *
     * @param request        {@code creditWalletAccountId}, {@code amount}, {@code currency}; optional
     *                       {@code fundingMerchantId}, the saved-card tokens and {@code returnUrl}
     * @param idempotencyKey required
     * @return the top-up ({@code data})
     */
    public Map<String, Object> startCard(Map<String, ?> request, String idempotencyKey) {
        return Envelope.data(invoker.call("startCardTopup", Map.of(), null, request, idempotencyKey));
    }

    /** @return {@code {operation, history}} ({@code GET /wallet/topups/{operationId}}) */
    public Map<String, Object> get(String operationId) {
        return Envelope.data(invoker.call("getTopup", WalletInvoker.path("operationId", operationId)));
    }

    /**
     * Refunds a card top-up to the card ({@code POST /wallet/topups/{operationId}/refund}). Four eyes:
     * {@code approverPrincipal} is required and must differ from {@code requesterPrincipal}; binding them to two real
     * people is your approval flow's job.
     *
     * @param operationId the top-up operation id
     * @param request     {@code reason}, {@code requesterPrincipal}, {@code approverPrincipal}
     * @return the refund ({@code data})
     */
    public Map<String, Object> refund(String operationId, Map<String, ?> request) {
        return Envelope.data(invoker.call("refundTopup", WalletInvoker.path("operationId", operationId), null,
                request, null));
    }

    /**
     * Cash top-up through an agent of the program ({@code POST /wallet/agent-topups}).
     *
     * @param request        {@code agentId}, {@code creditWalletAccountId}, {@code amount}, {@code currency}
     * @param idempotencyKey required
     * @return the top-up ({@code data})
     */
    public Map<String, Object> agent(Map<String, ?> request, String idempotencyKey) {
        return Envelope.data(invoker.call("agentTopup", Map.of(), null, request, idempotencyKey));
    }
}
