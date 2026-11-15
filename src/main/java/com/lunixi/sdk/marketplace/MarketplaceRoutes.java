package com.lunixi.sdk.marketplace;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.lunixi.sdk.http.Route;

/**
 * The marketplace route table ({@code MARKETPLACE_ROUTES} of the Node SDK and the shared catalog): the split charges
 * under {@code /api/v1/payments/marketplace/*} (signed per request, optional {@code Idempotency-Key}) and the dealer,
 * commission, payout and audit routes under {@code /api/v1/marketplace/*} (bearer-only).
 */
public final class MarketplaceRoutes {

    /** Fields of a seller leg: the aggregate amount of one sub-dealer. */
    public static final List<String> SELLER_FIELDS = List.of("subDealerId", "amountMinor", "scenario");

    /**
     * Fields a split card charge ({@code chargeMarketplaceCard2d} / {@code 3d}) needs besides the seller legs: the
     * commercial context of a traceable card payment.
     */
    public static final List<String> CHARGE_REQUIRED_FIELDS = List.of("amount", "currency", "orderId", "card",
            "buyer", "billingAddress", "basketItems");

    /** The routes by operation name, in catalog order. */
    public static final Map<String, Route> ROUTES;

    private static final String MP = "/api/v1/marketplace";
    private static final String PAY = "/api/v1/payments/marketplace";

    static {
        Map<String, Route> routes = new LinkedHashMap<>();
        routes.put("createCheckoutIntent", charge(PAY + "/intents"));
        routes.put("chargeMarketplaceCard2d", charge(PAY + "/2d"));
        routes.put("chargeMarketplaceCard3d", charge(PAY + "/3d"));

        routes.put("createDealer", post("/dealers").withRequired("legalName")
                .withOptional("taxId", "dealerType", "categoryCode", "email", "externalRef"));
        routes.put("bulkCreateDealers", post("/dealers/bulk").withRequired("dealers"));
        routes.put("listDealers", get("/dealers").withQuery("status", "search", "limit", "offset"));
        routes.put("getDealer", get("/dealers/:dealerId"));
        routes.put("updateDealerStatus", Route.of("PUT", MP + "/dealers/:dealerId/status").withRequired("status"));
        routes.put("onboardingToken", post("/dealers/:dealerId/onboarding-token"));
        routes.put("dealerApplication", get("/dealers/:dealerId/application"));
        routes.put("submitDealerApplication", post("/dealers/:dealerId/submit").asFree());
        routes.put("setDealerTerms", post("/dealers/:dealerId/terms").asFree());
        routes.put("signDealerContract", post("/dealers/:dealerId/sign").asFree());
        routes.put("activateDealer", post("/dealers/:dealerId/activate"));
        routes.put("recordDealerKycResult", post("/dealers/:dealerId/kyc-result").asFree());

        routes.put("payoutLedger", get("/payout-ledger").withQuery("status", "subDealerId", "days", "limit"));
        routes.put("transactions", get("/transactions").withQuery("status", "subDealerId", "limit"));
        routes.put("commissionRules", get("/commission-rules").withQuery("limit"));
        routes.put("upsertCommissionRule", post("/commission-rules").asFree());
        routes.put("previewCommissionRule", post("/commission-rules/preview").asFree());
        routes.put("resolveCommission", get("/commission/resolve")
                .withQuery("subDealerId", "categoryCode", "mtdVolumeMinor"));

        routes.put("createPayout", post("/payouts").asFree());
        routes.put("markPayoutPaid", post("/payouts/:payoutId/mark-paid").asFree());
        routes.put("bulkMarkPayoutsPaid", post("/payouts/bulk-mark-paid").asFree());

        routes.put("auditLog", get("/audit-log").withQuery("limit", "type"));
        routes.put("verifyAuditLog", get("/audit-log/verify"));
        ROUTES = Collections.unmodifiableMap(routes);
    }

    private MarketplaceRoutes() {
    }

    private static Route charge(String path) {
        return Route.of("POST", path).signed().optionalKey().asFree();
    }

    private static Route get(String path) {
        return Route.of("GET", MP + path);
    }

    private static Route post(String path) {
        return Route.of("POST", MP + path);
    }

    /**
     * @param name the operation name
     * @return its route
     * @throws IllegalArgumentException for an unknown name
     */
    public static Route route(String name) {
        Route route = ROUTES.get(name);
        if (route == null) {
            throw new IllegalArgumentException("Unknown marketplace route " + name + ".");
        }
        return route;
    }
}
