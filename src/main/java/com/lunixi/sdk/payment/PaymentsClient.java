package com.lunixi.sdk.payment;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import com.lunixi.sdk.exception.ConfigurationException;
import com.lunixi.sdk.exception.ValidationException;
import com.lunixi.sdk.http.Envelope;
import com.lunixi.sdk.internal.ApiClient;
import com.lunixi.sdk.internal.RequestOptions;

/**
 * Server payment endpoints. Persist the exact request and operation key BEFORE sending.
 *
 * <p>The payment-method surface ({@link #listPaymentMethods()}, {@link #directMethodPayment},
 * {@link #confirmPaymentAction}, promissory notes, split payments, bank transfer exceptions) checks every request
 * before sending and throws {@link ConfigurationException} for a rule violation; nothing is sent then. Those calls
 * carry the per-request signature and return the gateway envelope.
 *
 * <p>The card surface ({@link #binInfo}, {@link #installmentOptions(InstallmentOptionsRequest)},
 * {@link #cardTaxonomy}, stored cards), {@link #createMarketplaceIntent(Map, String)}, {@link #list(Map)} and the
 * analytics reads check their input the same way. Card numbers and security codes never appear in an error message.
 */
public final class PaymentsClient {
    private static final String BASE = "/api/v1/payments";
    private static final String BIN = "/api/v1/bin";
    /** Filters of {@link #list(Map)}. */
    public static final List<String> LIST_FILTERS = List.of("page", "limit", "status", "customerId", "includeLegs");
    /** Largest {@code limit} of {@link #listBankTransferExceptions(String, String, Integer)}. */
    public static final int BANK_TRANSFER_EXCEPTION_LIMIT_MAX = 200;
    /** {@code environment} values of {@link #listBankTransferExceptions(String, String, Integer)}. */
    public static final List<String> BANK_TRANSFER_EXCEPTION_ENVIRONMENTS = List.of("TEST", "LIVE");
    private static final String PDF_ACCEPT = "application/pdf, application/json";
    private static final Pattern UUID = Pattern.compile(
            "(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private final ApiClient api;
    public PaymentsClient(ApiClient api) { this.api = api; }
    /**
     * Creates a checkout intent ({@code POST /api/v1/payments/intents}) from the gateway body.
     *
     * <p>Optional {@code enabledInstallments} ({@code List<Integer>}) lists the installment counts the hosted form
     * may offer for this payment, for example {@code List.of(1, 2, 3)}; {@code List.of(1)} allows a single payment
     * only. The form shows only the counts also allowed by the panel settings, the card and the platform maximum.
     * Leave it out for no restriction. Send distinct integers 1–36 that include 1, in ascending order; the gateway
     * rejects any other list, an empty one included, with 400.
     *
     * <p>The order details are required ({@link OrderDetails}): {@code buyer} ({@code ip} may be left out here: the
     * Lunixi form takes it from the buyer's browser), {@code billingAddress} and {@code basketItems} (at least one,
     * the {@code price} values adding up to {@code amount}); {@code shippingAddress} when an item is
     * {@code PHYSICAL}. A body without them throws {@link ConfigurationException} naming the missing paths; nothing
     * is sent then.
     *
     * @param request gateway body: {@code amount} (canonical minor-unit string), {@code currency}, {@code orderId},
     *     the order details and optional fields such as {@code callbackUrl} and {@code enabledInstallments}
     * @param idempotencyKey the persisted key of this checkout attempt
     * @return the checkout session with the payment id, token and optional payment page URL
     */
    public CheckoutSession createIntent(Map<String, Object> request, String idempotencyKey) {
        if (request != null && (request.containsKey("price") || request.containsKey("paidPrice"))) { throw new IllegalArgumentException("Removed top-level amount field; use integer minor-unit amount."); }
        if (request == null || Money.integer(request.get("amount")) < 1 || !(request.get("orderId") instanceof String order) || order.isBlank() || !(request.get("currency") instanceof String currency)) { throw new IllegalArgumentException("Server order, positive amount and currency are required."); }
        Money.exponent(currency);
        OrderDetails.assertComplete(request, "A payment intent", true, true);
        if (request.containsKey("callbackUrl")) { httpsUrl(String.valueOf(request.get("callbackUrl"))); }
        Map<String, Object> result = Envelope.data(api.request("POST", BASE + "/intents", request, mutation(idempotencyKey)));
        String id = text(result.get("paymentId"));
        for (String alias : new String[]{"intentId", "id"}) { if (result.containsKey(alias) && !id.equals(result.get(alias))) { throw new IllegalStateException("Conflicting checkout response identities; reconcile the persisted attempt."); } }
        Object paymentPageUrl = result.get("paymentPageUrl");
        if (paymentPageUrl != null && !(paymentPageUrl instanceof String)) { throw new IllegalStateException("Uncertain checkout response: paymentPageUrl must be a string when present."); }
        return new CheckoutSession(id, text(result.get("token")), (String) paymentPageUrl);
    }
    /** Full envelope is retained because settlement balances are in details beside data. */
    public Map<String, Object> get(String paymentId) { return api.request("GET", BASE + "/" + segment(paymentId), null, RequestOptions.none()); }
    public Map<String, Object> getSplit(String splitId) { return api.request("GET", BASE + "/split/" + segment(splitId), null, RequestOptions.none()); }
    /** Capture an explicit positive amount. Null is rejected; use captureRemaining for full capture. */
    public Map<String, Object> capture(String id, String amount, String key) { positiveAmount(amount); return financial(id, "capture", amount, null, key); }
    /** Capture the remaining authorization without an amount field. */
    public Map<String, Object> captureRemaining(String id, String key) { return financial(id, "capture", null, null, key); }
    /** Refund an explicit positive amount. Null is rejected; use refundRemaining for the remaining balance. */
    public Map<String, Object> refund(String id, String amount, String reason, String key) { positiveAmount(amount); return financial(id, "refund", amount, reason, key); }
    /** Refund the remaining balance without an amount field. */
    public Map<String, Object> refundRemaining(String id, String reason, String key) { return financial(id, "refund", null, reason, key); }
    public Map<String, Object> voidPayment(String id, String key) { return api.request("POST", BASE + "/" + segment(id) + "/void", null, mutation(key)); }
    public Map<String, Object> refundSplit(String id, String amount, String reason, String key) {
        if (amount == null) { throw new IllegalArgumentException("A split refund amount is required."); }
        Map<String, Object> body = amountBody(amount, reason);
        return api.request("POST", BASE + "/split/" + segment(id) + "/refund", body, mutation(key));
    }
    /** Same as {@link #getSplit(String)}: one split payment with its legs ({@code GET /payments/split/{id}}). */
    public Map<String, Object> getSplitPayment(String splitPaymentId) { return getSplit(splitPaymentId); }
    /** Same as {@link #refundSplit(String, String, String, String)}. */
    public Map<String, Object> refundSplitPayment(String splitPaymentId, String amount, String reason, String key) { return refundSplit(splitPaymentId, amount, reason, key); }

    // ── Payment methods ───────────────────────────────────────────────────────────────────────────────────────

    /**
     * The payment methods open for your account ({@code GET /payments/methods}, signed): per method its
     * {@code blockName}, {@code providerSelection} ({@link ProviderSelection}), {@code providerSelectionMode}
     * ({@link ProviderSelectionMode}), {@code preauth} support, limits, {@code splitEligible} /
     * {@code splitBehavior} and providers with the {@code inputFields} to send in the method block. Read
     * {@code providerCode} values from here; never fix them in code.
     *
     * @return the gateway envelope, {@code data: { methods: [...] }}
     */
    public Map<String, Object> listPaymentMethods() { return listPaymentMethods(null); }

    /**
     * The row of one method ({@code ?paymentMethod=}); {@code {methods: []}} when it is not open for the account.
     * {@code QR} is sent as {@code QR_CODE}.
     *
     * @param paymentMethod a method code such as {@link PaymentMethod#MOBILE_BILLING}, or {@code null} for all
     * @return the gateway envelope
     */
    public Map<String, Object> listPaymentMethods(String paymentMethod) {
        RequestOptions.Builder options = RequestOptions.builder().stepUp(true);
        if (paymentMethod != null) { options.query("paymentMethod", PaymentMethod.normalize(paymentMethod)); }
        return api.request("GET", BASE + "/methods", null, options.build());
    }

    /**
     * Starts a payment with any payment method from the merchant server ({@code POST /payments/direct/payment},
     * signed). This is the only server-side card charge ({@link DirectMethodPaymentRequest#withCard}, plus
     * {@code withForce3D(true)} for 3D Secure).
     *
     * <p>The gateway envelope comes back as it is: {@code data} holds {@code paymentId}, {@code orderId},
     * {@code status}, {@code paymentMethod}, {@code providerCode} and, when the buyer must act, {@code action}
     * (HTTP 202; see {@link PaymentActionType} and {@link PaymentBankTransferInstruction}). A declined payment is a
     * 400 and throws {@link ValidationException} whose {@code response()} carries the same fields.
     *
     * @param request        the body
     * @param idempotencyKey the persisted key of this payment (16–128 ASCII characters); reuse it when retrying
     * @return the gateway envelope
     */
    public Map<String, Object> directMethodPayment(DirectMethodPaymentRequest request, String idempotencyKey) {
        if (request == null) { throw new ConfigurationException("A direct method payment needs a DirectMethodPaymentRequest."); }
        return directMethodPayment(request.toBody(), idempotencyKey);
    }

    /**
     * Same as {@link #directMethodPayment(DirectMethodPaymentRequest, String)} for a body map. The body is checked
     * with the same rules: no {@code price} / {@code paidPrice}, known fields only, {@code paymentMethod},
     * {@code amount}, {@code currency}, {@code orderId} required, card fields only with {@code CARD}, exactly the
     * chosen method's block (structure, {@code providerCode}; none for {@code BANK_TRANSFER}), the promissory-note
     * plan, the shopping-loan basket (1–100 items), boolean {@code preauth} / {@code force3D} and the order details
     * ({@code buyer}, {@code billingAddress}, {@code basketItems}; {@code shippingAddress} for a {@code PHYSICAL}
     * item; {@link OrderDetails}).
     *
     * @param body           the gateway body
     * @param idempotencyKey the persisted key of this payment (16–128 ASCII characters)
     * @return the gateway envelope
     */
    public Map<String, Object> directMethodPayment(Map<String, Object> body, String idempotencyKey) {
        PaymentRules.assertDirectMethodPaymentPayload(body);
        String key = PaymentRules.operationKey("direct method payment", idempotencyKey);
        return api.request("POST", BASE + "/direct/payment", new LinkedHashMap<>(body),
                RequestOptions.builder().stepUp(true).idempotencyKey(key).build());
    }

    /**
     * Completes the open action of a payment ({@code POST /payments/{paymentId}/actions/{actionId}/confirm},
     * signed): the buyer's one-time code ({@code otp}, digits), the agreement consent ({@code consentAccepted})
     * when {@code action.consent.required} is true, or the values an {@code INPUT} action asks for
     * ({@code fields}). Repeating the same confirm returns the recorded result. A rejected code is a 422 with
     * {@code details.attemptsRemaining}; the action stays open.
     *
     * @param paymentId {@code data.paymentId}
     * @param actionId  {@code data.action.id}
     * @param input     {@code otp}, {@code consentAccepted} and/or {@code fields}; nothing else
     * @return the gateway envelope
     */
    public Map<String, Object> confirmPaymentAction(String paymentId, String actionId, Map<String, ?> input) {
        String path = actionPath(paymentId, actionId, "confirm");
        return api.request("POST", path, PaymentRules.confirmBody(input), RequestOptions.builder().stepUp(true).build());
    }

    /**
     * Sends a new one-time code ({@code POST /payments/{paymentId}/actions/{actionId}/resend}, signed, no body).
     * The previous action is closed; confirm with the new {@code data.action.id}.
     *
     * @param paymentId {@code data.paymentId}
     * @param actionId  {@code data.action.id}
     * @return the gateway envelope
     */
    public Map<String, Object> resendPaymentAction(String paymentId, String actionId) {
        return api.request("POST", actionPath(paymentId, actionId, "resend"), null, RequestOptions.builder().stepUp(true).build());
    }

    // ── Promissory notes (Senetle Ödeme) ──────────────────────────────────────────────────────────────────────

    /**
     * The notes of a Senetle Ödeme payment ({@code GET /payments/{paymentId}/promissory-notes}, signed):
     * {@code documentType}, {@code rowSerialNumber}, {@code totalAmount} and per note {@code serialNumber},
     * {@code sequence}, {@code amount}, {@code dueDate} and {@code status} ({@link PromissoryNoteValues.Status}).
     * A payment without notes is a 404 {@code payment.promissory_note.not_found}.
     *
     * @param paymentId the payment
     * @return the gateway envelope
     */
    public Map<String, Object> getPromissoryNotes(String paymentId) {
        return api.request("GET", promissoryNotesPath(paymentId), null, RequestOptions.builder().stepUp(true).build());
    }

    /**
     * Cancels every open note at the provider ({@code POST /payments/{paymentId}/promissory-notes/cancel}, signed,
     * no body).
     *
     * @param paymentId the payment
     * @return the gateway envelope: the note list plus {@code cancelResults[]}
     */
    public Map<String, Object> cancelPromissoryNotes(String paymentId) { return cancelPromissoryNotes(paymentId, null, null); }

    /**
     * Cancels the given notes ({@code {serialNumbers}}); {@code null} cancels every open note.
     *
     * @param paymentId     the payment
     * @param serialNumbers 1–24 serial numbers from {@link #getPromissoryNotes}, or {@code null}
     * @return the gateway envelope
     */
    public Map<String, Object> cancelPromissoryNotes(String paymentId, List<String> serialNumbers) { return cancelPromissoryNotes(paymentId, serialNumbers, null); }

    /**
     * Cancels notes at the provider. The answer is the note list plus {@code cancelResults[]} per note
     * ({@link PromissoryNoteValues.CancelResult}) with a Lunixi {@code errorCode}
     * ({@link PromissoryNoteValues.CancelErrorCode}; {@code provider_unavailable} is temporary,
     * {@code cancel_rejected} permanent). The payment stays {@code CAPTURED}: no money moved through the platform.
     *
     * @param paymentId      the payment
     * @param serialNumbers  1–24 serial numbers, or {@code null} for every open note
     * @param idempotencyKey an optional {@code Idempotency-Key} (16–128 ASCII characters), or {@code null}
     * @return the gateway envelope
     */
    public Map<String, Object> cancelPromissoryNotes(String paymentId, List<String> serialNumbers, String idempotencyKey) {
        String path = promissoryNotesPath(paymentId) + "/cancel";
        Map<String, Object> body = null;
        if (serialNumbers != null) { body = new LinkedHashMap<>(); body.put("serialNumbers", PromissoryNotePlan.requireSerialNumbers(serialNumbers)); }
        RequestOptions.Builder options = RequestOptions.builder().stepUp(true);
        if (idempotencyKey != null) { options.idempotencyKey(PaymentRules.operationKey("promissory note cancel", idempotencyKey)); }
        return api.request("POST", path, body, options.build());
    }

    /**
     * The printable notes as a PDF in Turkish ({@code GET /payments/{paymentId}/promissory-notes/document}, signed).
     *
     * @param paymentId the payment
     * @return the document
     */
    public PromissoryNoteDocument downloadPromissoryNoteDocument(String paymentId) { return downloadPromissoryNoteDocument(paymentId, null); }

    /**
     * The printable notes as a PDF, one note per page. Built from the provider's current data and not stored. A
     * 503 {@code payment.promissory_note.document_unavailable} means the provider could not be reached; try again
     * later. Needs a transport that reads binary answers ({@link com.lunixi.sdk.http.HttpTransport#sendBinary}).
     *
     * @param paymentId the payment
     * @param locale    {@link PromissoryNoteValues.DocumentLocale#TR} or {@code EN}, or {@code null} for the default
     * @return the document
     */
    public PromissoryNoteDocument downloadPromissoryNoteDocument(String paymentId, String locale) {
        String path = promissoryNotesPath(paymentId) + "/document";
        RequestOptions.Builder options = RequestOptions.builder().stepUp(true);
        if (locale != null) { options.query("locale", PromissoryNotePlan.requireDocumentLocale(locale)); }
        ApiClient.Binary document = api.requestBinary("GET", path, PDF_ACCEPT, options.build());
        return new PromissoryNoteDocument(document.contentType(), document.bytes());
    }

    // ── Split payments ────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Starts a split payment, or continues it ({@code POST /payments/split}, signed). The idempotency key is the
     * identity of the split payment: after a step (an OTP confirmed, the buyer back from 3D Secure) send the SAME
     * body with the SAME key; the payment continues where it stopped.
     *
     * <p>The answer carries {@code splitPaymentId}, {@code status}, {@code steps} and {@code pendingStep}. While the
     * payment is running ({@link SplitPaymentValues.Status#IN_PROGRESS}) the gateway answers with HTTP 400 and the
     * status as {@code code}; that answer is returned here, not thrown. A failed payment throws
     * {@link ValidationException}.
     *
     * <p>The body is checked before sending: known fields, 1–5 legs adding up to {@code totalAmount}, card legs
     * with {@code cardToken}, non-card legs with exactly one of {@link SplitPaymentValues#SPLIT_LEG_BLOCK_FIELDS}
     * (with {@code providerCode}); meal card, open banking, shopping loan and bank transfer cannot be legs. The
     * order details are required ({@code buyer}, {@code billingAddress}, {@code basketItems}; {@code shippingAddress}
     * for a {@code PHYSICAL} item; {@link OrderDetails}): every leg goes to the payment provider with them.
     *
     * @param body           the gateway body
     * @param idempotencyKey the persisted key of this split payment (16–128 ASCII characters)
     * @return the gateway envelope
     */
    public Map<String, Object> createSplitPayment(Map<String, Object> body, String idempotencyKey) {
        SplitPaymentRules.assertPayload(body);
        String key = PaymentRules.operationKey("split payment", idempotencyKey);
        try {
            return api.request("POST", BASE + "/split", new LinkedHashMap<>(body), RequestOptions.builder().stepUp(true).idempotencyKey(key).build());
        } catch (ValidationException error) {
            Map<String, Object> running = runningSplitResponse(error);
            if (running == null) { throw error; }
            Money.validateFields(running, true);
            return running;
        }
    }

    /**
     * Split payments, newest first ({@code GET /payments/split}).
     *
     * @return the gateway envelope
     */
    public Map<String, Object> listSplitPayments() { return listSplitPayments(Map.of()); }

    /**
     * Split payments, newest first ({@code GET /payments/split}). Filters ({@link SplitPaymentValues#LIST_SPLIT_PAYMENTS_FILTERS}):
     * {@code status} and {@code currency} (a value or a list), {@code createdFrom}, {@code createdTo} (ISO 8601),
     * {@code orderId}, {@code ownerRef}, {@code limit} (1–100) and {@code after} (the {@code pageInfo.endCursor} of
     * the previous page).
     *
     * @param filters the filters
     * @return the gateway envelope
     */
    public Map<String, Object> listSplitPayments(Map<String, ?> filters) {
        String query = SplitPaymentRules.listQuery(filters);
        return api.request("GET", query.isEmpty() ? BASE + "/split" : BASE + "/split?" + query, null, RequestOptions.none());
    }

    // ── Bank transfer (Para Transferi) exceptions ─────────────────────────────────────────────────────────────

    /**
     * Bank transfer exceptions ({@code GET /payments/bank-transfer/exceptions}, signed), without filters.
     *
     * @return the gateway envelope, {@code data: { items: [...] }}
     */
    public Map<String, Object> listBankTransferExceptions() { return listBankTransferExceptions(null, null, null); }

    /**
     * Late ({@code LATE}), short ({@code UNDERPAID}) and excess ({@code OVERPAID}) transfers of
     * {@code BANK_TRANSFER} payments. The items carry {@code id}, {@code paymentId}, {@code kind}, {@code status},
     * {@code environment}, {@code amountMinor}, {@code currency}, {@code expectedMinor}, {@code receivedMinor},
     * the masked sender name, {@code acceptedPaymentId} and timestamps. {@code null} filters are not sent.
     *
     * @param environment {@code TEST} or {@code LIVE}, or {@code null}
     * @param status      a {@link PaymentBankTransferExceptionStatus} value, or {@code null}
     * @param limit       1–200, or {@code null}
     * @return the gateway envelope
     */
    public Map<String, Object> listBankTransferExceptions(String environment, String status, Integer limit) {
        RequestOptions.Builder options = RequestOptions.builder().stepUp(true);
        if (environment != null) {
            if (!BANK_TRANSFER_EXCEPTION_ENVIRONMENTS.contains(environment)) { throw new ConfigurationException("environment must be one of TEST, LIVE; got " + PaymentMethod.quote(environment) + "."); }
            options.query("environment", environment);
        }
        if (status != null) {
            if (!PaymentBankTransferExceptionStatus.ALL.contains(status)) { throw new ConfigurationException("status must be one of " + String.join(", ", PaymentBankTransferExceptionStatus.ALL) + "; got " + PaymentMethod.quote(status) + "."); }
            options.query("status", status);
        }
        if (limit != null) {
            if (limit < 1 || limit > BANK_TRANSFER_EXCEPTION_LIMIT_MAX) { throw new ConfigurationException("limit must be an integer between 1 and " + BANK_TRANSFER_EXCEPTION_LIMIT_MAX + "; got " + limit + "."); }
            options.query("limit", String.valueOf(limit));
        }
        return api.request("GET", BASE + "/bank-transfer/exceptions", null, options.build());
    }

    /**
     * Accepts a bank transfer exception ({@code POST …/exceptions/{id}/accept}, signed; the key needs
     * {@code payment:refund}). {@code LATE}: a new captured payment linked to the original is opened for the same
     * order ({@code acceptedPaymentId}); {@code UNDERPAID}: the payment is captured with the amount received.
     * {@code OVERPAID} cannot be accepted (409 {@code payment.bank_transfer.decision_not_supported}).
     *
     * @param exceptionId the exception's UUID ({@code items[].id})
     * @return the gateway envelope; {@code data} is the exception or {@code null}
     */
    public Map<String, Object> acceptBankTransferException(String exceptionId) {
        return api.request("POST", bankTransferExceptionPath(exceptionId, "accept"), null, RequestOptions.builder().stepUp(true).build());
    }

    /**
     * Reports that you refunded the money of a bank transfer exception to the sender from your own bank
     * ({@code POST …/exceptions/{id}/refunded}, signed; the key needs {@code payment:refund}). Lunixi does not send
     * the refund. For {@code UNDERPAID} the waiting payment closes as failed.
     *
     * @param exceptionId the exception's UUID ({@code items[].id})
     * @return the gateway envelope; {@code data} is the exception or {@code null}
     */
    public Map<String, Object> markBankTransferExceptionRefunded(String exceptionId) {
        return api.request("POST", bankTransferExceptionPath(exceptionId, "refunded"), null, RequestOptions.builder().stepUp(true).build());
    }

    // ── Payment list ──────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Payments, newest first ({@code GET /api/v1/payments}), without filters.
     *
     * @return the gateway envelope
     */
    public Map<String, Object> list() { return list(Map.of()); }

    /**
     * Payments, newest first ({@code GET /api/v1/payments}). A split payment is one row (its main payment,
     * {@code split.role} {@code PARENT}); {@code includeLegs: true} also returns each leg as a row
     * ({@code split.role} {@code LEG}). Rows of other payments carry no {@code split}.
     *
     * @param filters {@link #LIST_FILTERS}: {@code page}, {@code limit}, {@code status}, {@code customerId} and the
     *                boolean {@code includeLegs}; {@code null} and empty values are not sent
     * @return the gateway envelope
     */
    public Map<String, Object> list(Map<String, ?> filters) {
        RequestOptions.Builder options = RequestOptions.builder();
        Map<String, ?> given = filters == null ? Map.of() : filters;
        assertKnownFilters("list", given, LIST_FILTERS);
        for (String key : LIST_FILTERS) {
            Object value = given.get(key);
            if (value == null || "".equals(value)) { continue; }
            if ("includeLegs".equals(key)) {
                options.query(key, String.valueOf(PaymentRules.bool(value, "includeLegs")));
            } else {
                options.query(key, scalarFilter(key, value));
            }
        }
        return api.request("GET", BASE, null, options.build());
    }

    // ── Cards: BIN, installment table, stored cards ───────────────────────────────────────────────────────────

    /**
     * Card, bank and installment eligibility of a BIN ({@code POST /api/v1/bin/info}).
     *
     * @param binOrPan a 6- or 8-digit BIN or the card number (6–19 digits); never shown in an error message
     * @return the gateway envelope
     */
    public Map<String, Object> binInfo(String binOrPan) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("binOrPan", CardRules.binOrPan(binOrPan));
        return api.request("POST", BIN + "/info", body, RequestOptions.none());
    }

    /**
     * The installment table of an amount ({@code POST /api/v1/bin/installments}); with a BIN and
     * {@code installment > 1} the answer carries the binding {@code quoteToken} for
     * {@link DirectMethodPaymentRequest#withInstallment(int, String)}.
     *
     * @param request the body
     * @return the gateway envelope
     */
    public Map<String, Object> installmentOptions(InstallmentOptionsRequest request) {
        if (request == null) { throw new ConfigurationException("installmentOptions needs an InstallmentOptionsRequest."); }
        return api.request("POST", BIN + "/installments", request.toBody(), RequestOptions.none());
    }

    /**
     * Same as {@link #installmentOptions(InstallmentOptionsRequest)} for a body map. {@code amount} must be a
     * positive canonical minor-unit string; a given {@code enabledInstallments} follows the rule of
     * {@link InstallmentOptionsRequest#withEnabledInstallments} and is sent in ascending order.
     *
     * @param body the gateway body
     * @return the gateway envelope
     */
    public Map<String, Object> installmentOptions(Map<String, ?> body) {
        if (body == null) { throw new ConfigurationException("installmentOptions needs a body: amount and currency."); }
        Map<String, Object> copy = new LinkedHashMap<>(body);
        PaymentRules.positiveMinor(copy.get("amount"), "amount");
        if (copy.get("enabledInstallments") != null) {
            copy.put("enabledInstallments", EnabledInstallments.require(copy.get("enabledInstallments"), "enabledInstallments"));
        }
        return api.request("POST", BIN + "/installments", copy, RequestOptions.none());
    }

    /**
     * Brand, family and bank reference lists ({@code GET /api/v1/bin/card-taxonomy}).
     *
     * @return the gateway envelope
     */
    public Map<String, Object> cardTaxonomy() {
        return api.request("GET", BIN + "/card-taxonomy", null, RequestOptions.none());
    }

    /**
     * Stores a card ({@code POST /api/v1/payments/cards}, signed). The {@code Idempotency-Key} is required; reuse
     * it when retrying the same card. The buyer and the billing address are required (no basket;
     * {@link OrderDetails}); later stored-card charges take the buyer from this first payment.
     *
     * @param request        the body
     * @param idempotencyKey the persisted key of this operation (16–128 ASCII characters)
     * @return the gateway envelope: {@code storedCardToken}, {@code cardUserKey} and the masked card
     */
    public Map<String, Object> storeCard(StoreCardRequest request, String idempotencyKey) {
        if (request == null) { throw new ConfigurationException("storeCard needs a StoreCardRequest."); }
        return storeCard(request.toBody(), idempotencyKey);
    }

    /**
     * Same as {@link #storeCard(StoreCardRequest, String)} for a body map with a non-empty {@code card} map, the
     * {@code buyer} and the {@code billingAddress}.
     *
     * @param body           the gateway body
     * @param idempotencyKey the persisted key of this operation (16–128 ASCII characters)
     * @return the gateway envelope
     */
    public Map<String, Object> storeCard(Map<String, ?> body, String idempotencyKey) {
        if (body == null) { throw new ConfigurationException("storeCard needs a body with the card fields."); }
        CardRules.card(body.get("card"));
        OrderDetails.assertComplete(body, "Storing a card", false, false);
        String key = PaymentRules.operationKey("store card", idempotencyKey);
        return api.request("POST", BASE + "/cards", new LinkedHashMap<>(body),
                RequestOptions.builder().stepUp(true).idempotencyKey(key).build());
    }

    /**
     * The stored cards of a card owner ({@code GET /api/v1/payments/cards?cardUserKey=}).
     *
     * @param cardUserKey the card owner's key
     * @return the gateway envelope
     */
    public Map<String, Object> listStoredCards(String cardUserKey) {
        RequestOptions options = RequestOptions.builder().query("cardUserKey", PaymentRules.text(cardUserKey, "cardUserKey")).build();
        return api.request("GET", BASE + "/cards", null, options);
    }

    /**
     * One stored card ({@code GET /api/v1/payments/cards/{storedCardToken}}).
     *
     * @param storedCardToken the token from {@link #storeCard} or {@link #listStoredCards}
     * @return the gateway envelope
     */
    public Map<String, Object> getStoredCard(String storedCardToken) {
        return api.request("GET", storedCardPath(storedCardToken), null, RequestOptions.none());
    }

    /**
     * Deactivates a stored card ({@code DELETE /api/v1/payments/cards/{storedCardToken}}, signed). The
     * {@code Idempotency-Key} is required.
     *
     * @param storedCardToken the token of the card
     * @param idempotencyKey  the persisted key of this operation (16–128 ASCII characters)
     * @return the gateway envelope
     */
    public Map<String, Object> deactivateStoredCard(String storedCardToken, String idempotencyKey) {
        String path = storedCardPath(storedCardToken);
        String key = PaymentRules.operationKey("deactivate stored card", idempotencyKey);
        return api.request("DELETE", path, null, RequestOptions.builder().stepUp(true).idempotencyKey(key).build());
    }

    // ── Marketplace ───────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Same as {@link #createMarketplaceIntent(Map, String)} without an {@code Idempotency-Key}.
     *
     * @param body the gateway body
     * @return the gateway envelope
     */
    public Map<String, Object> createMarketplaceIntent(Map<String, ?> body) { return createMarketplaceIntent(body, null); }

    /**
     * Creates a marketplace checkout intent ({@code POST /api/v1/payments/marketplace/intents}, signed): the intent
     * body plus {@code sellers[]} ({@code { subDealerId, amountMinor, scenario? }}, {@code amountMinor} a canonical
     * minor-unit string) and {@code multiSellerPolicy}. Checked before sending: no {@code price} /
     * {@code paidPrice}, positive {@code amount}, {@code currency}, {@code orderId}, the sellers'
     * {@code amountMinor} values adding up to {@code amount}, and the order details as on {@link #createIntent}.
     *
     * @param body           the gateway body
     * @param idempotencyKey the persisted key of this attempt (16–128 ASCII characters), or {@code null}
     * @return the gateway envelope
     */
    public Map<String, Object> createMarketplaceIntent(Map<String, ?> body, String idempotencyKey) {
        MarketplaceIntentRules.assertPayload(body);
        OrderDetails.assertComplete(body, "A marketplace payment intent", true, true);
        RequestOptions.Builder options = RequestOptions.builder().stepUp(true);
        if (idempotencyKey != null) { options.idempotencyKey(PaymentRules.operationKey("marketplace intent", idempotencyKey)); }
        return api.request("POST", BASE + "/marketplace/intents", new LinkedHashMap<>(body), options.build());
    }

    // ── Analytics ─────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Payments rescued by provider failover retries ({@code GET /api/v1/payments/analytics/failover-recovery}).
     *
     * @return the gateway envelope
     */
    public Map<String, Object> failoverRecoveryAnalytics() { return failoverRecoveryAnalytics(Map.of()); }

    /**
     * Same as {@link #failoverRecoveryAnalytics()} with query filters such as {@code from}, {@code to} and
     * {@code range}. Values are strings, numbers, booleans or {@code java.time} values (sent as ISO 8601).
     *
     * @param filters the query filters
     * @return the gateway envelope
     */
    public Map<String, Object> failoverRecoveryAnalytics(Map<String, ?> filters) {
        return api.request("GET", BASE + "/analytics/failover-recovery", null, analyticsQuery(filters));
    }

    /**
     * Performance of one provider credential ({@code GET /api/v1/payments/analytics/provider-credentials/{id}}).
     *
     * @param credentialId the credential
     * @return the gateway envelope
     */
    public Map<String, Object> providerCredentialAnalytics(String credentialId) { return providerCredentialAnalytics(credentialId, Map.of()); }

    /**
     * Same as {@link #providerCredentialAnalytics(String)} with query filters (see
     * {@link #failoverRecoveryAnalytics(Map)}).
     *
     * @param credentialId the credential
     * @param filters      the query filters
     * @return the gateway envelope
     */
    public Map<String, Object> providerCredentialAnalytics(String credentialId, Map<String, ?> filters) {
        String path = BASE + "/analytics/provider-credentials/" + PaymentRules.segment("credentialId", credentialId);
        return api.request("GET", path, null, analyticsQuery(filters));
    }

    private static String storedCardPath(String storedCardToken) {
        return BASE + "/cards/" + PaymentRules.segment("storedCardToken", storedCardToken);
    }
    private static RequestOptions analyticsQuery(Map<String, ?> filters) {
        RequestOptions.Builder options = RequestOptions.builder();
        if (filters == null) { return options.build(); }
        for (Map.Entry<String, ?> entry : filters.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) { throw new ConfigurationException("Analytics filter names must be non-empty strings."); }
            if (entry.getValue() == null || "".equals(entry.getValue())) { continue; }
            options.query(entry.getKey(), scalarFilter(entry.getKey(), entry.getValue()));
        }
        return options.build();
    }
    private static void assertKnownFilters(String operation, Map<String, ?> filters, List<String> allowed) {
        List<String> unknown = filters.keySet().stream().filter(key -> key == null || !allowed.contains(key)).map(String::valueOf).toList();
        if (!unknown.isEmpty()) { throw new ConfigurationException(operation + " does not take " + String.join(", ", unknown) + "; filters: " + String.join(", ", allowed) + "."); }
    }
    private static String scalarFilter(String key, Object value) {
        if (value instanceof String || value instanceof Number || value instanceof Boolean) { return String.valueOf(value); }
        if (value instanceof java.time.Instant || value instanceof java.time.OffsetDateTime || value instanceof java.time.LocalDate) { return value.toString(); }
        if (value instanceof java.time.ZonedDateTime zoned) { return zoned.toOffsetDateTime().toString(); }
        throw new ConfigurationException(key + " must be a string, number, boolean or java.time value; got " + value.getClass().getSimpleName() + ".");
    }

    private static String actionPath(String paymentId, String actionId, String operation) {
        return BASE + "/" + PaymentRules.segment("paymentId (data.paymentId)", paymentId) + "/actions/"
                + PaymentRules.segment("actionId (data.action.id)", actionId) + "/" + operation;
    }
    private static String promissoryNotesPath(String paymentId) {
        return BASE + "/" + PaymentRules.segment("paymentId (data.paymentId of the promissory-note payment)", paymentId) + "/promissory-notes";
    }
    private static String bankTransferExceptionPath(String exceptionId, String decision) {
        if (exceptionId == null || !UUID.matcher(exceptionId).matches()) { throw new ConfigurationException("exceptionId must be the UUID of a bank transfer exception (items[].id)."); }
        return BASE + "/bank-transfer/exceptions/" + exceptionId + "/" + decision;
    }
    /**
     * The gateway answers a running split payment with HTTP 400 and the split status as {@code code}; only a body
     * that is the split payment itself ({@code data.splitPaymentId}) with a running status qualifies.
     */
    private static Map<String, Object> runningSplitResponse(ValidationException error) {
        if (error.statusCode() != 400) { return null; }
        Map<String, Object> body = error.response();
        if (!SplitPaymentValues.Status.IN_PROGRESS.contains(error.code().orElse(""))) { return null; }
        Object id = Envelope.data(body).get("splitPaymentId");
        return id instanceof String text && !text.isEmpty() ? body : null;
    }

    private Map<String, Object> financial(String id, String action, String amount, String reason, String key) {
        Map<String, Object> body = amountBody(amount, reason); body.put("paymentIntentId", id);
        return api.request("POST", BASE + "/" + segment(id) + "/" + action, body, mutation(key));
    }
    private static void positiveAmount(String amount) {
        if (Money.integer(amount) < 1) { throw new IllegalArgumentException("Positive minor units are required."); }
    }
    private static Map<String, Object> amountBody(String amount, String reason) {
        if (amount != null && Money.integer(amount) < 1) { throw new IllegalArgumentException("Positive minor units are required."); }
        Map<String, Object> body = new LinkedHashMap<>(); if (amount != null) { body.put("amount", amount); }
        if (reason != null && !reason.isBlank()) { body.put("reason", reason); }
        return body;
    }
    private static RequestOptions mutation(String key) {
        if (key == null || !key.matches("[\\x21-\\x7e]{16,128}")) { throw new IllegalArgumentException("A persisted ASCII idempotency key (16–128 characters) is required."); }
        return RequestOptions.builder().stepUp(true).idempotencyKey(key).build();
    }
    private static String segment(String id) {
        if (id == null || id.isBlank() || id.length() > 191 || id.equals(".") || id.equals("..")) { throw new IllegalArgumentException("A valid resource ID is required."); }
        return URLEncoder.encode(id, StandardCharsets.UTF_8).replace("+", "%20");
    }
    private static String text(Object value) { return value instanceof String ? (String) value : ""; }
    public static void httpsUrl(String value) {
        URI uri;
        try { uri = URI.create(value); } catch (RuntimeException error) { throw new IllegalArgumentException("An HTTPS URL is required."); }
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null) { throw new IllegalArgumentException("An HTTPS URL without userinfo/fragment is required."); }
    }
}
