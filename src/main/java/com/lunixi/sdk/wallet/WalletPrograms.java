package com.lunixi.sdk.wallet;

import java.util.Map;

import com.lunixi.sdk.http.Envelope;

/** Wallet programs of the signing merchant ({@code client.wallet().programs()}). */
public final class WalletPrograms {

    private final WalletInvoker invoker;

    WalletPrograms(WalletInvoker invoker) {
        this.invoker = invoker;
    }

    /** @return the wallet programs of the signing merchant ({@code GET /wallet/programs/me}) */
    public WalletPage listMine() {
        return new WalletPage(invoker.call("listMyPrograms", Map.of()));
    }

    /**
     * Sets the end user whose wallet collects the program's merchant payments
     * ({@code PUT /wallet/programs/{programId}/collection-anchor}). Leaving {@code collectionEndUserId} out clears
     * the anchor.
     *
     * @param programId the program id
     * @param request   {@code collectionEndUserId}, {@code reason}, {@code expectedVersion} (all optional)
     * @return the program ({@code data})
     */
    public Map<String, Object> setCollectionAnchor(String programId, Map<String, ?> request) {
        return Envelope.data(invoker.call("setCollectionAnchor", WalletInvoker.path("programId", programId), null,
                request, null));
    }
}
