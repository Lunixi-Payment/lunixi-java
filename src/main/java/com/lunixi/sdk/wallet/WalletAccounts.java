package com.lunixi.sdk.wallet;

import java.util.Map;

import com.lunixi.sdk.http.Envelope;

/** Wallets and their currency accounts ({@code client.wallet().wallets()}). */
public final class WalletAccounts {

    private final WalletInvoker invoker;

    WalletAccounts(WalletInvoker invoker) {
        this.invoker = invoker;
    }

    /**
     * Adds a currency account to a wallet ({@code POST /wallet/wallets/{walletId}/accounts}).
     *
     * @param walletId       the wallet id
     * @param currency       the account currency
     * @param idempotencyKey required
     * @return the account ({@code data})
     */
    public Map<String, Object> createAccount(String walletId, String currency, String idempotencyKey) {
        return Envelope.data(invoker.call("createWalletAccount", WalletInvoker.path("walletId", walletId), null,
                WalletInvoker.compact("currency", currency), idempotencyKey));
    }

    /**
     * Resolves a wallet number or phone to {@code {exists, walletNo, maskedName}} before a transfer
     * ({@code POST /wallet/wallets/lookup}).
     *
     * @param request {@code programId}, {@code phone}, {@code walletNo} (all optional)
     * @return the lookup result ({@code data})
     */
    public Map<String, Object> lookup(Map<String, ?> request) {
        return Envelope.data(invoker.call("lookupWallet", Map.of(), null, request, null));
    }
}
