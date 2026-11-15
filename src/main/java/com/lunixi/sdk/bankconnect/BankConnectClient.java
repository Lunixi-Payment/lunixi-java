package com.lunixi.sdk.bankconnect;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.lunixi.sdk.http.Envelope;
import com.lunixi.sdk.internal.ApiClient;
import com.lunixi.sdk.internal.Checks;
import com.lunixi.sdk.internal.CursorPages;
import com.lunixi.sdk.internal.RequestOptions;

/**
 * Bank accounts ({@code /api/v1/bank-connect}, product {@code BANK_CONNECT}), {@code client.bankConnect()}: read your
 * connected bank accounts, balances and transactions.
 *
 * <p>These routes accept the API key's per-request Ed25519 signature only, so every call is signed, reads included.
 * Scopes: {@code bank-connect:view} for banks, connections, accounts and {@link #requestSync}; {@code
 * bank-connect:transactions:view} for balances and transactions (403 {@code API_INSUFFICIENT_SCOPE} otherwise). The
 * key decides the environment: a TEST key reads only test-environment accounts, a LIVE key only live ones.
 * Connecting a bank and entering bank credentials happen in the Lunixi dashboard only.
 *
 * <p>Money movement lives on two sub-clients, available when the connection offers it ({@code capabilities} has
 * {@code OUTGOING_TRANSFER} / {@code FX_EXCHANGE}): {@link #transfers()} and {@link #fx()}. They need the
 * {@code bank-connect:transfer} scope and a key with an IP allowlist.
 *
 * <p>Single answers are the envelope's {@code data}; list calls return a {@link BankConnectPage}. Filters
 * ({@link BankConnectValues#FILTERS}) are checked before anything is sent: an unknown filter, a value outside its
 * set, a {@code limit} outside 1–100, more than 100 account ids, a malformed amount or date window throws
 * {@link com.lunixi.sdk.exception.ConfigurationException}.
 */
public final class BankConnectClient {

    private static final String BASE = "/api/v1/bank-connect";

    private final ApiClient api;
    private final BankTransferClient transfers;
    private final BankFxClient fx;

    /**
     * @param api the gateway client
     */
    public BankConnectClient(ApiClient api) {
        this.api = api;
        this.transfers = new BankTransferClient(api);
        this.fx = new BankFxClient(api);
    }

    /** @return outgoing transfers from a connected account ({@code bank-connect:transfer}) */
    public BankTransferClient transfers() {
        return transfers;
    }

    /** @return currency exchange between two of your accounts on one connection ({@code bank-connect:transfer}) */
    public BankFxClient fx() {
        return fx;
    }

    /** @return the first page of the bank catalogue */
    public BankConnectPage listBanks() {
        return listBanks(Map.of());
    }

    /** The bank catalogue: whether each bank can be connected and what is read from it ({@code GET /banks}). */
    public BankConnectPage listBanks(Map<String, ?> filters) {
        return list("/banks", filters, "listBanks");
    }

    /** Every bank of the catalogue, following the cursors. */
    public Iterable<Map<String, Object>> iterateBanks(Map<String, ?> filters) {
        return CursorPages.iterate(filters, this::listBanks, BankConnectPage::items, BankConnectPage::continuation);
    }

    /** @return the first page of your bank connections */
    public BankConnectPage listConnections() {
        return listConnections(Map.of());
    }

    /**
     * Your bank connections, newest first ({@code GET /connections}). Filters: {@code limit}, {@code cursor},
     * {@code status} (one value or a list of {@link BankConnectValues.ConnectionStatus}), {@code bankId}.
     */
    public BankConnectPage listConnections(Map<String, ?> filters) {
        return list("/connections", filters, "listConnections");
    }

    /** Every connection matching the filters, following the cursors. */
    public Iterable<Map<String, Object>> iterateConnections(Map<String, ?> filters) {
        return CursorPages.iterate(filters, this::listConnections, BankConnectPage::items,
                BankConnectPage::continuation);
    }

    /** One connection; 404 {@code bank_connect.connection.not_found} for an unknown one. */
    public Map<String, Object> getConnection(String connectionId) {
        return read("/connections/" + encode(Checks.text(connectionId, "connectionId")));
    }

    /** @return the first page of your connected accounts */
    public BankConnectPage listAccounts() {
        return listAccounts(Map.of());
    }

    /**
     * Your connected accounts, newest first, each with its data {@code freshness} ({@code GET /accounts}). Filters:
     * {@code limit}, {@code cursor}, {@code connectionId}, {@code bankId}, {@code currency}, {@code status}
     * ({@link BankConnectValues.AccountStatus}).
     */
    public BankConnectPage listAccounts(Map<String, ?> filters) {
        return list("/accounts", filters, "listAccounts");
    }

    /** Every account matching the filters, following the cursors. */
    public Iterable<Map<String, Object>> iterateAccounts(Map<String, ?> filters) {
        return CursorPages.iterate(filters, this::listAccounts, BankConnectPage::items,
                BankConnectPage::continuation);
    }

    /** One account; 404 {@code bank_connect.account.not_found} for an unknown one. */
    public Map<String, Object> getAccount(String accountId) {
        return read(accountPath(accountId));
    }

    /**
     * Asks for a fresh read of the account from the bank ({@code POST /accounts/{accountId}/sync}, HTTP 202).
     *
     * <p>It never reads earlier than the bank's minimum interval: when the interval has not passed the answer is
     * {@code SCHEDULED} with {@code scheduledAt}, and a request while one is already waiting joins it. Nothing is
     * written, so no {@code Idempotency-Key} is needed. New transactions arrive by the
     * {@link BankConnectEvents#TRANSACTION_RECEIVED} webhook. 409 {@code bank_connect.connection.not_active} when the
     * connection is not {@code ACTIVE} or {@code DEGRADED}; 429 {@code RATE_LIMITED}
     * ({@link com.lunixi.sdk.exception.RateLimitException} with the {@code Retry-After}) when the key sent too many
     * sync requests in the last minute.
     *
     * @param accountId the account id
     * @return {@code {accountId, syncStatus, scheduledAt, lastSuccessfulSyncAt, minSyncIntervalSeconds}}
     */
    public Map<String, Object> requestSync(String accountId) {
        return Envelope.data(api.request("POST", BASE + accountPath(accountId) + "/sync", null, signed()));
    }

    /** @return the first page of the latest balances */
    public BankConnectPage listBalances() {
        return listBalances(Map.of());
    }

    /**
     * The latest balance of each account ({@code GET /balances}, scope {@code bank-connect:transactions:view}).
     * Without {@code accountId}, every active account of the key's environment. Filters: {@code limit},
     * {@code cursor}, {@code accountId} (one id or a list of at most 100), {@code currency}.
     */
    public BankConnectPage listBalances(Map<String, ?> filters) {
        return list("/balances", filters, "listBalances");
    }

    /** Every balance matching the filters, following the cursors. */
    public Iterable<Map<String, Object>> iterateBalances(Map<String, ?> filters) {
        return CursorPages.iterate(filters, this::listBalances, BankConnectPage::items,
                BankConnectPage::continuation);
    }

    /** @return the first page of transactions */
    public BankConnectPage listTransactions() {
        return listTransactions(Map.of());
    }

    /**
     * Transactions, newest recorded first ({@code GET /transactions}, scope {@code bank-connect:transactions:view}).
     *
     * <p>The order is {@code receivedAt} (when Lunixi recorded the transaction), which never changes, so paging
     * forward never skips a transaction that arrives late with an older bank date. {@code bookedFrom} (inclusive) /
     * {@code bookedTo} (exclusive) filter on the bank date: an {@code Instant}, an {@code OffsetDateTime} or an
     * ISO-8601 string with a time zone, at most 92 days apart; {@code bookedTo} needs {@code bookedFrom}
     * ({@code bookedFrom} alone runs up to now). {@code minAmountMinor} / {@code maxAmountMinor} are inclusive
     * minor-unit strings. {@code direction} ({@link BankConnectValues.TransactionDirection}), {@code canonicalType}
     * (one or a list of {@link BankConnectValues.TransactionType}), {@code accountId} (one id or a list).
     */
    public BankConnectPage listTransactions(Map<String, ?> filters) {
        List<String> allowed = BankConnectValues.FILTERS.get("listTransactions");
        Map<String, String> query = BankConnectRules.values(filters, allowed, "listTransactions");
        BankConnectRules.assertTransactionRules(filters, query);
        return new BankConnectPage(api.request("GET", BASE + "/transactions", null,
                BankConnectRules.query(filters, allowed, "listTransactions")));
    }

    /** Every transaction matching the filters, following the cursors. */
    public Iterable<Map<String, Object>> iterateTransactions(Map<String, ?> filters) {
        return CursorPages.iterate(filters, this::listTransactions, BankConnectPage::items,
                BankConnectPage::continuation);
    }

    /** One transaction with its detail fields ({@code balanceAfterMinor}, {@code documentNo}, {@code bank}). */
    public Map<String, Object> getTransaction(String transactionId) {
        return read("/transactions/" + encode(Checks.text(transactionId, "transactionId")));
    }

    private BankConnectPage list(String path, Map<String, ?> filters, String route) {
        return new BankConnectPage(api.request("GET", BASE + path, null,
                BankConnectRules.query(filters, BankConnectValues.FILTERS.get(route), route)));
    }

    private Map<String, Object> read(String path) {
        return Envelope.data(api.request("GET", BASE + path, null, signed()));
    }

    private static RequestOptions signed() {
        return RequestOptions.builder().stepUp(true).build();
    }

    private static String accountPath(String accountId) {
        return "/accounts/" + encode(Checks.text(accountId, "accountId"));
    }

    static String encode(String segment) {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
