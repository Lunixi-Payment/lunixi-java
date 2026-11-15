package com.lunixi.sdk.wallet;

import java.util.Map;

import com.lunixi.sdk.http.Envelope;

/** Fee quotes ({@code client.wallet().fees()}). */
public final class WalletFees {

    private final WalletInvoker invoker;

    WalletFees(WalletInvoker invoker) {
        this.invoker = invoker;
    }

    /**
     * The fee, tax and total charge an operation would cost ({@code POST /wallet/fees/quote}).
     *
     * @param request {@code programId}, {@code operationType}, {@code currency}, {@code amount}; optional {@code at}
     * @return the quote ({@code data})
     */
    public Map<String, Object> quote(Map<String, ?> request) {
        return Envelope.data(invoker.call("quoteFees", Map.of(), null, request, null));
    }
}
