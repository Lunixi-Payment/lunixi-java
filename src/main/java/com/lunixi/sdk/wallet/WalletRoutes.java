package com.lunixi.sdk.wallet;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.lunixi.sdk.http.Route;

/**
 * The single description of every wallet route the SDK calls ({@code /api/v1/wallet/*}): method, path template,
 * whether the gateway requires an {@code Idempotency-Key}, and the body and query fields the gateway DTO accepts.
 * {@link WalletClient} checks each call against it before sending (the gateway runs with
 * {@code forbidNonWhitelisted}, so an unknown body key would be a 400). The table is the Node SDK's
 * {@code WALLET_ROUTES}, row for row, and the shared catalog's {@code WALLET_ROUTES} group.
 */
public final class WalletRoutes {

    /** Minor units as a digit string: {@code "12550"} is 125.50 TRY. Never a JSON number. */
    public static final Pattern AMOUNT_PATTERN = Pattern.compile("^\\d{1,18}$");
    /** The gateway trims the header and accepts 1–128 characters. */
    public static final int IDEMPOTENCY_KEY_MAX = 128;
    /** Body fields that carry a minor-unit amount string. */
    public static final List<String> AMOUNT_FIELDS = List.of("amount");

    /**
     * The fields of a nested row / item list the gateway validates element by element.
     *
     * @param field    the body field holding the list
     * @param required fields each element needs
     * @param optional other fields an element may carry
     */
    public record NestedFields(String field, List<String> required, List<String> optional) {
        public NestedFields {
            required = List.copyOf(required);
            optional = List.copyOf(optional);
        }
    }

    /** Nested list rules by route name. */
    public static final Map<String, NestedFields> NESTED_FIELDS = Map.of(
            "importCorporateEmployees", new NestedFields("rows", List.of("phone"),
                    List.of("displayName", "externalCustomerId", "kycRef")),
            "offboardCorporateEmployees", new NestedFields("rows", List.of(),
                    List.of("endUserId", "externalCustomerId")),
            "createBulkPayout", new NestedFields("items", List.of("targetType", "targetRef", "amount"),
                    List.of("beneficiaryName")));

    /** Every route by name, in the Node table's order. */
    public static final Map<String, Route> ROUTES;

    private static final List<String> PAGE_QUERY = List.of("limit", "cursor");
    private static final String BASE = "/api/v1/wallet";
    private static final String[] IBAN_REQUIRED = {
        "endUserId", "sourceWalletAccountId", "beneficiaryName", "iban", "amount", "currency"};
    private static final String[] IBAN_INITIATE_OPTIONAL = {
        "bankCode", "paymentPurpose", "scheduleVersionId", "otpDestination", "otpChannel", "locale"};

    static {
        Map<String, Route> routes = new LinkedHashMap<>();
        routes.put("ping", get("/ping"));

        routes.put("listMyPrograms", get("/programs/me"));
        routes.put("setCollectionAnchor", route("PUT", "/programs/:programId/collection-anchor")
                .withOptional("collectionEndUserId", "reason", "expectedVersion"));

        routes.put("createEndUser", post("/end-users").keyed()
                .withRequired("programId", "accountType", "phone")
                .withOptional("externalCustomerId", "kycRef", "kycLevelCache", "displayName", "corporateAccountId",
                        "defaultCurrency"));
        routes.put("listEndUsers", get("/end-users").withQuery(List.of("programId", "accountType", "status",
                "corporateAccountId", "merchantCategoryState"), PAGE_QUERY.toArray(String[]::new)));
        routes.put("getEndUserByExternalId", get("/end-users/by-external/:externalCustomerId")
                .withQuery("programId"));
        routes.put("getEndUser", get("/end-users/:endUserId"));
        routes.put("freezeEndUser", post("/end-users/:endUserId/freeze").keyed().withOptional("reason"));
        routes.put("getBalances", get("/end-users/:endUserId/balances"));
        routes.put("getKycLevel", get("/end-users/:endUserId/kyc-level"));
        routes.put("updateKycLevel", route("PUT", "/end-users/:endUserId/kyc-level").keyed()
                .withRequired("kycLevel", "sourceRef")
                .withOptional("externalCustomerId", "programId", "reason", "effectiveAt", "expectedCurrentLevel"));
        routes.put("getLimits", get("/end-users/:endUserId/limits").withQuery("operationType", "currency"));
        routes.put("createWallet", post("/end-users/:endUserId/wallets").keyed().withOptional("name", "currencies"));

        routes.put("createWalletAccount", post("/wallets/:walletId/accounts").keyed().withRequired("currency"));
        routes.put("lookupWallet", post("/wallets/lookup").withOptional("programId", "phone", "walletNo"));

        routes.put("listDepositInstructions", get("/end-users/:endUserId/deposit-instructions"));
        routes.put("createDepositInstruction", post("/end-users/:endUserId/deposit-instructions")
                .withRequired("walletAccountId").withOptional("currency"));
        routes.put("simulateInboundCredit", post("/bank-integrations/inbound-credits")
                .withRequired("programId", "railId", "amount", "currency", "rawReference")
                .withOptional("senderName", "senderIban", "creditedIban", "receiptNumber"));

        routes.put("createCorporateAccount", post("/corporate-accounts").keyed()
                .withRequired("programId", "code", "name", "ownerEndUserId").withOptional("fundingCurrency"));
        routes.put("listCorporateAccounts", get("/corporate-accounts")
                .withQuery(List.of("programId", "status"), PAGE_QUERY.toArray(String[]::new)));
        routes.put("importCorporateEmployees", post("/corporate-accounts/:corporateAccountId/employees/import")
                .keyed().withRequired("rows"));
        routes.put("offboardCorporateEmployees", post("/corporate-accounts/:corporateAccountId/employees/offboard")
                .keyed().withRequired("rows").withOptional("reason"));

        routes.put("quoteFees", post("/fees/quote")
                .withRequired("programId", "operationType", "currency", "amount").withOptional("at"));

        routes.put("quoteW2W", post("/transfers/w2w/quote")
                .withRequired("endUserId", "sourceWalletAccountId", "amount", "currency")
                .withOptional("destinationWalletNo", "destinationPhone", "paymentPurpose"));
        routes.put("initiateW2W", post("/transfers/w2w").keyed()
                .withRequired("endUserId", "sourceWalletAccountId", "amount", "currency")
                .withOptional("destinationWalletNo", "destinationPhone", "paymentPurpose", "scheduleVersionId",
                        "otpDestination", "otpChannel", "locale"));
        routes.put("completeW2W", complete("/transfers/w2w/:operationId/complete"));

        routes.put("quoteW2Iban", post("/transfers/w2iban/quote").withRequired(IBAN_REQUIRED)
                .withOptional("bankCode", "paymentPurpose"));
        routes.put("initiateW2Iban", post("/transfers/w2iban").keyed().withRequired(IBAN_REQUIRED)
                .withOptional(IBAN_INITIATE_OPTIONAL));
        routes.put("completeW2Iban", complete("/transfers/w2iban/:operationId/complete"));

        routes.put("quoteBankWithdrawal", post("/withdrawals/bank/quote").withRequired(IBAN_REQUIRED)
                .withOptional("bankCode", "paymentPurpose"));
        routes.put("initiateBankWithdrawal", post("/withdrawals/bank").keyed().withRequired(IBAN_REQUIRED)
                .withOptional(IBAN_INITIATE_OPTIONAL));
        routes.put("completeBankWithdrawal", complete("/withdrawals/bank/:operationId/complete"));
        routes.put("getWithdrawal", get("/withdrawals/:operationId"));

        routes.put("listOperations", get("/operations").withQuery(List.of("endUserId", "walletAccountId", "status",
                "operationType", "destinationWalletAccountId"), PAGE_QUERY.toArray(String[]::new)));
        routes.put("getOperation", get("/operations/:operationId"));
        routes.put("getOperationReceipt", get("/operations/:operationId/receipt"));

        // The payment provider requires the buyer and billing address (buyer.ip: the user's browser IP);
        // a missing field is 400 payment.order.field_required with the paths in details.fields.
        routes.put("startCardTopup", post("/topups/card").keyed()
                .withRequired("creditWalletAccountId", "amount", "currency", "buyer", "billingAddress")
                .withOptional("fundingMerchantId", "savedCardUserKey", "savedCardToken", "savedConsumerToken",
                        "savedPublicCardStorageToken", "returnUrl", "shippingAddress"));
        routes.put("getTopup", get("/topups/:operationId"));
        // The gateway DTO marks approverPrincipal optional, but the wallet service refuses the refund without it.
        routes.put("refundTopup", post("/topups/:operationId/refund")
                .withRequired("reason", "requesterPrincipal", "approverPrincipal"));
        routes.put("agentTopup", post("/agent-topups").keyed()
                .withRequired("agentId", "creditWalletAccountId", "amount", "currency"));

        routes.put("requestPayment", post("/payments/buy").keyed()
                .withRequired("amount", "currency", "paymentPurpose")
                .withOptional("buyerWalletNo", "buyerPhone", "orderRef", "cashierRef", "otpDestination",
                        "otpChannel", "locale"));
        routes.put("approvePayment", post("/payments/:operationId/approve").keyed()
                .withRequired("buyerEndUserId", "integrityHash", "nonce").withOptional("otpCode",
                        "trustedActionToken"));
        routes.put("collectByCode", post("/collect").keyed().withRequired("code", "amount", "currency")
                .withOptional("orderRef", "paymentPurpose", "locale"));
        routes.put("getPayment", get("/payments/:operationId"));
        routes.put("getBusinessCommission", get("/business-accounts/:businessAccountId/commission"));

        routes.put("createQr", post("/qr").keyed().withRequired("qrType", "currency")
                .withOptional("targetEndUserId", "targetExternalCustomerId", "programId", "amount",
                        "expiresInSeconds", "metadata"));
        routes.put("listQr", get("/qr").withQuery(List.of("programId", "qrType", "status", "targetEndUserId"),
                PAGE_QUERY.toArray(String[]::new)));
        routes.put("getQr", get("/qr/:qrId"));
        routes.put("getQrImage", get("/qr/:qrId/image").asBinary().withQuery("format", "scale"));
        routes.put("revokeQr", post("/qr/:qrId/revoke"));
        routes.put("parseQr", post("/qr/parse").withRequired("payload"));

        routes.put("createBulkPayout", post("/bulk-payouts").keyed()
                .withRequired("programId", "sourceWalletAccountId", "currency", "items").withOptional("reason"));
        routes.put("listBulkPayouts", get("/bulk-payouts").withQuery("programId", "status", "limit"));
        routes.put("getBulkPayout", get("/bulk-payouts/:batchId"));
        routes.put("listBulkPayoutItems", get("/bulk-payouts/:batchId/items").withQuery("status", "limit"));
        routes.put("submitBulkPayout", post("/bulk-payouts/:batchId/submit"));
        routes.put("approveBulkPayout", post("/bulk-payouts/:batchId/approve").withRequired("approve")
                .withOptional("reason"));
        routes.put("rejectBulkPayout", post("/bulk-payouts/:batchId/reject").withOptional("reason"));
        routes.put("processBulkPayout", post("/bulk-payouts/:batchId/process"));
        routes.put("retryFailedBulkPayout", post("/bulk-payouts/:batchId/retry-failed"));
        ROUTES = Collections.unmodifiableMap(routes);
    }

    private WalletRoutes() {
    }

    /**
     * @param name a route name, e.g. {@code createEndUser}
     * @return the route
     * @throws IllegalArgumentException for an unknown name
     */
    public static Route route(String name) {
        Route route = ROUTES.get(name);
        if (route == null) {
            throw new IllegalArgumentException("Unknown wallet route " + name);
        }
        return route;
    }

    private static Route complete(String path) {
        return post(path).keyed().withRequired("endUserId", "operationId", "integrityHash", "nonce")
                .withOptional("otpCode", "trustedActionToken");
    }

    private static Route get(String path) {
        return route("GET", path);
    }

    private static Route post(String path) {
        return route("POST", path);
    }

    private static Route route(String method, String path) {
        return Route.of(method, BASE + path);
    }
}
