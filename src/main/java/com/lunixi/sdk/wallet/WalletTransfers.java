package com.lunixi.sdk.wallet;

import java.util.Map;

import com.lunixi.sdk.http.Envelope;

/**
 * Wallet-to-wallet transfers and transfers to an IBAN ({@code client.wallet().transfers()}).
 *
 * <p>Money moves in two steps: {@code initiate…} reserves the limit and returns {@code {operationId, integrityHash,
 * nonce, otpRequired, …}}; {@code complete…} with the same {@code integrityHash} and {@code nonce} (and the OTP when
 * {@code otpRequired}) commits it. The complete calls put {@code operationId} into the body themselves. Quote first
 * to show the fee; pass its {@code scheduleVersionId} to {@code initiate…} to hold that fee. Every call names the end
 * user it moves money for ({@code endUserId}).
 */
public final class WalletTransfers {

    private final WalletInvoker invoker;

    WalletTransfers(WalletInvoker invoker) {
        this.invoker = invoker;
    }

    /** Quotes a wallet-to-wallet transfer ({@code POST /wallet/transfers/w2w/quote}). */
    public Map<String, Object> quoteW2W(Map<String, ?> request) {
        return Envelope.data(invoker.call("quoteW2W", Map.of(), null, request, null));
    }

    /** Starts a wallet-to-wallet transfer ({@code POST /wallet/transfers/w2w}; {@code Idempotency-Key} required). */
    public Map<String, Object> initiateW2W(Map<String, ?> request, String idempotencyKey) {
        return Envelope.data(invoker.call("initiateW2W", Map.of(), null, request, idempotencyKey));
    }

    /**
     * Commits a wallet-to-wallet transfer ({@code POST /wallet/transfers/w2w/{operationId}/complete}).
     *
     * @param operationId    the operation id from {@link #initiateW2W}
     * @param request        {@code endUserId}, {@code integrityHash}, {@code nonce}; optional {@code otpCode},
     *                       {@code trustedActionToken}
     * @param idempotencyKey required
     * @return the operation ({@code data})
     */
    public Map<String, Object> completeW2W(String operationId, Map<String, ?> request, String idempotencyKey) {
        return Envelope.data(invoker.call("completeW2W", WalletInvoker.path("operationId", operationId), null,
                WalletInvoker.withOperationId(operationId, request), idempotencyKey));
    }

    /** Quotes a transfer to an IBAN ({@code POST /wallet/transfers/w2iban/quote}). */
    public Map<String, Object> quoteW2Iban(Map<String, ?> request) {
        return Envelope.data(invoker.call("quoteW2Iban", Map.of(), null, request, null));
    }

    /** Starts a transfer to an IBAN ({@code POST /wallet/transfers/w2iban}; {@code Idempotency-Key} required). */
    public Map<String, Object> initiateW2Iban(Map<String, ?> request, String idempotencyKey) {
        return Envelope.data(invoker.call("initiateW2Iban", Map.of(), null, request, idempotencyKey));
    }

    /** Commits a transfer to an IBAN ({@code POST /wallet/transfers/w2iban/{operationId}/complete}). */
    public Map<String, Object> completeW2Iban(String operationId, Map<String, ?> request, String idempotencyKey) {
        return Envelope.data(invoker.call("completeW2Iban", WalletInvoker.path("operationId", operationId), null,
                WalletInvoker.withOperationId(operationId, request), idempotencyKey));
    }
}
