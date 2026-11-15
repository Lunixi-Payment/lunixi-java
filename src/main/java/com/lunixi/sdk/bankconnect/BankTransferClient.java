package com.lunixi.sdk.bankconnect;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.lunixi.sdk.exception.ConfigurationException;
import com.lunixi.sdk.http.Envelope;
import com.lunixi.sdk.http.Route;
import com.lunixi.sdk.internal.ApiClient;
import com.lunixi.sdk.internal.Checks;
import com.lunixi.sdk.internal.CursorPages;
import com.lunixi.sdk.internal.RequestOptions;

/**
 * Outgoing bank transfers ({@code /api/v1/bank-connect/transfers}), {@code client.bankConnect().transfers()}.
 *
 * <p>Available on a connection whose {@code capabilities} include {@code OUTGOING_TRANSFER}. Every route, reads
 * included, is signed and needs the {@code bank-connect:transfer} scope; the key must have an IP allowlist and the
 * request must come from a listed IP (403 {@code bank_connect.auth.ip_allowlist_required} /
 * {@code bank_connect.auth.ip_not_allowed}).
 *
 * <p>With an API key there is no e-mail code: the key's scope, the signature and the source IP approve the transfer,
 * so {@link #create} answers {@code SUBMITTED} and the transfer is queued for the bank. Follow it with {@link #get}
 * or the {@link BankTransferEvents} webhooks. The bank chooses the rail; Lunixi does not check the balance — the bank
 * decides. Bodies and filters are checked against {@link BankTransferRoutes} before sending.
 */
public final class BankTransferClient {

    private final ApiClient api;

    BankTransferClient(ApiClient api) {
        this.api = api;
    }

    /**
     * Payment purpose codes the bank accepts on this connection ({@code GET /transfer-purposes}). Pass one as
     * {@code paymentPurpose} to {@link #create}; leave it out for the bank's default.
     *
     * @param connectionId the connection id
     * @return {@code {items: [{code, name, order}]}}
     */
    public Map<String, Object> listPurposes(String connectionId) {
        return Envelope.data(invoke(api, "listTransferPurposes", Map.of(),
                Map.of("connectionId", Checks.text(connectionId, "connectionId")), null, null));
    }

    /** Same as {@link #create(Map, String)} with a generated {@code Idempotency-Key}. */
    public Map<String, Object> create(Map<String, ?> request) {
        return create(request, null);
    }

    /**
     * Creates an outgoing transfer ({@code POST /transfers}, HTTP 201): {@code {transfer, stepUp, replayed}}.
     *
     * <p>The key is generated (UUID v4) when {@code null}; a network error or a 5xx is retried with the same key
     * (up to the configured {@code maxRetries}). Keep your own key (e.g. your payout id) to retry safely across
     * processes: the same key with the same body answers the existing transfer with {@code replayed: true}, the same
     * key with another body is 409 {@code bank_connect.transfer.idempotency_conflict}. The key used is also in
     * {@code transfer.idempotencyKey}.
     *
     * @param request        {@code accountId} (source account), {@code receiverIban} (TR IBAN, at most 42
     *                       characters), {@code receiverName} (2–140 characters), {@code amountMinor} (positive
     *                       minor-unit string: {@code "150075"} = 1,500.75 TRY); optional {@code currency} (TRY),
     *                       {@code paymentPurpose} and {@code description} (at most 140 characters)
     * @param idempotencyKey 8–200 printable ASCII characters, or {@code null} to generate one
     * @return {@code {transfer, stepUp, replayed}}
     */
    public Map<String, Object> create(Map<String, ?> request, String idempotencyKey) {
        Map<String, Object> body = checkBody("createTransfer", request);
        checkTransferBody(body);
        return Envelope.data(invoke(api, "createTransfer", Map.of(), null, body, idempotencyKeyFor(idempotencyKey)));
    }

    /** @return the first page of transfers */
    public BankConnectPage list() {
        return list(Map.of());
    }

    /**
     * Transfers, newest first ({@code GET /transfers}). Filters: {@code accountId}, {@code status} (one or a list of
     * {@link BankConnectValues.TransferStatus}), {@code createdFrom} (inclusive) / {@code createdTo} (exclusive) as an
     * {@code Instant}, an {@code OffsetDateTime} or an ISO-8601 string, {@code limit} 1–100, {@code cursor}.
     */
    public BankConnectPage list(Map<String, ?> filters) {
        return new BankConnectPage(invoke(api, "listTransfers", Map.of(), query(filters, "listTransfers"), null,
                null));
    }

    /** Every transfer matching the filters, following the cursors. */
    public Iterable<Map<String, Object>> iterate(Map<String, ?> filters) {
        return CursorPages.iterate(filters, this::list, BankConnectPage::items, BankConnectPage::continuation);
    }

    /** One transfer; 404 {@code bank_connect.transfer.not_found} for an unknown id. */
    public Map<String, Object> get(String transferId) {
        return Envelope.data(invoke(api, "getTransfer", Map.of("transferId", Checks.text(transferId, "transferId")),
                null, null, null));
    }

    // ── Shared with BankFxClient ──────────────────────────────────────────────────────────────────────────────

    /** Sends one route of {@link BankTransferRoutes}, signed. */
    static Map<String, Object> invoke(ApiClient api, String routeName, Map<String, String> path,
                                      Map<String, String> query, Map<String, Object> body, String idempotencyKey) {
        Route route = BankTransferRoutes.route(routeName);
        RequestOptions.Builder options = RequestOptions.builder().stepUp(true);
        if (query != null) {
            query.forEach(options::query);
        }
        if (route.idempotencyKey()) {
            options.idempotencyKey(idempotencyKey);
        }
        return api.request(route.method(), route.fill(path), body, options.build());
    }

    /** A caller's key is checked; a missing one is generated (UUID v4). */
    static String idempotencyKeyFor(String value) {
        if (value == null || value.isEmpty()) {
            return UUID.randomUUID().toString();
        }
        if (!BankTransferRoutes.IDEMPOTENCY_KEY_PATTERN.matcher(value).matches()) {
            throw new ConfigurationException("idempotencyKey must be 8-200 printable ASCII characters (no spaces).");
        }
        return value;
    }

    /** Whitelists the body against the route: unknown or missing fields fail before sending. */
    static Map<String, Object> checkBody(String routeName, Map<String, ?> request) {
        if (request == null) {
            throw new ConfigurationException(routeName + " body must be an object (a map).");
        }
        Route route = BankTransferRoutes.route(routeName);
        Map<String, Object> body = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : Checks.plainMap(request, routeName + " body").entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if ("".equals(value)) {
                continue;
            }
            if (!route.bodyFields().contains(key)) {
                throw new ConfigurationException(routeName + " does not accept " + key
                        + " (the gateway rejects unknown fields with 400).");
            }
            body.put(key, BankTransferRoutes.AMOUNT_FIELDS.contains(key) ? Checks.minor(value, "'" + key + "'", true)
                    : value);
        }
        java.util.List<String> missing = new java.util.ArrayList<>();
        for (String key : route.required()) {
            if (!body.containsKey(key)) {
                missing.add(key);
            }
        }
        if (!missing.isEmpty()) {
            throw new ConfigurationException(routeName + " requires " + String.join(", ", missing) + ".");
        }
        for (String key : route.required()) {
            if (!BankTransferRoutes.AMOUNT_FIELDS.contains(key)) {
                body.put(key, requireText(key, body.get(key)));
            }
        }
        return body;
    }

    static void checkDescription(Object value) {
        if (!(value instanceof String text) || text.codePointCount(0, text.length())
                > BankTransferRoutes.DESCRIPTION_MAX) {
            throw new ConfigurationException("'description' must be a string of at most "
                    + BankTransferRoutes.DESCRIPTION_MAX + " characters.");
        }
    }

    static Map<String, String> query(Map<String, ?> filters, String routeName) {
        return BankConnectRules.values(filters, BankTransferRoutes.route(routeName).query(), routeName);
    }

    private static void checkTransferBody(Map<String, Object> body) {
        String iban = (String) body.get("receiverIban");
        if (iban.length() > BankTransferRoutes.IBAN_MAX) {
            throw new ConfigurationException("'receiverIban' must be at most " + BankTransferRoutes.IBAN_MAX
                    + " characters.");
        }
        String name = (String) body.get("receiverName");
        int length = name.codePointCount(0, name.length());
        if (length < BankTransferRoutes.RECEIVER_NAME_MIN || length > BankTransferRoutes.RECEIVER_NAME_MAX) {
            throw new ConfigurationException("'receiverName' must be " + BankTransferRoutes.RECEIVER_NAME_MIN + "-"
                    + BankTransferRoutes.RECEIVER_NAME_MAX + " characters.");
        }
        if (body.containsKey("currency")) {
            body.put("currency", Checks.currency(body.get("currency"), "currency"));
        }
        if (body.containsKey("paymentPurpose") && !(body.get("paymentPurpose") instanceof String purpose
                && BankTransferRoutes.PAYMENT_PURPOSE_PATTERN.matcher(purpose).matches())) {
            throw new ConfigurationException("'paymentPurpose' must be a code from listPurposes() (letters, digits, "
                    + "underscore; at most 64).");
        }
        if (body.containsKey("description")) {
            checkDescription(body.get("description"));
        }
    }

    private static String requireText(String key, Object value) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw new ConfigurationException("'" + key + "' must be a non-empty string.");
        }
        return text.trim();
    }
}
