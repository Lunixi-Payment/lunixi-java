package com.lunixi.sdk.insurance;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.lunixi.sdk.exception.ConfigurationException;
import com.lunixi.sdk.http.Envelope;
import com.lunixi.sdk.internal.ApiClient;
import com.lunixi.sdk.internal.Checks;
import com.lunixi.sdk.internal.CursorPages;
import com.lunixi.sdk.internal.RequestOptions;

/**
 * Insurance ({@code /api/v1/insurance}, product {@code INSURANCE_SERVICE}), {@code client.insurance()}: offer, sell
 * and service insurance for your basket items, and submit leads for the types sold by request only.
 *
 * <p>Every call is signed, reads included; an unsigned request is 401 {@code AUTH_HEADERS_MISSING} and a key without
 * the product is 403 {@code PRODUCT_NOT_ENABLED}. The key decides the environment (TEST or LIVE).
 *
 * <p>{@link #createOrder}, {@link #confirmPayment}, {@link #issuePolicy}, {@link #cancelOrder},
 * {@link #refundPremium} and {@link #createLead} require an {@code Idempotency-Key}
 * ({@link InsuranceValues#IDEMPOTENCY_KEY_PATTERN}); {@link #createQuote} and {@link #bindPayment} take one
 * optionally. Request fields and query filters are checked before anything is sent and throw
 * {@link ConfigurationException}; the field rules of an insurance type's own object ({@code insured},
 * {@code tickets} …) are not repeated here — a violation comes back as 422 {@code insurance.validation.invalid}
 * ({@link com.lunixi.sdk.exception.ValidationException}) with every {@code details.errors[{field, reason}]} at once.
 * Amounts are canonical minor-unit strings.
 *
 * <p>Single answers are the envelope's {@code data}; list calls return an {@link InsurancePage}. The policy files and
 * the reconciliation export are binary answers (the JSON envelope only on an error).
 */
public final class InsuranceClient {

    private static final String BASE = "/api/v1/insurance";
    private static final int CURSOR_MAX_LENGTH = 512;
    private static final int TEXT_MAX_LENGTH = 128;
    private static final String PDF_ACCEPT = "application/pdf, application/json";
    private static final String CSV_ACCEPT = "text/csv, application/json";

    private final ApiClient api;

    /**
     * @param api the gateway client
     */
    public InsuranceClient(ApiClient api) {
        this.api = api;
    }

    // ── Products and reference codes ──────────────────────────────────────────────────────────────────────────

    /** Same as {@link #listProducts(Map)} with only {@code currency}: the types sold without a basket item. */
    public Map<String, Object> listProducts(String currency) {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("currency", currency);
        return listProducts(filters);
    }

    /**
     * Insurance types offered for a payment's basket, read from the items sent when the payment was created
     * ({@code GET /products}). Without {@code paymentIntentId} it lists the types sold without a basket item. An
     * empty list means insurance is not offered; it is not an error. Each product says what the items already fill
     * in ({@code prefill}, {@code providedFields} masked), whether a quote needs nothing more ({@code quoteReady})
     * and which {@code insuranceData} could not be used ({@code insuranceDataIssues}).
     *
     * @param filters {@code currency} (required), {@code paymentIntentId}, {@code insuranceType}
     * @return {@code {products}}
     */
    public Map<String, Object> listProducts(Map<String, ?> filters) {
        Map<String, String> query = query(filters, "listProducts");
        requireFilters(query, List.of("currency"), "listProducts");
        return read("/products", query);
    }

    /**
     * Insurance types offered for basket items before the payment exists ({@code POST /products}, read-only). Send
     * the items you will send in the payment.
     *
     * @param request {@code currency}, {@code context: {items}} (1–100 basket items), optional {@code insuranceType}
     * @return {@code {products}}
     */
    public Map<String, Object> searchProducts(Map<String, ?> request) {
        Map<String, Object> body = Checks.plainMap(request, "searchProducts request");
        Checks.assertKeys(body.keySet(), InsuranceValues.PRODUCT_SEARCH_FIELDS, "searchProducts request");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("currency", Checks.currency(body.get("currency"), "currency"));
        payload.put("context", context(body.get("context")));
        if (Checks.present(body.get("insuranceType"))) {
            payload.put("insuranceType", Checks.text(body.get("insuranceType"), "insuranceType"));
        }
        return Envelope.data(api.request("POST", BASE + "/products", payload, signed().build()));
    }

    /** @return the first page of one kind of reference codes */
    public InsurancePage listReferenceValues(String kind) {
        return listReferenceValues(kind, Map.of());
    }

    /**
     * Lunixi reference codes of one kind ({@code GET /reference/{kind}}), for the type fields that take a code.
     *
     * @param kind    an {@link InsuranceValues.ReferenceKind} value
     * @param filters {@code parentCode}, {@code limit}, {@code cursor}
     * @return the page
     */
    public InsurancePage listReferenceValues(String kind, Map<String, ?> filters) {
        String path = "/reference/" + encode(referenceKind(kind));
        return new InsurancePage(api.request("GET", BASE + path, null,
                options(query(filters, "listReferenceValues"))));
    }

    /** Every reference code of one kind, following the cursors. */
    public Iterable<Map<String, Object>> iterateReferenceValues(String kind, Map<String, ?> filters) {
        referenceKind(kind);
        return CursorPages.iterate(filters, page -> listReferenceValues(kind, page), InsurancePage::items,
                InsurancePage::continuation);
    }

    // ── Quotes ────────────────────────────────────────────────────────────────────────────────────────────────

    /** Same as {@link #createQuote(Map, String)} without an {@code Idempotency-Key}. */
    public Map<String, Object> createQuote(Map<String, ?> request) {
        return createQuote(request, null);
    }

    /**
     * Prices an insurance type ({@code POST /quotes}, HTTP 201). The basket is either a payment's recorded basket
     * ({@code paymentIntentId}) or the items you will send in the payment ({@code context.items}), never both
     * (refused before sending). Send neither for a type sold without a basket item: the insured value is then in the
     * type object. Every option has its own {@code quoteId}, {@code terms} and {@code expiresAt}; a company the
     * request could not be priced with is in {@code excludedOptions}.
     *
     * @param request        the fields of {@link InsuranceValues#QUOTE_FIELDS}: {@code insuranceType},
     *                       {@code currency}, a basket source, the person fields and the type object
     * @param idempotencyKey optional; it only correlates traces (every call is a new, time-limited price)
     * @return {@code {insuranceType, schemaVersion, options, excludedOptions}}
     */
    public Map<String, Object> createQuote(Map<String, ?> request, String idempotencyKey) {
        Map<String, Object> body = Checks.plainMap(request, "createQuote request");
        Checks.assertKeys(body.keySet(), InsuranceValues.QUOTE_FIELDS, "createQuote request");
        boolean hasPayment = Checks.present(body.get("paymentIntentId"));
        boolean hasItems = Checks.present(body.get("context"));
        if (hasPayment && hasItems) {
            throw new ConfigurationException("Send the basket either as 'paymentIntentId' (a payment that exists) or "
                    + "as 'context.items' (before the payment), not both. Send neither for an insurance type sold "
                    + "without a basket item.");
        }
        body.put("insuranceType", Checks.text(body.get("insuranceType"), "insuranceType"));
        body.put("currency", Checks.currency(body.get("currency"), "currency"));
        replace(body, "paymentIntentId", hasPayment ? Checks.uuid(body.get("paymentIntentId"), "paymentIntentId")
                : null);
        replace(body, "context", hasItems ? context(body.get("context")) : null);
        replace(body, "merchantOrderId", Checks.present(body.get("merchantOrderId"))
                ? Checks.text(body.get("merchantOrderId"), "merchantOrderId", TEXT_MAX_LENGTH) : null);
        return Envelope.data(api.request("POST", BASE + "/quotes", body,
                signed().idempotencyKey(optionalIdempotencyKey(idempotencyKey)).build()));
    }

    /** One quote option; 404 {@code insurance.quote.not_found} for an unknown or another merchant's quote. */
    public Map<String, Object> getQuote(String quoteId) {
        return read("/quotes/" + encode(Checks.text(quoteId, "quoteId")), Map.of());
    }

    // ── Orders ────────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Turns a quote option into an order ({@code POST /orders}, HTTP 201) and records the buyer's consent. No provider
     * call and no money movement. The same key with the same body returns the recorded order; with another body it
     * is 409 {@code insurance.idempotency.conflict}.
     *
     * @param request        {@code quoteId}, optional {@code merchantOrderId}, {@code consent} (the option's
     *                       {@code terms} unchanged plus the acceptance time; {@link #consentFromQuote} builds it)
     * @param idempotencyKey required
     * @return the order ({@code data})
     */
    public Map<String, Object> createOrder(Map<String, ?> request, String idempotencyKey) {
        Map<String, Object> body = Checks.plainMap(request, "createOrder request");
        Checks.assertKeys(body.keySet(), InsuranceValues.ORDER_FIELDS, "createOrder request");
        Map<String, Object> consent = Checks.plainMap(body.get("consent"), "consent");
        Checks.assertKeys(consent.keySet(), InsuranceValues.CONSENT_FIELDS, "consent");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("quoteId", Checks.text(body.get("quoteId"), "quoteId"));
        if (Checks.present(body.get("merchantOrderId"))) {
            payload.put("merchantOrderId", Checks.text(body.get("merchantOrderId"), "merchantOrderId",
                    TEXT_MAX_LENGTH));
        }
        Map<String, Object> consentBody = new LinkedHashMap<>();
        consentBody.put("termsVersion", Checks.text(consent.get("termsVersion"), "consent.termsVersion"));
        consentBody.put("termsSha256", Checks.text(consent.get("termsSha256"), "consent.termsSha256"));
        consentBody.put("acceptedAt", Checks.isoTime(consent.get("acceptedAt"), "consent.acceptedAt"));
        payload.put("consent", consentBody);
        return keyed("POST", "/orders", payload, requireIdempotencyKey("insurance order create", idempotencyKey));
    }

    /** @return the first page of your insurance orders */
    public InsurancePage listOrders() {
        return listOrders(Map.of());
    }

    /**
     * Your insurance orders, newest first ({@code GET /orders}). Filters: {@code limit}, {@code cursor},
     * {@code paymentIntentId}, {@code merchantOrderId}.
     */
    public InsurancePage listOrders(Map<String, ?> filters) {
        return new InsurancePage(api.request("GET", BASE + "/orders", null, options(query(filters, "listOrders"))));
    }

    /** Every order matching the filters, following the cursors. */
    public Iterable<Map<String, Object>> iterateOrders(Map<String, ?> filters) {
        return CursorPages.iterate(filters, this::listOrders, InsurancePage::items, InsurancePage::continuation);
    }

    /** One order; 404 {@code insurance.order.not_found} for an unknown or another merchant's order. */
    public Map<String, Object> getOrder(String orderId) {
        return read(orderPath(orderId), Map.of());
    }

    /** Same as {@link #bindPayment(String, String, String)} without an {@code Idempotency-Key}. */
    public Map<String, Object> bindPayment(String orderId, String paymentIntentId) {
        return bindPayment(orderId, paymentIntentId, null);
    }

    /**
     * Binds an order to the Lunixi payment that collects its premium ({@code POST /orders/{orderId}/bind-payment}).
     * When you collect it ({@code premiumCollectedBy: MERCHANT}) the payment basket must hold the premium item
     * ({@link #premiumBasketItem}); when the provider collects it, it must not. Binding the same payment again
     * returns the order.
     *
     * @param orderId         the order id
     * @param paymentIntentId the payment (UUID)
     * @param idempotencyKey  optional
     * @return the order ({@code data})
     */
    public Map<String, Object> bindPayment(String orderId, String paymentIntentId, String idempotencyKey) {
        String path = orderPath(orderId) + "/bind-payment";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("paymentIntentId", Checks.uuid(paymentIntentId, "paymentIntentId"));
        return keyed("POST", path, body, optionalIdempotencyKey(idempotencyKey));
    }

    /**
     * Records a premium you collected outside Lunixi ({@code POST /orders/{orderId}/confirm-payment}).
     * {@code paidAmount} and {@code paidCurrency} must equal the order's premium; repeating the same
     * {@code externalPaymentId} returns the order.
     *
     * @param orderId        the order id
     * @param payment        {@code externalPaymentId}, {@code paidAmount} (minor units), {@code paidCurrency},
     *                       {@code paidAt}, optional {@code paymentProvider}
     * @param idempotencyKey required
     * @return the order ({@code data})
     */
    public Map<String, Object> confirmPayment(String orderId, Map<String, ?> payment, String idempotencyKey) {
        String path = orderPath(orderId) + "/confirm-payment";
        Map<String, Object> source = Checks.plainMap(payment, "payment");
        Checks.assertKeys(source.keySet(), InsuranceValues.EXTERNAL_PAYMENT_FIELDS, "payment");
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("externalPaymentId", Checks.text(source.get("externalPaymentId"), "payment.externalPaymentId",
                TEXT_MAX_LENGTH));
        fields.put("paidAmount", Checks.minor(source.get("paidAmount"), "payment.paidAmount", true));
        fields.put("paidCurrency", Checks.currency(source.get("paidCurrency"), "payment.paidCurrency"));
        fields.put("paidAt", Checks.isoTime(source.get("paidAt"), "payment.paidAt"));
        if (Checks.present(source.get("paymentProvider"))) {
            fields.put("paymentProvider", Checks.text(source.get("paymentProvider"), "payment.paymentProvider", 64));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("payment", fields);
        return keyed("POST", path, body, requireIdempotencyKey("insurance payment confirmation", idempotencyKey));
    }

    /**
     * Requests the policy of an order whose premium you collected outside Lunixi
     * ({@code POST /orders/{orderId}/issue-policy}; {@code policyStatus: PENDING}, the result arrives by webhook).
     * With a Lunixi payment the policy is requested on its own once the premium is collected. 412
     * {@code insurance.premium.not_captured} before the premium is collected.
     *
     * @param orderId        the order id
     * @param idempotencyKey required
     * @return the order ({@code data})
     */
    public Map<String, Object> issuePolicy(String orderId, String idempotencyKey) {
        String path = orderPath(orderId) + "/issue-policy";
        return keyed("POST", path, null, requireIdempotencyKey("insurance policy issue", idempotencyKey));
    }

    /**
     * Opens a cancellation at the provider for every issued policy of the order
     * ({@code POST /orders/{orderId}/cancel}; {@code policyStatus: CANCEL_PENDING}, confirmed by
     * {@link InsuranceEvents#POLICY_CANCELLED}). Cancelling does not refund the premium: call {@link #refundPremium}
     * after it.
     *
     * @param orderId        the order id
     * @param idempotencyKey required
     * @return the order ({@code data})
     */
    public Map<String, Object> cancelOrder(String orderId, String idempotencyKey) {
        String path = orderPath(orderId) + "/cancel";
        return keyed("POST", path, null, requireIdempotencyKey("insurance order cancel", idempotencyKey));
    }

    /** Refunds the premium of an order paid with a Lunixi payment (empty body). */
    public Map<String, Object> refundPremium(String orderId, String idempotencyKey) {
        return refundPremium(orderId, Map.of(), idempotencyKey);
    }

    /**
     * Refunds the premium ({@code POST /orders/{orderId}/refund-premium}). With a Lunixi payment, leave
     * {@code externalRefund} out: the premium is refunded from that payment ({@code refundStatus: PENDING}, then
     * {@link InsuranceEvents#REFUND_COMPLETED}). For a premium collected outside Lunixi, send the refund you made. An
     * in-force policy must be cancelled first (409, {@code policy_active}).
     *
     * @param orderId        the order id
     * @param options        {@code externalRefund: {externalRefundId, refundedAt}}, or empty
     * @param idempotencyKey required
     * @return the order ({@code data})
     */
    public Map<String, Object> refundPremium(String orderId, Map<String, ?> options, String idempotencyKey) {
        String path = orderPath(orderId) + "/refund-premium";
        Map<String, Object> source = options == null ? Map.of() : Checks.plainMap(options, "refundPremium options");
        Checks.assertKeys(source.keySet(), List.of("externalRefund"), "refundPremium options");
        Map<String, Object> body = new LinkedHashMap<>();
        if (Checks.present(source.get("externalRefund"))) {
            Map<String, Object> refund = Checks.plainMap(source.get("externalRefund"), "externalRefund");
            Checks.assertKeys(refund.keySet(), InsuranceValues.EXTERNAL_REFUND_FIELDS, "externalRefund");
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("externalRefundId", Checks.text(refund.get("externalRefundId"),
                    "externalRefund.externalRefundId", TEXT_MAX_LENGTH));
            fields.put("refundedAt", Checks.isoTime(refund.get("refundedAt"), "externalRefund.refundedAt"));
            body.put("externalRefund", fields);
        }
        return keyed("POST", path, body, requireIdempotencyKey("insurance premium refund", idempotencyKey));
    }

    // ── Leads ─────────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Submits a lead ({@code POST /leads}, HTTP 201) for an insurance type sold by request only: there is no premium,
     * order, policy or document. The lead is sent to the provider with your own connection in this call. The
     * answer's {@code status} is {@code SUBMITTED} (followed by {@link InsuranceEvents#LEAD_SUBMITTED}) or
     * {@code UNKNOWN} (the lead is not sent again). A refusal is 422 {@code insurance.lead.rejected}.
     *
     * @param request        the fields of {@link InsuranceValues#LEAD_FIELDS}; {@code note} is sent as given (at most
     *                       1000 characters)
     * @param idempotencyKey required; the same key with the same body returns the same lead
     * @return the lead ({@code data})
     */
    public Map<String, Object> createLead(Map<String, ?> request, String idempotencyKey) {
        String key = requireIdempotencyKey("insurance lead submit", idempotencyKey);
        Map<String, Object> body = Checks.plainMap(request, "createLead request");
        Checks.assertKeys(body.keySet(), InsuranceValues.LEAD_FIELDS, "createLead request");
        body.put("insuranceType", Checks.text(body.get("insuranceType"), "insuranceType", 64));
        replace(body, "note", Checks.present(body.get("note")) ? leadNote(body.get("note")) : null);
        return keyed("POST", "/leads", body, key);
    }

    /** @return the first page of your leads */
    public InsurancePage listLeads() {
        return listLeads(Map.of());
    }

    /**
     * Your leads, newest first ({@code GET /leads}); personal data is masked in {@code requestMasked}. Filters:
     * {@code limit}, {@code cursor}, {@code insuranceType}, {@code status} ({@link InsuranceValues.LeadStatus}).
     */
    public InsurancePage listLeads(Map<String, ?> filters) {
        return new InsurancePage(api.request("GET", BASE + "/leads", null, options(query(filters, "listLeads"))));
    }

    /** Every lead matching the filters, following the cursors. */
    public Iterable<Map<String, Object>> iterateLeads(Map<String, ?> filters) {
        return CursorPages.iterate(filters, this::listLeads, InsurancePage::items, InsurancePage::continuation);
    }

    /** One lead; 404 {@code insurance.lead.not_found} for an unknown or another merchant's lead. */
    public Map<String, Object> getLead(String leadId) {
        return read("/leads/" + encode(Checks.text(leadId, "leadId")), Map.of());
    }

    // ── Policy files ──────────────────────────────────────────────────────────────────────────────────────────

    /**
     * The policy PDF ({@code GET /policies/{policyId}/document}). It is read from the provider on every call and is
     * available while the policy is in force ({@code ISSUED}, {@code CANCEL_PENDING}); 503
     * {@code insurance.document.unavailable} is temporary — retry later. Needs a transport that reads binary answers.
     */
    public InsuranceDocument getPolicyDocument(String policyId) {
        return policyFile(policyId, "document");
    }

    /**
     * The provider's receipt for the policy premium ({@code GET /policies/{policyId}/receipt}). Only for products
     * that issue one; otherwise 412 {@code insurance.document.not_supported}.
     */
    public InsuranceDocument getPolicyReceipt(String policyId) {
        return policyFile(policyId, "receipt");
    }

    /**
     * The provider's certificate of the policy ({@code GET /policies/{policyId}/certificate}), for example a motor
     * Green Card. Only for products that issue one; otherwise 412 {@code insurance.document.not_supported}.
     */
    public InsuranceDocument getPolicyCertificate(String policyId) {
        return policyFile(policyId, "certificate");
    }

    // ── Reconciliation ────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Reconciliation totals grouped by provider connection, currency and {@code premiumCollectedBy}
     * ({@code GET /reconciliation/summary}).
     *
     * @param filters {@code from} (inclusive) and {@code to} (exclusive) are required, on the order creation time (an
     *                {@code Instant}, an {@code OffsetDateTime} or an ISO-8601 string with a time zone, at most 366
     *                days apart); optional {@code connectionId}, {@code insuranceType}, {@code currency}
     * @return the summary ({@code data})
     */
    public Map<String, Object> getReconciliationSummary(Map<String, ?> filters) {
        Map<String, String> query = query(filters, "getReconciliationSummary");
        requireFilters(query, List.of("from", "to"), "getReconciliationSummary");
        return read("/reconciliation/summary", query);
    }

    /** The orders and policies behind the summary for the same filter, newest first (plus {@code limit}, {@code cursor}). */
    public InsurancePage listReconciliationOrders(Map<String, ?> filters) {
        Map<String, String> query = query(filters, "listReconciliationOrders");
        requireFilters(query, List.of("from", "to"), "listReconciliationOrders");
        return new InsurancePage(api.request("GET", BASE + "/reconciliation/orders", null, options(query)));
    }

    /** Every reconciliation order matching the filters, following the cursors. */
    public Iterable<Map<String, Object>> iterateReconciliationOrders(Map<String, ?> filters) {
        return CursorPages.iterate(filters, this::listReconciliationOrders, InsurancePage::items,
                InsurancePage::continuation);
    }

    /**
     * The reconciliation report as CSV ({@code GET /reconciliation/export}): the summary's filters plus
     * {@code cursor}. A file holds at most 20000 orders; see {@link InsuranceReconciliationExport#truncated()} and
     * {@link InsuranceReconciliationExport#aborted()}. Needs a transport that reads binary answers.
     *
     * @param filters the filters ({@code from} and {@code to} required)
     * @return the CSV file
     */
    public InsuranceReconciliationExport exportReconciliation(Map<String, ?> filters) {
        Map<String, String> query = query(filters, "exportReconciliation");
        requireFilters(query, List.of("from", "to"), "exportReconciliation");
        ApiClient.Binary file = api.requestBinary("GET", BASE + "/reconciliation/export", CSV_ACCEPT, options(query));
        return new InsuranceReconciliationExport(file.contentType(), file.bytes());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * The {@code consent} of {@link #createOrder}: the quote option's {@code terms} unchanged and the moment the
     * buyer accepted them.
     *
     * @param option     a quote option returned by {@link #createQuote} ({@code options[]}) or {@link #getQuote}
     * @param acceptedAt an {@code Instant}, an {@code OffsetDateTime} or an ISO-8601 string with a time zone, not in
     *                   the future
     * @return {@code {termsVersion, termsSha256, acceptedAt}}
     */
    public static Map<String, Object> consentFromQuote(Map<String, ?> option, Object acceptedAt) {
        Object terms = Checks.plainMap(option, "quote option").get("terms");
        if (!(terms instanceof Map<?, ?> map)) {
            throw new ConfigurationException("The quote option has no 'terms': pass an option returned by "
                    + "createQuote() or getQuote().");
        }
        Map<String, Object> consent = new LinkedHashMap<>();
        consent.put("termsVersion", Checks.text(map.get("version"), "terms.version"));
        consent.put("termsSha256", Checks.text(map.get("sha256"), "terms.sha256"));
        consent.put("acceptedAt", Checks.isoTime(acceptedAt, "acceptedAt"));
        return consent;
    }

    /** Same as {@link #premiumBasketItem(Map, String, String, String)} without {@code category2}. */
    public static Map<String, Object> premiumBasketItem(Map<String, ?> order, String name, String category1) {
        return premiumBasketItem(order, name, category1, null);
    }

    /**
     * The premium basket item for a payment that collects the premium of an order ({@code premiumCollectedBy:
     * MERCHANT}): {@code id} = {@code orderId}, {@code itemType: VIRTUAL}, {@code price} = {@code premiumAmount}. Add it
     * to the payment's basket next to the insured items and add the premium to the payment {@code amount}; for
     * insurance sold without a basket item it can be the only item. It carries no insurance field. A {@code PROVIDER}
     * order must not have one.
     *
     * @param order     the order ({@link #createOrder} / {@link #getOrder})
     * @param name      the item name
     * @param category1 the item category
     * @param category2 the sub-category, or {@code null}
     * @return the basket item map
     */
    public static Map<String, Object> premiumBasketItem(Map<String, ?> order, String name, String category1,
                                                        String category2) {
        Map<String, Object> source = Checks.plainMap(order, "order");
        if (!InsuranceValues.PremiumCollectedBy.MERCHANT.equals(source.get("premiumCollectedBy"))) {
            throw new ConfigurationException("Only an order whose premium you collect (premiumCollectedBy MERCHANT) "
                    + "has a premium basket item.");
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", Checks.text(source.get("orderId"), "order.orderId"));
        item.put("price", Checks.minor(source.get("premiumAmount"), "order.premiumAmount", true));
        item.put("name", Checks.text(name, "name"));
        item.put("category1", Checks.text(category1, "category1"));
        if (Checks.present(category2)) {
            item.put("category2", Checks.text(category2, "category2"));
        }
        item.put("itemType", "VIRTUAL");
        return item;
    }

    // ── Internals ─────────────────────────────────────────────────────────────────────────────────────────────

    private InsuranceDocument policyFile(String policyId, String file) {
        String path = BASE + "/policies/" + encode(Checks.text(policyId, "policyId")) + "/" + file;
        ApiClient.Binary pdf = api.requestBinary("GET", path, PDF_ACCEPT, signed().build());
        return new InsuranceDocument(pdf.contentType(), pdf.bytes());
    }

    private Map<String, Object> read(String path, Map<String, String> query) {
        return Envelope.data(api.request("GET", BASE + path, null, options(query)));
    }

    private Map<String, Object> keyed(String method, String path, Object body, String idempotencyKey) {
        return Envelope.data(api.request(method, BASE + path, body, signed().idempotencyKey(idempotencyKey).build()));
    }

    private static RequestOptions.Builder signed() {
        return RequestOptions.builder().stepUp(true);
    }

    private static RequestOptions options(Map<String, String> query) {
        RequestOptions.Builder options = signed();
        query.forEach(options::query);
        return options.build();
    }

    private static Map<String, String> query(Map<String, ?> filters, String route) {
        Map<String, ?> given = filters == null ? Map.of() : filters;
        List<String> allowed = InsuranceValues.FILTERS.get(route);
        List<String> unknown = given.keySet().stream().filter(key -> !allowed.contains(key)).toList();
        if (!unknown.isEmpty()) {
            throw new ConfigurationException("Unknown insurance filter(s): " + String.join(", ", unknown)
                    + ". Allowed: " + String.join(", ", allowed) + ". The gateway rejects unknown query fields with "
                    + "400.");
        }
        Map<String, String> query = new LinkedHashMap<>();
        for (String key : allowed) {
            Object value = given.get(key);
            if (Checks.present(value)) {
                query.put(key, filterValue(key, value));
            }
        }
        return query;
    }

    private static String filterValue(String key, Object value) {
        return switch (key) {
            case "limit" -> Long.toString(Checks.limit(value, InsuranceValues.MAX_LIMIT));
            case "cursor" -> Checks.cursor(value, CURSOR_MAX_LENGTH);
            case "currency" -> Checks.currency(value, key);
            case "paymentIntentId", "connectionId" -> Checks.uuid(value, key);
            case "from", "to" -> Checks.isoTime(value, key);
            case "insuranceType", "parentCode" -> Checks.text(value, key, 64);
            case "merchantOrderId" -> Checks.text(value, key, TEXT_MAX_LENGTH);
            case "status" -> Checks.oneOf(value, InsuranceValues.LeadStatus.ALL, key);
            default -> throw new ConfigurationException("Unknown insurance filter '" + key + "'.");
        };
    }

    private static void requireFilters(Map<String, String> query, List<String> required, String route) {
        List<String> missing = required.stream().filter(key -> !query.containsKey(key)).map(key -> "'" + key + "'")
                .toList();
        if (!missing.isEmpty()) {
            throw new ConfigurationException(route + " requires " + String.join(" and ", missing) + ".");
        }
    }

    private static String referenceKind(String kind) {
        if (kind == null || !InsuranceValues.ReferenceKind.ALL.contains(kind)) {
            throw new ConfigurationException("Reference kind must be one of "
                    + String.join(", ", InsuranceValues.ReferenceKind.ALL) + ".");
        }
        return kind;
    }

    /** {@code context: {items}} — the same basket items the payment carries (1–100). */
    private static Map<String, Object> context(Object value) {
        if (!Checks.present(value)) {
            throw new ConfigurationException("'context.items' is required.");
        }
        Map<String, Object> context = Checks.plainMap(value, "context");
        Checks.assertKeys(context.keySet(), List.of("items"), "context");
        if (!(context.get("items") instanceof Collection<?> items) || items.isEmpty()
                || items.size() > InsuranceValues.MAX_CONTEXT_ITEMS) {
            throw new ConfigurationException("'context.items' must hold 1 to " + InsuranceValues.MAX_CONTEXT_ITEMS
                    + " basket items.");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", new java.util.ArrayList<>(items));
        return out;
    }

    /** The lead {@code note} is free text sent as given (not trimmed), at most 1000 characters. */
    private static String leadNote(Object value) {
        if (!(value instanceof String note)) {
            throw new ConfigurationException("'note' must be a string.");
        }
        if (note.codePointCount(0, note.length()) > InsuranceValues.LEAD_NOTE_MAX_LENGTH) {
            throw new ConfigurationException("'note' must be at most " + InsuranceValues.LEAD_NOTE_MAX_LENGTH
                    + " characters.");
        }
        return note;
    }

    private static String optionalIdempotencyKey(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        return assertIdempotencyKeyFormat(key.trim());
    }

    private static String requireIdempotencyKey(String operation, String key) {
        String value = key == null ? "" : key.trim();
        if (value.isEmpty()) {
            throw new ConfigurationException("A stable Idempotency-Key is required for " + operation
                    + ". Reuse the same key when retrying the same operation.");
        }
        return assertIdempotencyKeyFormat(value);
    }

    private static String assertIdempotencyKeyFormat(String value) {
        if (!InsuranceValues.IDEMPOTENCY_KEY_PATTERN.matcher(value).matches()) {
            throw new ConfigurationException("Idempotency-Key must be 8-200 printable ASCII characters without "
                    + "spaces.");
        }
        return value;
    }

    /** Replaces a value in place, or removes the key when the new value is {@code null}. */
    private static void replace(Map<String, Object> body, String key, Object value) {
        if (value == null) {
            body.remove(key);
        } else {
            body.put(key, value);
        }
    }

    private static String orderPath(String orderId) {
        return "/orders/" + encode(Checks.text(orderId, "orderId"));
    }

    private static String encode(String segment) {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
