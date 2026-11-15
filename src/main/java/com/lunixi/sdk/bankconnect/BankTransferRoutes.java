package com.lunixi.sdk.bankconnect;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.lunixi.sdk.http.Route;

/**
 * Transfer and exchange routes ({@code /api/v1/bank-connect}, T1–T9): the single source of method, path, body
 * fields, query fields and the {@code Idempotency-Key} rule for {@link BankTransferClient} and {@link BankFxClient}.
 * The table is the Node SDK's {@code BANK_TRANSFER_ROUTES}, row for row, and the shared catalog's group of the same
 * name. Every route is signed and needs {@code bank-connect:transfer} plus a key with an IP allowlist.
 */
public final class BankTransferRoutes {

    /** {@code Idempotency-Key} of a transfer or an exchange order: 8–200 printable ASCII characters. */
    public static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile("^[\\x21-\\x7E]{8,200}$");
    /** {@code paymentPurpose}: a code from {@link BankTransferClient#listPurposes}. */
    public static final Pattern PAYMENT_PURPOSE_PATTERN = Pattern.compile("^[A-Za-z0-9_]{1,64}$");
    /** Longest {@code description} of a transfer or an exchange order. */
    public static final int DESCRIPTION_MAX = 140;
    /** Shortest {@code receiverName} (characters). */
    public static final int RECEIVER_NAME_MIN = 2;
    /** Longest {@code receiverName} (characters). */
    public static final int RECEIVER_NAME_MAX = 140;
    /** Longest {@code receiverIban} the gateway accepts (spaces included); the service checks TR IBAN mod-97. */
    public static final int IBAN_MAX = 42;
    /** Body fields that carry a minor-unit amount string. */
    public static final List<String> AMOUNT_FIELDS = List.of("amountMinor", "sourceAmountMinor", "targetAmountMinor");

    /** Every route by name, in the Node table's order. */
    public static final Map<String, Route> ROUTES;

    private static final String BASE = "/api/v1/bank-connect";
    private static final List<String> PAGE_QUERY = List.of("limit", "cursor");

    static {
        Map<String, Route> routes = new LinkedHashMap<>();
        routes.put("listTransferPurposes", Route.of("GET", BASE + "/transfer-purposes").withQuery("connectionId"));
        routes.put("createTransfer", Route.of("POST", BASE + "/transfers").keyed()
                .withRequired("accountId", "receiverIban", "receiverName", "amountMinor")
                .withOptional("currency", "paymentPurpose", "description"));
        routes.put("listTransfers", Route.of("GET", BASE + "/transfers")
                .withQuery(PAGE_QUERY, "accountId", "status", "createdFrom", "createdTo"));
        routes.put("getTransfer", Route.of("GET", BASE + "/transfers/:transferId"));
        routes.put("listFxRates", Route.of("GET", BASE + "/fx/rates").withQuery("connectionId"));
        routes.put("createFxQuote", Route.of("POST", BASE + "/fx/quotes")
                .withRequired("sourceAccountId", "targetAccountId")
                .withOptional("sourceAmountMinor", "targetAmountMinor"));
        routes.put("createFxOrder", Route.of("POST", BASE + "/fx/orders").keyed()
                .withRequired("quoteId").withOptional("description"));
        routes.put("listFxOrders", Route.of("GET", BASE + "/fx/orders").withQuery(PAGE_QUERY, "status"));
        routes.put("getFxOrder", Route.of("GET", BASE + "/fx/orders/:orderId"));
        ROUTES = Collections.unmodifiableMap(routes);
    }

    private BankTransferRoutes() {
    }

    /**
     * @param name a route name, e.g. {@code createTransfer}
     * @return the route
     * @throws IllegalArgumentException for an unknown name
     */
    public static Route route(String name) {
        Route route = ROUTES.get(name);
        if (route == null) {
            throw new IllegalArgumentException("Unknown bank transfer route " + name);
        }
        return route;
    }
}
