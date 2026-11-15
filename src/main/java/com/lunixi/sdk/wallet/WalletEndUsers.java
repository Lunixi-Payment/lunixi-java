package com.lunixi.sdk.wallet;

import java.util.Map;

import com.lunixi.sdk.http.Envelope;
import com.lunixi.sdk.internal.CursorPages;

/** End users of a wallet program ({@code client.wallet().endUsers()}). */
public final class WalletEndUsers {

    private final WalletInvoker invoker;

    WalletEndUsers(WalletInvoker invoker) {
        this.invoker = invoker;
    }

    /**
     * Creates an end user and their default wallet ({@code POST /wallet/end-users}).
     *
     * @param request        {@code programId}, {@code accountType} ({@link WalletValues.AccountType}), {@code phone};
     *                       optional {@code externalCustomerId}, {@code kycRef}, {@code kycLevelCache},
     *                       {@code displayName}, {@code corporateAccountId}, {@code defaultCurrency}
     * @param idempotencyKey required; reuse it when retrying
     * @return the end user ({@code data})
     */
    public Map<String, Object> create(Map<String, ?> request, String idempotencyKey) {
        return Envelope.data(invoker.call("createEndUser", Map.of(), null, request, idempotencyKey));
    }

    /** @return the first page of end users */
    public WalletPage list() {
        return list(Map.of());
    }

    /**
     * End users ({@code GET /wallet/end-users}). Filters: {@code programId}, {@code accountType}, {@code status},
     * {@code corporateAccountId}, {@code merchantCategoryState}, {@code limit}, {@code cursor}.
     *
     * @param filters the filters
     * @return the page
     */
    public WalletPage list(Map<String, ?> filters) {
        return new WalletPage(invoker.call("listEndUsers", Map.of(), filters, null, null));
    }

    /** Every end user matching the filters, following the cursors. */
    public Iterable<Map<String, Object>> iterate(Map<String, ?> filters) {
        return CursorPages.iterate(filters, this::list, WalletPage::items, page -> page.nextCursor().orElse(null));
    }

    /** @return one end user ({@code GET /wallet/end-users/{endUserId}}) */
    public Map<String, Object> get(String endUserId) {
        return Envelope.data(invoker.call("getEndUser", WalletInvoker.path("endUserId", endUserId)));
    }

    /** Finds an end user by the merchant's own customer id. */
    public Map<String, Object> getByExternalId(String externalCustomerId) {
        return getByExternalId(externalCustomerId, null);
    }

    /**
     * Finds an end user by the merchant's own customer id ({@code GET /wallet/end-users/by-external/{id}}).
     *
     * @param externalCustomerId your customer id
     * @param programId          narrows the search to one program, or {@code null}
     * @return the end user ({@code data})
     */
    public Map<String, Object> getByExternalId(String externalCustomerId, String programId) {
        return Envelope.data(invoker.call("getEndUserByExternalId",
                WalletInvoker.path("externalCustomerId", externalCustomerId), WalletInvoker.compact("programId",
                        programId), null, null));
    }

    /** Same as {@link #freeze(String, Map, String)} without a reason. */
    public Map<String, Object> freeze(String endUserId, String idempotencyKey) {
        return freeze(endUserId, Map.of(), idempotencyKey);
    }

    /**
     * Freezes an end user ({@code POST /wallet/end-users/{endUserId}/freeze}).
     *
     * @param endUserId      the end user id
     * @param request        optional {@code reason}
     * @param idempotencyKey required
     * @return the end user ({@code data})
     */
    public Map<String, Object> freeze(String endUserId, Map<String, ?> request, String idempotencyKey) {
        return Envelope.data(invoker.call("freezeEndUser", WalletInvoker.path("endUserId", endUserId), null, request,
                idempotencyKey));
    }

    /** @return the balances of the end user's accounts ({@code GET …/balances}) */
    public Map<String, Object> getBalances(String endUserId) {
        return Envelope.data(invoker.call("getBalances", WalletInvoker.path("endUserId", endUserId)));
    }

    /** @return the end user's KYC level ({@code GET …/kyc-level}) */
    public Map<String, Object> getKycLevel(String endUserId) {
        return Envelope.data(invoker.call("getKycLevel", WalletInvoker.path("endUserId", endUserId)));
    }

    /**
     * Records the KYC level from your own verification ({@code PUT …/kyc-level}, permission
     * {@code wallet:enduser:kyc-override}).
     *
     * @param endUserId      the end user id
     * @param request        {@code kycLevel} ({@link WalletValues.KycLevel}), {@code sourceRef} (your KYC session
     *                       id); optional {@code externalCustomerId}, {@code programId}, {@code reason},
     *                       {@code effectiveAt}, {@code expectedCurrentLevel}
     * @param idempotencyKey required
     * @return the KYC level ({@code data})
     */
    public Map<String, Object> updateKycLevel(String endUserId, Map<String, ?> request, String idempotencyKey) {
        return Envelope.data(invoker.call("updateKycLevel", WalletInvoker.path("endUserId", endUserId), null,
                request, idempotencyKey));
    }

    /** @return the limit usage of every operation type */
    public Map<String, Object> getLimits(String endUserId) {
        return getLimits(endUserId, Map.of());
    }

    /**
     * Limit usage per operation type ({@code GET …/limits}).
     *
     * @param endUserId the end user id
     * @param filters   {@code operationType} and {@code currency} narrow the answer
     * @return the limits ({@code data})
     */
    public Map<String, Object> getLimits(String endUserId, Map<String, ?> filters) {
        return Envelope.data(invoker.call("getLimits", WalletInvoker.path("endUserId", endUserId), filters, null,
                null));
    }

    /**
     * Creates another wallet for the end user ({@code POST …/wallets}).
     *
     * @param endUserId      the end user id
     * @param request        optional {@code name}, {@code currencies}
     * @param idempotencyKey required
     * @return the wallet ({@code data})
     */
    public Map<String, Object> createWallet(String endUserId, Map<String, ?> request, String idempotencyKey) {
        return Envelope.data(invoker.call("createWallet", WalletInvoker.path("endUserId", endUserId), null, request,
                idempotencyKey));
    }
}
