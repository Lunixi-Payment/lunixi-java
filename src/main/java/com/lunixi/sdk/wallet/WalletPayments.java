package com.lunixi.sdk.wallet;

import java.util.Map;

import com.lunixi.sdk.http.Envelope;

/**
 * The end user pays the merchant ({@code client.wallet().payments()}). The creditor is the wallet terminal bound to
 * the signing key ({@code WALLET_TERMINAL_*} 403 without one); a {@code businessAccountId} in the body is refused.
 */
public final class WalletPayments {

    private final WalletInvoker invoker;

    WalletPayments(WalletInvoker invoker) {
        this.invoker = invoker;
    }

    /**
     * Asks the buyer (by wallet number or phone) to pay ({@code POST /wallet/payments/buy}); approve with
     * {@link #approve}.
     *
     * @param request        {@code amount}, {@code currency}, {@code paymentPurpose}; optional {@code buyerWalletNo},
     *                       {@code buyerPhone}, {@code orderRef}, {@code cashierRef}, {@code otpDestination},
     *                       {@code otpChannel}, {@code locale}
     * @param idempotencyKey required
     * @return the payment ({@code data})
     */
    public Map<String, Object> request(Map<String, ?> request, String idempotencyKey) {
        return Envelope.data(invoker.call("requestPayment", Map.of(), null, request, idempotencyKey));
    }

    /**
     * Approves a requested payment for the buyer ({@code POST /wallet/payments/{operationId}/approve}).
     *
     * @param operationId    the payment operation id
     * @param request        {@code buyerEndUserId}, {@code integrityHash}, {@code nonce}; optional {@code otpCode},
     *                       {@code trustedActionToken}
     * @param idempotencyKey required
     * @return the payment ({@code data})
     */
    public Map<String, Object> approve(String operationId, Map<String, ?> request, String idempotencyKey) {
        return Envelope.data(invoker.call("approvePayment", WalletInvoker.path("operationId", operationId), null,
                request, idempotencyKey));
    }

    /**
     * Collects with the payment code the buyer shows ({@code POST /wallet/collect}).
     *
     * @param request        {@code code}, {@code amount}, {@code currency}; optional {@code orderRef},
     *                       {@code paymentPurpose}, {@code locale}
     * @param idempotencyKey required
     * @return the payment ({@code data})
     */
    public Map<String, Object> collect(Map<String, ?> request, String idempotencyKey) {
        return Envelope.data(invoker.call("collectByCode", Map.of(), null, request, idempotencyKey));
    }

    /** @return {@code {operation, history}} ({@code GET /wallet/payments/{operationId}}) */
    public Map<String, Object> get(String operationId) {
        return Envelope.data(invoker.call("getPayment", WalletInvoker.path("operationId", operationId)));
    }

    /** @return the business account's commission ({@code GET /wallet/business-accounts/{id}/commission}) */
    public Map<String, Object> getCommission(String businessAccountId) {
        return Envelope.data(invoker.call("getBusinessCommission",
                WalletInvoker.path("businessAccountId", businessAccountId)));
    }
}
