package com.lunixi.sdk.wallet;

import java.util.Map;

import com.lunixi.sdk.http.Envelope;

/** Top-ups by bank transfer ({@code client.wallet().deposits()}). */
public final class WalletDeposits {

    private final WalletInvoker invoker;

    WalletDeposits(WalletInvoker invoker) {
        this.invoker = invoker;
    }

    /** @return the bank transfer details the end user pays into to top up */
    public Map<String, Object> listInstructions(String endUserId) {
        return Envelope.data(invoker.call("listDepositInstructions", WalletInvoker.path("endUserId", endUserId)));
    }

    /**
     * Creates deposit instructions for one wallet account ({@code POST …/deposit-instructions}).
     *
     * @param endUserId the end user id
     * @param request   {@code walletAccountId}, optional {@code currency}
     * @return the instruction ({@code data})
     */
    public Map<String, Object> createInstruction(String endUserId, Map<String, ?> request) {
        return Envelope.data(invoker.call("createDepositInstruction", WalletInvoker.path("endUserId", endUserId),
                null, request, null));
    }

    /**
     * Test only: books an incoming bank transfer as the bank rail would
     * ({@code POST /wallet/bank-integrations/inbound-credits}).
     *
     * @param request {@code programId}, {@code railId}, {@code amount}, {@code currency}, {@code rawReference};
     *                optional {@code senderName}, {@code senderIban}, {@code creditedIban}, {@code receiptNumber}
     * @return the booking ({@code data})
     */
    public Map<String, Object> simulateInboundCredit(Map<String, ?> request) {
        return Envelope.data(invoker.call("simulateInboundCredit", Map.of(), null, request, null));
    }
}
