package com.lunixi.sdk.marketplace;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.lunixi.sdk.exception.ConfigurationException;
import com.lunixi.sdk.exception.LunixiApiException;
import com.lunixi.sdk.http.RoutePage;
import com.lunixi.sdk.internal.ApiClient;
import com.lunixi.sdk.internal.Checks;
import com.lunixi.sdk.internal.RouteRequest;
import com.lunixi.sdk.payment.OrderDetails;

/**
 * Marketplace, {@code client.marketplace()}: split charges across sub-dealers, the sub-dealer lifecycle,
 * commissions, payouts and the audit log.
 *
 * <p>Split charges ({@code /api/v1/payments/marketplace/*}) are signed per request and take an optional
 * {@code Idempotency-Key}. A split is aggregate per seller: group the basket by vendor and pass one leg per
 * sub-dealer, {@code {subDealerId, amountMinor, scenario?}}; the legs must add up to {@code amount}, checked before
 * sending. Dealer, commission, payout and audit routes ({@code /api/v1/marketplace/*}) are bearer-only.
 *
 * <p>Card data (card number, expiry, security code) is sent as given and never logged or put in an error message; a
 * direct card charge is PCI DSS SAQ-D scope, so most marketplaces use {@link #createCheckoutIntent} (hosted form).
 *
 * <p>Amounts are canonical minor-unit strings. Every call is checked against {@link MarketplaceRoutes} before
 * sending; a rule violation throws {@link ConfigurationException}. Entity calls return the envelope's {@code data};
 * lists return a {@link RoutePage}.
 */
public final class MarketplaceClient {

    private final RouteRequest routes;

    /**
     * @param api the gateway client
     */
    public MarketplaceClient(ApiClient api) {
        this.routes = new RouteRequest(api, MarketplaceRoutes.ROUTES);
    }

    // ── Split charges ─────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Same as {@link #createCheckoutIntent(Map, List, String, String)} with {@code ALL_OR_NOTHING} and no key.
     *
     * @param request the intent body
     * @param sellers the seller legs
     * @return the answer as it came
     */
    public Map<String, Object> createCheckoutIntent(Map<String, ?> request, List<? extends Map<String, ?>> sellers) {
        return createCheckoutIntent(request, sellers, MarketplaceValues.MultiSellerPolicy.ALL_OR_NOTHING, null);
    }

    /**
     * Hosted-form split checkout ({@code POST /payments/marketplace/intents}, signed): the intent body
     * ({@code amount}, {@code currency}, {@code orderId}, …) plus the seller legs and the policy. Returns the answer
     * as it came ({@code token}, {@code checkoutFormContent}, {@code paymentId} …); an answer without a token or form
     * (a seller not eligible under {@code ALL_OR_NOTHING}) throws {@link LunixiApiException} instead of leaving the
     * buyer on an empty page. The same route as {@code payments().createMarketplaceIntent()}. The order details are
     * required ({@code buyer} without {@code ip}, {@code billingAddress}, {@code basketItems}; {@link OrderDetails}).
     *
     * @param request           the intent body, without {@code sellers} / {@code multiSellerPolicy}
     * @param sellers           the seller legs
     * @param multiSellerPolicy {@code ALL_OR_NOTHING} or {@code PARTIAL_ALLOWED}
     * @param idempotencyKey    the key, or {@code null}
     * @return the answer as it came
     */
    public Map<String, Object> createCheckoutIntent(Map<String, ?> request, List<? extends Map<String, ?>> sellers,
                                                    String multiSellerPolicy, String idempotencyKey) {
        Map<String, Object> body = MarketplaceSplit.splitBody(
                MarketplaceSplit.body(request, "createCheckoutIntent request"), sellers, multiSellerPolicy);
        OrderDetails.assertComplete(body, "A marketplace payment intent", true, true);
        ApiClient.Exchange exchange = routes.exchange("createCheckoutIntent", Map.of(), null, body, idempotencyKey);
        Map<String, Object> response = exchange.envelope();
        Map<String, Object> intent = RouteRequest.mergeRoot(response);
        if (text(intent.get("token")) == null && text(intent.get("checkoutFormContent")) == null) {
            String message = text(intent.get("message"));
            throw new LunixiApiException(message != null ? message
                    : "The marketplace checkout intent did not return a token.", exchange.statusCode(),
                    text(intent.get("code")), response, text(intent.get("requestId")));
        }
        return response;
    }

    /**
     * Same as {@link #chargeMarketplaceCard(Map, boolean, String)} without 3-D Secure and without a key.
     *
     * @param payload the raw charge body
     * @return the answer as it came
     */
    public Map<String, Object> chargeMarketplaceCard(Map<String, ?> payload) {
        return chargeMarketplaceCard(payload, false, null);
    }

    /**
     * Direct split card charge from a raw body ({@code amount}, {@code currency}, {@code orderId}, {@code card},
     * {@code buyer}, {@code billingAddress}, {@code basketItems}, {@code sellers}, {@code multiSellerPolicy},
     * {@code preauth?} …): {@code POST /payments/marketplace/3d} when {@code threeDS} is true, {@code /2d} otherwise.
     * Only the amount rules and the order details ({@link OrderDetails}) are checked here; prefer
     * {@link #chargeMarketplaceCard2d} / {@link #chargeMarketplaceCard3d}, which check the body and the legs.
     *
     * @param payload        the raw charge body
     * @param threeDS        {@code true} for 3-D Secure
     * @param idempotencyKey the key, or {@code null}
     * @return the answer as it came
     */
    public Map<String, Object> chargeMarketplaceCard(Map<String, ?> payload, boolean threeDS, String idempotencyKey) {
        Map<String, Object> body = MarketplaceSplit.body(payload, "chargeMarketplaceCard payload");
        MarketplaceSplit.assertChargeAmount(body);
        OrderDetails.assertComplete(body, "A marketplace card charge", true, false);
        return routes.send(threeDS ? "chargeMarketplaceCard3d" : "chargeMarketplaceCard2d", Map.of(), null, body,
                idempotencyKey);
    }

    /**
     * Same as {@link #chargeMarketplaceCard2d(Map, List, String, String)} with {@code ALL_OR_NOTHING} and no key.
     *
     * @param request the card payment body
     * @param sellers the seller legs
     * @return the answer as it came
     */
    public Map<String, Object> chargeMarketplaceCard2d(Map<String, ?> request, List<? extends Map<String, ?>> sellers) {
        return chargeMarketplaceCard2d(request, sellers, MarketplaceValues.MultiSellerPolicy.ALL_OR_NOTHING, null);
    }

    /**
     * Split card charge without 3-D Secure: the card payment body ({@link MarketplaceRoutes#CHARGE_REQUIRED_FIELDS}
     * required, at least one basket item) plus the seller legs.
     *
     * @param request           the card payment body
     * @param sellers           the seller legs
     * @param multiSellerPolicy {@code ALL_OR_NOTHING} or {@code PARTIAL_ALLOWED}
     * @param idempotencyKey    the key, or {@code null}
     * @return the answer as it came
     */
    public Map<String, Object> chargeMarketplaceCard2d(Map<String, ?> request, List<? extends Map<String, ?>> sellers,
                                                       String multiSellerPolicy, String idempotencyKey) {
        return chargeMarketplaceCard(MarketplaceSplit.chargeBody(request, sellers, multiSellerPolicy), false,
                idempotencyKey);
    }

    /**
     * Same as {@link #chargeMarketplaceCard3d(Map, List, String, String)} with {@code ALL_OR_NOTHING} and no key.
     *
     * @param request the card payment body
     * @param sellers the seller legs
     * @return the answer as it came
     */
    public Map<String, Object> chargeMarketplaceCard3d(Map<String, ?> request, List<? extends Map<String, ?>> sellers) {
        return chargeMarketplaceCard3d(request, sellers, MarketplaceValues.MultiSellerPolicy.ALL_OR_NOTHING, null);
    }

    /**
     * Split card charge with 3-D Secure: the card payment body plus the seller legs.
     *
     * @param request           the card payment body
     * @param sellers           the seller legs
     * @param multiSellerPolicy {@code ALL_OR_NOTHING} or {@code PARTIAL_ALLOWED}
     * @param idempotencyKey    the key, or {@code null}
     * @return the answer as it came
     */
    public Map<String, Object> chargeMarketplaceCard3d(Map<String, ?> request, List<? extends Map<String, ?>> sellers,
                                                       String multiSellerPolicy, String idempotencyKey) {
        return chargeMarketplaceCard(MarketplaceSplit.chargeBody(request, sellers, multiSellerPolicy), true,
                idempotencyKey);
    }

    // ── Sub-dealers ───────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Creates a sub-dealer: {@code legalName}, and optionally {@code taxId}, {@code dealerType}, {@code categoryCode},
     * {@code email}, {@code externalRef}. The dealer then onboards itself (KYC, IBAN, contract) before it is
     * {@code ACTIVE}.
     *
     * @param request the dealer
     * @return the dealer ({@code data})
     */
    public Map<String, Object> createDealer(Map<String, ?> request) {
        return RouteRequest.data(routes.send("createDealer", Map.of(), null, dealerBody(request, "createDealer request"),
                null));
    }

    /**
     * Several dealers at once (an import); each row as in {@link #createDealer(Map)}.
     *
     * @param dealers the dealers
     * @return the answer ({@code data})
     */
    public Map<String, Object> bulkCreateDealers(List<? extends Map<String, ?>> dealers) {
        if (dealers == null || dealers.isEmpty()) {
            throw new ConfigurationException("bulkCreateDealers needs a non-empty list of dealers.");
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < dealers.size(); i++) {
            rows.add(Checks.plainMap(dealers.get(i), "dealers[" + i + "]"));
        }
        return RouteRequest.data(routes.send("bulkCreateDealers", Map.of(), null, Map.of("dealers", rows), null));
    }

    /** @return the first page of dealers */
    public RoutePage listDealers() {
        return listDealers(Map.of());
    }

    /**
     * @param filters {@code status}, {@code search}, {@code limit}, {@code offset}
     * @return one page of dealers
     */
    public RoutePage listDealers(Map<String, ?> filters) {
        return new RoutePage(routes.send("listDealers", Map.of(), filters, null, null));
    }

    /**
     * @param dealerId the dealer id
     * @return the dealer ({@code data})
     */
    public Map<String, Object> getDealer(String dealerId) {
        return RouteRequest.data(routes.send("getDealer", RouteRequest.params("dealerId", dealerId), null, null, null));
    }

    /**
     * @param dealerId the dealer id
     * @param status   one of {@link MarketplaceValues.DealerStatus#ALL}
     * @return the dealer ({@code data})
     */
    public Map<String, Object> updateDealerStatus(String dealerId, String status) {
        if (status == null || !MarketplaceValues.DealerStatus.ALL.contains(status)) {
            throw new ConfigurationException("status must be one of "
                    + String.join(", ", MarketplaceValues.DealerStatus.ALL) + ".");
        }
        return RouteRequest.data(routes.send("updateDealerStatus", RouteRequest.params("dealerId", dealerId), null,
                Map.of("status", status), null));
    }

    /**
     * Self-onboarding link: {@code {token, url, …}} ({@code token} also starts the embedded onboarding SDK);
     * {@link MarketplaceValues#onboardingUrl(Map)} gives the URL.
     *
     * @param dealerId the dealer id
     * @return the link (root and {@code data} fields merged)
     */
    public Map<String, Object> onboardingToken(String dealerId) {
        return RouteRequest.mergeRoot(routes.send("onboardingToken", RouteRequest.params("dealerId", dealerId), null,
                null, null));
    }

    /**
     * @param dealerId the dealer id
     * @return the onboarding / application status ({@code data})
     */
    public Map<String, Object> dealerApplication(String dealerId) {
        return RouteRequest.data(routes.send("dealerApplication", RouteRequest.params("dealerId", dealerId), null,
                null, null));
    }

    /**
     * @param dealerId the dealer id
     * @return the answer ({@code data})
     */
    public Map<String, Object> submitDealerApplication(String dealerId) {
        return submitDealerApplication(dealerId, Map.of());
    }

    /**
     * @param dealerId the dealer id
     * @param payload  the application fields
     * @return the answer ({@code data})
     */
    public Map<String, Object> submitDealerApplication(String dealerId, Map<String, ?> payload) {
        return dealerWrite("submitDealerApplication", dealerId, payload);
    }

    /**
     * Commission / terms definition.
     *
     * @param dealerId the dealer id
     * @param payload  the terms
     * @return the answer ({@code data})
     */
    public Map<String, Object> setDealerTerms(String dealerId, Map<String, ?> payload) {
        return dealerWrite("setDealerTerms", dealerId, payload);
    }

    /**
     * @param dealerId the dealer id
     * @return the answer ({@code data})
     */
    public Map<String, Object> signDealerContract(String dealerId) {
        return signDealerContract(dealerId, Map.of());
    }

    /**
     * Signature / contract acceptance.
     *
     * @param dealerId the dealer id
     * @param payload  the signature fields
     * @return the answer ({@code data})
     */
    public Map<String, Object> signDealerContract(String dealerId, Map<String, ?> payload) {
        return dealerWrite("signDealerContract", dealerId, payload);
    }

    /**
     * @param dealerId the dealer id
     * @return the dealer ({@code data})
     */
    public Map<String, Object> activateDealer(String dealerId) {
        return RouteRequest.data(routes.send("activateDealer", RouteRequest.params("dealerId", dealerId), null, null,
                null));
    }

    /**
     * A KYC outcome, when you run the dealer's KYC yourself.
     *
     * @param dealerId the dealer id
     * @param payload  the outcome
     * @return the answer ({@code data})
     */
    public Map<String, Object> recordDealerKycResult(String dealerId, Map<String, ?> payload) {
        return dealerWrite("recordDealerKycResult", dealerId, payload);
    }

    // ── Reporting and commissions ─────────────────────────────────────────────────────────────────────────────

    /** @return the first page of the payout ledger */
    public RoutePage payoutLedger() {
        return payoutLedger(Map.of());
    }

    /**
     * @param filters {@code status}, {@code subDealerId}, {@code days}, {@code limit}
     * @return one page of the payout ledger
     */
    public RoutePage payoutLedger(Map<String, ?> filters) {
        return new RoutePage(routes.send("payoutLedger", Map.of(), filters, null, null));
    }

    /** @return the first page of split transactions */
    public RoutePage transactions() {
        return transactions(Map.of());
    }

    /**
     * @param filters {@code status}, {@code subDealerId}, {@code limit}
     * @return one page of split transactions
     */
    public RoutePage transactions(Map<String, ?> filters) {
        return new RoutePage(routes.send("transactions", Map.of(), filters, null, null));
    }

    /** @return the first page of commission rules */
    public RoutePage commissionRules() {
        return commissionRules(Map.of());
    }

    /**
     * @param filters {@code limit}
     * @return one page of commission rules
     */
    public RoutePage commissionRules(Map<String, ?> filters) {
        return new RoutePage(routes.send("commissionRules", Map.of(), filters, null, null));
    }

    /**
     * @param rule the commission rule
     * @return the rule ({@code data})
     */
    public Map<String, Object> upsertCommissionRule(Map<String, ?> rule) {
        return RouteRequest.data(routes.send("upsertCommissionRule", Map.of(), null,
                MarketplaceSplit.body(rule, "upsertCommissionRule rule"), null));
    }

    /**
     * A dry run of a rule's effect.
     *
     * @param rule the commission rule
     * @return the preview ({@code data})
     */
    public Map<String, Object> previewCommissionRule(Map<String, ?> rule) {
        return RouteRequest.data(routes.send("previewCommissionRule", Map.of(), null,
                MarketplaceSplit.body(rule, "previewCommissionRule rule"), null));
    }

    /**
     * @param subDealerId the dealer id
     * @return the dealer's effective commission ({@code data})
     */
    public Map<String, Object> resolveCommission(String subDealerId) {
        return resolveCommission(subDealerId, null, null);
    }

    /**
     * The effective commission of a dealer (and category) at a month-to-date volume.
     *
     * @param subDealerId    the dealer id
     * @param categoryCode   the category, or {@code null}
     * @param mtdVolumeMinor the month-to-date volume as a minor-unit string, or {@code null}
     * @return the commission ({@code data})
     */
    public Map<String, Object> resolveCommission(String subDealerId, String categoryCode, String mtdVolumeMinor) {
        Map<String, Object> query = RouteRequest.compact(
                "subDealerId", Checks.text(subDealerId, "subDealerId"),
                "categoryCode", RouteRequest.optionalText(categoryCode, "categoryCode"),
                "mtdVolumeMinor", mtdVolumeMinor);
        return RouteRequest.data(routes.send("resolveCommission", Map.of(), query, null, null));
    }

    // ── Payouts ───────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * A manual payout: {@code {subDealerId, amountMinor, currency, reference?, …}} ({@code amountMinor} a positive
     * minor-unit string).
     *
     * @param payload the payout
     * @return the payout ({@code data})
     */
    public Map<String, Object> createPayout(Map<String, ?> payload) {
        Map<String, Object> body = MarketplaceSplit.body(payload, "createPayout payload");
        if (body.get("amountMinor") != null) {
            Checks.minor(body.get("amountMinor"), "amountMinor", true);
        }
        return RouteRequest.data(routes.send("createPayout", Map.of(), null, body, null));
    }

    /**
     * @param payoutId the payout id
     * @return the payout ({@code data})
     */
    public Map<String, Object> markPayoutPaid(String payoutId) {
        return markPayoutPaid(payoutId, Map.of());
    }

    /**
     * @param payoutId the payout id
     * @param payload  {@code {paidAt?, reference?, …}}
     * @return the payout ({@code data})
     */
    public Map<String, Object> markPayoutPaid(String payoutId, Map<String, ?> payload) {
        return RouteRequest.data(routes.send("markPayoutPaid", RouteRequest.params("payoutId", payoutId), null,
                MarketplaceSplit.body(payload, "markPayoutPaid payload"), null));
    }

    /**
     * @param payoutIds the payout ids
     * @return the answer ({@code data})
     */
    public Map<String, Object> bulkMarkPayoutsPaid(List<String> payoutIds) {
        return bulkMarkPayoutsPaid(payoutIds, Map.of());
    }

    /**
     * @param payoutIds the payout ids
     * @param extra     other fields ({@code reference}, {@code paidAt}, …)
     * @return the answer ({@code data})
     */
    public Map<String, Object> bulkMarkPayoutsPaid(List<String> payoutIds, Map<String, ?> extra) {
        if (payoutIds == null || payoutIds.isEmpty()) {
            throw new ConfigurationException("bulkMarkPayoutsPaid needs a non-empty list of payout ids.");
        }
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < payoutIds.size(); i++) {
            ids.add(Checks.text(payoutIds.get(i), "payoutIds[" + i + "]"));
        }
        Map<String, Object> body = MarketplaceSplit.body(extra, "bulkMarkPayoutsPaid extra");
        body.put("payoutIds", ids);
        return RouteRequest.data(routes.send("bulkMarkPayoutsPaid", Map.of(), null, body, null));
    }

    // ── Audit log (append-only, hash-chained) ─────────────────────────────────────────────────────────────────

    /** @return the first page of the audit log */
    public RoutePage auditLog() {
        return auditLog(Map.of());
    }

    /**
     * @param filters {@code limit}, {@code type}
     * @return one page of the audit log
     */
    public RoutePage auditLog(Map<String, ?> filters) {
        return new RoutePage(routes.send("auditLog", Map.of(), filters, null, null));
    }

    /** @return the integrity check of the hash chain ({@code data}) */
    public Map<String, Object> verifyAuditLog() {
        return RouteRequest.data(routes.send("verifyAuditLog"));
    }

    private Map<String, Object> dealerWrite(String name, String dealerId, Map<String, ?> payload) {
        return RouteRequest.data(routes.send(name, RouteRequest.params("dealerId", dealerId), null,
                MarketplaceSplit.body(payload, name + " payload"), null));
    }

    private static Map<String, Object> dealerBody(Map<String, ?> request, String label) {
        Map<String, Object> body = MarketplaceSplit.body(request, label);
        if (body.get("dealerType") != null && !MarketplaceValues.DealerType.ALL.contains(body.get("dealerType"))) {
            throw new ConfigurationException("dealerType must be one of "
                    + String.join(", ", MarketplaceValues.DealerType.ALL) + ".");
        }
        return body;
    }

    private static String text(Object value) {
        return value instanceof String text && !text.isEmpty() ? text : null;
    }
}
