package com.lunixi.sdk.bankconnect;

import java.util.Map;

import com.lunixi.sdk.exception.ConfigurationException;
import com.lunixi.sdk.http.Envelope;
import com.lunixi.sdk.internal.ApiClient;
import com.lunixi.sdk.internal.Checks;
import com.lunixi.sdk.internal.CursorPages;

/**
 * Currency exchange between two of your accounts ({@code /api/v1/bank-connect/fx}), {@code client.bankConnect().fx()}.
 *
 * <p>Available on a connection whose {@code capabilities} include {@code FX_EXCHANGE}. The two accounts are active
 * accounts of the same connection in different currencies. Same scope and IP allowlist rule as transfers.
 *
 * <p>Flow: {@link #listRates} (optional) → {@link #createQuote} → {@link #createOrder} with the quote id before
 * {@code quote.expiresAt} (the bank sets the validity, typically under two minutes; see {@code validForSeconds}). The
 * order is {@code SUBMITTED} and queued; follow it with {@link #getOrder}. There is no webhook for exchange orders.
 */
public final class BankFxClient {

    private final ApiClient api;

    BankFxClient(ApiClient api) {
        this.api = api;
    }

    /**
     * Current rates on the connection ({@code GET /fx/rates}); rates are decimal strings.
     *
     * @param connectionId the connection id
     * @return {@code {items, refreshSeconds, fetchedAt}}
     */
    public Map<String, Object> listRates(String connectionId) {
        return Envelope.data(BankTransferClient.invoke(api, "listFxRates", Map.of(),
                Map.of("connectionId", Checks.text(connectionId, "connectionId")), null, null));
    }

    /**
     * Quotes an exchange ({@code POST /fx/quotes}, HTTP 201). Nothing is booked; a quote is used by one order.
     *
     * @param request {@code sourceAccountId} (you sell from it), {@code targetAccountId} (you buy into it) and exactly
     *                one of {@code sourceAmountMinor} / {@code targetAmountMinor} (positive minor-unit string of that
     *                account's currency)
     * @return the quote ({@code data})
     */
    public Map<String, Object> createQuote(Map<String, ?> request) {
        Map<String, Object> body = BankTransferClient.checkBody("createFxQuote", request);
        if (body.containsKey("sourceAmountMinor") == body.containsKey("targetAmountMinor")) {
            throw new ConfigurationException("createFxQuote takes exactly one of sourceAmountMinor and "
                    + "targetAmountMinor.");
        }
        if (body.get("sourceAccountId").equals(body.get("targetAccountId"))) {
            throw new ConfigurationException("createFxQuote needs two different accounts.");
        }
        return Envelope.data(BankTransferClient.invoke(api, "createFxQuote", Map.of(), null, body, null));
    }

    /** Same as {@link #createOrder(Map, String)} with a generated {@code Idempotency-Key}. */
    public Map<String, Object> createOrder(Map<String, ?> request) {
        return createOrder(request, null);
    }

    /**
     * Executes a quote ({@code POST /fx/orders}, HTTP 201): {@code {order, replayed}}. The key follows the same rule
     * as {@link BankTransferClient#create(Map, String)}: generated when {@code null}, the same key on a retry of a
     * network error or a 5xx. 409 {@code bank_connect.fx.quote_expired} / {@code bank_connect.fx.quote_used}: ask for
     * a new quote.
     *
     * @param request        {@code quoteId}, optional {@code description} (at most 140 characters)
     * @param idempotencyKey 8–200 printable ASCII characters, or {@code null} to generate one
     * @return {@code {order, replayed}}
     */
    public Map<String, Object> createOrder(Map<String, ?> request, String idempotencyKey) {
        Map<String, Object> body = BankTransferClient.checkBody("createFxOrder", request);
        if (body.containsKey("description")) {
            BankTransferClient.checkDescription(body.get("description"));
        }
        return Envelope.data(BankTransferClient.invoke(api, "createFxOrder", Map.of(), null, body,
                BankTransferClient.idempotencyKeyFor(idempotencyKey)));
    }

    /** @return the first page of exchange orders */
    public BankConnectPage listOrders() {
        return listOrders(Map.of());
    }

    /**
     * Exchange orders, newest first ({@code GET /fx/orders}). Filters: {@code status} (one or a list of
     * {@link BankConnectValues.FxOrderStatus}), {@code limit}, {@code cursor}.
     */
    public BankConnectPage listOrders(Map<String, ?> filters) {
        return new BankConnectPage(BankTransferClient.invoke(api, "listFxOrders", Map.of(),
                BankTransferClient.query(filters, "listFxOrders"), null, null));
    }

    /** Every exchange order matching the filters, following the cursors. */
    public Iterable<Map<String, Object>> iterateOrders(Map<String, ?> filters) {
        return CursorPages.iterate(filters, this::listOrders, BankConnectPage::items, BankConnectPage::continuation);
    }

    /** One exchange order; 404 {@code bank_connect.fx.order_not_found} for an unknown id. */
    public Map<String, Object> getOrder(String orderId) {
        return Envelope.data(BankTransferClient.invoke(api, "getFxOrder",
                Map.of("orderId", Checks.text(orderId, "orderId")), null, null, null));
    }
}
