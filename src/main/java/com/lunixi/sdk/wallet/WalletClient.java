package com.lunixi.sdk.wallet;

import java.util.Map;

import com.lunixi.sdk.http.Envelope;
import com.lunixi.sdk.internal.ApiClient;

/**
 * Wallet ({@code /api/v1/wallet/*}): the merchant server-to-server surface of the closed-loop wallet,
 * {@code client.wallet()}.
 *
 * <p>Every route is authenticated by the API key's per-request Ed25519 signature, so every call is sent signed,
 * reads included; the merchant, its programs and its end users come from the signing key. The key also needs the
 * {@code WALLET_SERVICE} product (403 {@code PRODUCT_NOT_ENABLED} otherwise) and the wallet permission behind the
 * route (403 {@code WALLET_INSUFFICIENT_SCOPE} otherwise): {@code wallet:*:read} for reads,
 * {@code wallet:program|enduser|limit|fee:manage} for set-up, {@code wallet:operation:intervene} for moving money
 * inside the wallet, {@code wallet:bank-payout:operate} for IBAN transfers and bank withdrawals,
 * {@code wallet:qr:manage}, {@code wallet:bulk:create}, {@code wallet:bulk:approve},
 * {@code wallet:enduser:kyc-override} for the KYC level.
 *
 * <p>Calls are checked against {@link WalletRoutes} before sending: an unknown body field or filter, a missing
 * required field, an amount that is not a minor-unit digit string ({@code "12550"} = 125.50 TRY) or a missing
 * {@code Idempotency-Key} throws {@link com.lunixi.sdk.exception.ConfigurationException}. A call that moves money for
 * an end user names that end user ({@code endUserId}, {@code buyerEndUserId}); the source account must belong to
 * them or the answer is 404 {@code WALLET_NOT_FOUND}.
 *
 * <p>Single answers are the envelope's {@code data}; list calls return a {@link WalletPage}.
 */
public final class WalletClient {

    private final WalletInvoker invoker;
    private final WalletPrograms programs;
    private final WalletEndUsers endUsers;
    private final WalletAccounts wallets;
    private final WalletDeposits deposits;
    private final WalletCorporate corporate;
    private final WalletFees fees;
    private final WalletTransfers transfers;
    private final WalletWithdrawals withdrawals;
    private final WalletOperations operations;
    private final WalletTopups topups;
    private final WalletPayments payments;
    private final WalletQr qr;
    private final WalletBulkPayouts bulkPayouts;

    /**
     * @param api the gateway client
     */
    public WalletClient(ApiClient api) {
        this.invoker = new WalletInvoker(api);
        this.programs = new WalletPrograms(invoker);
        this.endUsers = new WalletEndUsers(invoker);
        this.wallets = new WalletAccounts(invoker);
        this.deposits = new WalletDeposits(invoker);
        this.corporate = new WalletCorporate(invoker);
        this.fees = new WalletFees(invoker);
        this.transfers = new WalletTransfers(invoker);
        this.withdrawals = new WalletWithdrawals(invoker);
        this.operations = new WalletOperations(invoker);
        this.topups = new WalletTopups(invoker);
        this.payments = new WalletPayments(invoker);
        this.qr = new WalletQr(invoker);
        this.bulkPayouts = new WalletBulkPayouts(invoker);
    }

    /**
     * Checks that the key reaches the wallet: signature, product and a {@code wallet:*:manage} permission
     * ({@code GET /wallet/ping}).
     *
     * @return the answer ({@code data})
     */
    public Map<String, Object> ping() {
        return Envelope.data(invoker.call("ping", Map.of()));
    }

    /** @return the merchant's wallet programs */
    public WalletPrograms programs() {
        return programs;
    }

    /** @return end users, their balances, KYC level and limits */
    public WalletEndUsers endUsers() {
        return endUsers;
    }

    /** @return wallets and their currency accounts */
    public WalletAccounts wallets() {
        return wallets;
    }

    /** @return top-ups by bank transfer */
    public WalletDeposits deposits() {
        return deposits;
    }

    /** @return corporate accounts and their employees */
    public WalletCorporate corporate() {
        return corporate;
    }

    /** @return fee quotes */
    public WalletFees fees() {
        return fees;
    }

    /** @return wallet-to-wallet transfers and transfers to an IBAN */
    public WalletTransfers transfers() {
        return transfers;
    }

    /** @return withdrawals to the end user's own bank account */
    public WalletWithdrawals withdrawals() {
        return withdrawals;
    }

    /** @return operations and receipts */
    public WalletOperations operations() {
        return operations;
    }

    /** @return card and agent top-ups */
    public WalletTopups topups() {
        return topups;
    }

    /** @return payments from the end user to the merchant */
    public WalletPayments payments() {
        return payments;
    }

    /** @return QR codes */
    public WalletQr qr() {
        return qr;
    }

    /** @return bulk payouts */
    public WalletBulkPayouts bulkPayouts() {
        return bulkPayouts;
    }
}
