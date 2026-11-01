package com.lunixi.sdk.physicalpos;

import static com.lunixi.sdk.physicalpos.PhysicalPosSupport.BASE;
import static com.lunixi.sdk.physicalpos.PhysicalPosSupport.MAX_SAFE_INTEGER;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.lunixi.sdk.exception.WebhookVerificationException;
import com.lunixi.sdk.internal.ApiClient;
import com.lunixi.sdk.internal.RequestOptions;
import com.lunixi.sdk.json.Json;
import com.lunixi.sdk.json.JsonParseException;
import com.lunixi.sdk.physicalpos.PhysicalPosSupport.Shape;
import com.lunixi.sdk.webhook.WebhookEvent;
import com.lunixi.sdk.webhook.WebhookVerifier;

/**
 * Physical POS payments — start a card payment on one of your devices, reverse or cancel it, and read payments,
 * transaction trees, reports and end-of-day results ({@code /api/v1/physical-pos}, product {@code PHYSICAL_POS}).
 *
 * <p>Every call is signed. The key decides the environment; a TEST key reads only and is answered
 * {@code 403 physical_pos.environment.test_key_read_only} on every write. Writes need the key scope
 * {@code physical_pos:payments}; reads accept {@code physical_pos:view} or {@code physical_pos:payments}.
 * Responses are the envelope's {@code data}, as maps in wire order.
 *
 * <h2>Card data</h2>
 * The API never takes card data: the card is read on the device. A request or filter key that looks like card
 * data (card number, CVV, expiry, track, PIN) is refused before anything is sent with
 * {@link com.lunixi.sdk.exception.PhysicalPosCardDataRejectedException}; the gateway's own refusal maps to the same
 * type.
 *
 * <h2>Idempotency</h2>
 * {@link #createPayment} and {@link #createReversal} REQUIRE an {@code Idempotency-Key}; without one nothing is
 * sent. Create it with {@link #newIdempotencyKey()}, store it with your order, then call:
 *
 * <pre>
 * String key = PhysicalPosClient.newIdempotencyKey();
 * orders.saveIdempotencyKey(orderId, key);          // before the call
 * try {
 *     pos.createPayment(request, key);
 * } catch (PhysicalPosOutcomeUnknownException unknown) {
 *     pos.createPayment(request, unknown.idempotencyKey()); // later: the SAME key, never a new one
 * }
 * </pre>
 *
 * The same key and body again returns the payment's current view with {@code replayed: true} (HTTP 200); the same
 * key with another body is {@code 409 physical_pos.idempotency_mismatch}. {@link #cancelPayment} takes an optional
 * key (a UUID is generated when you pass {@code null}). The SDK retries a payment write itself only with the same key
 * and body. When any attempt ended without a usable answer (no answer, a {@code 2xx} without the payment, a
 * {@code 5xx} the gateway did not mark as "nothing started") the call raises
 * {@link com.lunixi.sdk.exception.PhysicalPosOutcomeUnknownException} with the key. For {@code SALE}, also send a
 * {@code merchantReference} and keep {@code allowDuplicateReference} false: the gateway then refuses a second sale
 * with the same reference while the first one's outcome is open ({@code 409 previous_outcome_unknown}).
 *
 * <h2>Card numbers in text</h2>
 * {@code merchantReference}, item names and tax group codes, and the {@code merchantReference} / {@code q} filters
 * are scanned for a card number (13–19 digits, Luhn, also space or dash separated) before sending; a match is
 * {@link com.lunixi.sdk.exception.PhysicalPosCardDataRejectedException} with the field path only.
 *
 * <h2>Lists</h2>
 * Newest first, cursor paging ({@code limit} 1–100, default 25). {@code iterate*} methods follow the cursor
 * lazily. A {@code 429} is {@link com.lunixi.sdk.exception.PhysicalPosRateLimitException} with the delay.
 *
 * <p>Resolving a payment under manual review is a panel-only action and has no API method.
 */
public final class PhysicalPosClient {

    private static final long SAFE = MAX_SAFE_INTEGER;

    static final Shape TARGET = new Shape("target")
            .text("deviceId", false, false)
            .text("vendorCode", false, false)
            .text("serialNumber", false, false)
            .text("externalRef", false, false);

    static final Shape SALE_ITEM = new Shape("items")
            .integer("lineNo", true, false, -SAFE, SAFE)
            .text("name", true, false)
            .integer("quantityMilli", true, false, -SAFE, SAFE)
            .text("unitCode", true, false)
            .money("unitPriceMinor", true, false)
            .text("taxGroupCode", true, false)
            .money("lineTotalMinor", true, false)
            .text("barcode", false, true);

    static final Shape REFUND_ITEM = new Shape("items")
            .integer("lineNo", true, false, -SAFE, SAFE)
            .integer("quantityMilli", true, false, -SAFE, SAFE)
            .money("amountMinor", false, false);

    static final Shape CREATE_PAYMENT = new Shape("createPayment request")
            .text("operation", true, false)
            .object("target", TARGET, true, false)
            .money("amountMinor", true, false)
            .text("currency", true, false)
            .integer("installmentCount", false, true, -SAFE, SAFE)
            .text("instrument", false, true)
            .text("merchantReference", false, true)
            .bool("allowDuplicateReference")
            .integer("startTimeoutSeconds", false, true, -SAFE, SAFE)
            .text("terminalAcquirerId", false, true)
            .objectList("items", SALE_ITEM);

    static final Shape REVERSAL = new Shape("createReversal request")
            .text("operation", true, false)
            .money("amountMinor", false, false)
            .text("currency", true, false)
            .text("merchantReference", false, true)
            .integer("startTimeoutSeconds", false, true, -SAFE, SAFE)
            .object("target", TARGET, false, true)
            .objectList("items", REFUND_ITEM);

    static final List<String> PAYMENT_FILTERS = PhysicalPosSupport.list(
            "limit", "cursor", "deviceId", "paymentStatus", "createdFrom", "createdTo", "merchantReference");
    static final List<String> TRANSACTION_FILTERS = PhysicalPosSupport.list(
            "limit", "cursor", "deviceId", "storeId", "registerId", "paymentStatus", "createdFrom", "createdTo",
            "merchantReference", "amountFromMinor", "amountToMinor", "origin", "operation");
    static final List<String> AMBIGUOUS_FILTERS = PhysicalPosSupport.list(
            "limit", "cursor", "deviceId", "paymentStatus");
    static final List<String> SALES_SUMMARY_FILTERS = PhysicalPosSupport.list(
            "dateFrom", "dateTo", "deviceId", "storeId", "groupBy");
    static final List<String> TAX_SUMMARY_FILTERS = PhysicalPosSupport.list(
            "dateFrom", "dateTo", "deviceId", "storeId");
    static final List<String> SETTLEMENT_FILTERS = PhysicalPosSupport.list(
            "limit", "cursor", "deviceId", "lineStatus", "createdFrom", "createdTo");

    private final ApiClient api;

    public PhysicalPosClient(ApiClient api) {
        this.api = api;
    }

    /** @return a new random UUID to use as the {@code Idempotency-Key} of one payment write */
    public static String newIdempotencyKey() {
        return PhysicalPosSupport.newIdempotencyKey();
    }

    // ── Payments ─────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Starts a payment on a device ({@code POST /payments}): a sale, a pre-authorisation or an unmatched refund.
     *
     * <p>Body: {@code operation} ({@code SALE} | {@code PREAUTH_OPEN} | {@code REFUND_UNMATCHED}), {@code target}
     * (exactly one of {@code {deviceId}}, {@code {vendorCode, serialNumber}}, {@code {externalRef}}),
     * {@code amountMinor} (canonical string, minor units), {@code currency}, and optionally {@code installmentCount},
     * {@code instrument}, {@code merchantReference}, {@code allowDuplicateReference}, {@code startTimeoutSeconds},
     * {@code terminalAcquirerId}, {@code items} (SALE only; omit for a sale without items).
     *
     * <p>A new payment answers {@code 202} with {@code paymentStatus: NOT_STARTED}: accepted, not paid. Follow it
     * by webhook or {@link #getPayment}.
     *
     * @param request        the body
     * @param idempotencyKey your stored key (required; see {@link #newIdempotencyKey()})
     * @return the payment, {@code replayed}, the HTTP status and the key that was sent
     * @throws com.lunixi.sdk.exception.ConfigurationException without a key or with an invalid one (nothing sent)
     */
    public PhysicalPosWriteResult createPayment(Map<String, ?> request, String idempotencyKey) {
        Map<String, Object> body = PhysicalPosSupport.body(request, CREATE_PAYMENT);
        rejectPanValues(body);
        String key = PhysicalPosSupport.requiredIdempotencyKey(idempotencyKey, "createPayment");
        return paymentWrite(BASE + "/payments", body, key, 202);
    }

    /**
     * Reverses a payment ({@code POST /payments/{paymentId}/reversals}): {@code VOID}, {@code REFUND} (by amount or
     * by items), {@code PREAUTH_CLOSE}, {@code PREAUTH_CANCEL}. Opens a child payment of the original.
     *
     * <p>Body: {@code operation}, {@code currency} (the original's), {@code amountMinor} (REFUND and
     * PREAUTH_CLOSE; omit for VOID / PREAUTH_CANCEL; derived from {@code items} on an item refund), optional
     * {@code merchantReference}, {@code startTimeoutSeconds}, {@code target} (REFUND on another device only),
     * {@code items} ({@code [{lineNo, quantityMilli, amountMinor?}]}, REFUND only).
     *
     * @param paymentId      the original payment
     * @param request        the body
     * @param idempotencyKey your stored key (required; see {@link #newIdempotencyKey()})
     * @return the child payment, {@code replayed}, the HTTP status (202 new / 200 replay) and the key
     * @throws com.lunixi.sdk.exception.ConfigurationException without a key or with an invalid one (nothing sent)
     */
    public PhysicalPosWriteResult createReversal(String paymentId, Map<String, ?> request, String idempotencyKey) {
        String path = BASE + "/payments/" + PhysicalPosSupport.segment(paymentId, "paymentId") + "/reversals";
        Map<String, Object> body = PhysicalPosSupport.body(request, REVERSAL);
        rejectPanValues(body);
        String key = PhysicalPosSupport.requiredIdempotencyKey(idempotencyKey, "createReversal");
        return paymentWrite(path, body, key, 202);
    }

    /**
     * Cancels a payment before its start command reached the device ({@code POST /payments/{paymentId}/cancel}).
     *
     * <p>Always {@code 200}. Read {@code paymentStatus}: only {@code EXPIRED} with
     * {@code statusReason = MERCHANT_CANCELLED_BEFORE_DISPATCH} means it was cancelled; a payment already final is
     * returned as it is ({@code replayed: true}), even when {@code APPROVED} — reverse an approved sale with
     * {@link #createReversal}. A command already dispatched is {@code 409 physical_pos.payment.not_cancellable}.
     *
     * @param paymentId      the payment
     * @param idempotencyKey your key, or {@code null} for a generated UUID
     * @return the payment, {@code replayed}, the HTTP status and the key
     */
    public PhysicalPosWriteResult cancelPayment(String paymentId, String idempotencyKey) {
        String path = BASE + "/payments/" + PhysicalPosSupport.segment(paymentId, "paymentId") + "/cancel";
        return paymentWrite(path, Map.of(), PhysicalPosSupport.idempotencyKey(idempotencyKey), 200);
    }

    /** {@link #cancelPayment(String, String)} with a generated key. */
    public PhysicalPosWriteResult cancelPayment(String paymentId) {
        return cancelPayment(paymentId, null);
    }

    /**
     * Reads a payment ({@code GET /payments/{paymentId}}), with {@code items} and {@code refundItems}. Another
     * account's payment and a missing one are the same {@code 404 physical_pos.payment.not_found}.
     *
     * @param paymentId the payment
     * @return the payment
     */
    public Map<String, Object> getPayment(String paymentId) {
        String path = BASE + "/payments/" + PhysicalPosSupport.segment(paymentId, "paymentId");
        return entity(read(path, RequestOptions.builder().stepUp(true)), "payment");
    }

    /**
     * Lists payments, roots and children together ({@code GET /payments}). Filters: {@code limit}, {@code cursor},
     * {@code deviceId}, {@code paymentStatus}, {@code createdFrom}, {@code createdTo} (ISO-8601 UTC ending in
     * {@code Z}), {@code merchantReference} (exact match — the way to find a payment after an unknown outcome).
     * Items are empty in lists; read one payment for them.
     *
     * @param filters the filters, or {@code null}
     * @return one page
     */
    public PhysicalPosPage listPayments(Map<String, ?> filters) {
        return page(BASE + "/payments", filters, PAYMENT_FILTERS, "listPayments");
    }

    /** @return every payment matching the filters, page by page */
    public Iterable<Map<String, Object>> iteratePayments(Map<String, ?> filters) {
        return PhysicalPosSupport.iterate(filters, this::listPayments);
    }

    // ── Transactions, reports, end of day ────────────────────────────────────────────────────────────────────

    /**
     * Lists transaction trees ({@code GET /transactions}): {@code {root, children (≤ 50), childrenTruncated,
     * itemSummary, storeId, registerId}}. Filters: {@code limit}, {@code cursor}, {@code deviceId},
     * {@code storeId}, {@code registerId}, {@code paymentStatus}, {@code createdFrom}, {@code createdTo},
     * {@code merchantReference}, {@code amountFromMinor}, {@code amountToMinor}, {@code origin},
     * {@code operation}.
     *
     * @param filters the filters, or {@code null}
     * @return one page
     */
    public PhysicalPosPage listTransactions(Map<String, ?> filters) {
        return page(BASE + "/transactions", filters, TRANSACTION_FILTERS, "listTransactions");
    }

    /** @return every transaction tree matching the filters, page by page */
    public Iterable<Map<String, Object>> iterateTransactions(Map<String, ?> filters) {
        return PhysicalPosSupport.iterate(filters, this::listTransactions);
    }

    /**
     * Reads one transaction tree with its timeline ({@code GET /transactions/{paymentId}}); a child id returns its
     * root's tree.
     *
     * @param paymentId a root or child payment
     * @return {@code {requestedPaymentId, root, children, timeline, timelineTruncated, childrenTruncated, storeId,
     *         registerId}}
     */
    public Map<String, Object> getTransaction(String paymentId) {
        String path = BASE + "/transactions/" + PhysicalPosSupport.segment(paymentId, "paymentId");
        return entity(read(path, RequestOptions.builder().stepUp(true)), "transaction");
    }

    /**
     * Payments with an unknown outcome or under manual review ({@code GET /transactions/ambiguous}). Filters:
     * {@code limit}, {@code cursor}, {@code deviceId}, {@code paymentStatus} ({@code UNKNOWN} | {@code MANUAL_REVIEW}).
     *
     * @param filters the filters, or {@code null}
     * @return one page of payments
     */
    public PhysicalPosPage listAmbiguousPayments(Map<String, ?> filters) {
        return page(BASE + "/transactions/ambiguous", filters, AMBIGUOUS_FILTERS, "listAmbiguousPayments");
    }

    /** @return every ambiguous payment, page by page */
    public Iterable<Map<String, Object>> iterateAmbiguousPayments(Map<String, ?> filters) {
        return PhysicalPosSupport.iterate(filters, this::listAmbiguousPayments);
    }

    /**
     * Sales, refunds and voids with the net ({@code GET /reports/sales-summary}). Filters: {@code dateFrom},
     * {@code dateTo} (business days {@code YYYY-MM-DD}, Europe/Istanbul), {@code deviceId}, {@code storeId},
     * {@code groupBy} ({@code DAY} | {@code STORE} | {@code DEVICE} | {@code INSTRUMENT}).
     *
     * @param filters the filters, or {@code null}
     * @return {@code {environment, dateFrom, dateTo, groupBy, rows}}
     */
    public Map<String, Object> getSalesSummary(Map<String, ?> filters) {
        return report(BASE + "/reports/sales-summary", filters, SALES_SUMMARY_FILTERS, "getSalesSummary");
    }

    /**
     * Tax breakdown with refunds and voids deducted ({@code GET /reports/tax-summary}). Filters: {@code dateFrom},
     * {@code dateTo}, {@code deviceId}, {@code storeId}.
     *
     * @param filters the filters, or {@code null}
     * @return {@code {environment, dateFrom, dateTo, rows, amountTotals}}
     */
    public Map<String, Object> getTaxSummary(Map<String, ?> filters) {
        return report(BASE + "/reports/tax-summary", filters, TAX_SUMMARY_FILTERS, "getTaxSummary");
    }

    /**
     * End-of-day results per bank line with the differences to the system totals ({@code GET /settlements}).
     * Filters: {@code limit}, {@code cursor} (opaque), {@code deviceId}, {@code lineStatus} ({@code MATCHED} |
     * {@code MISMATCH} | {@code UNMAPPED}), {@code createdFrom}, {@code createdTo}.
     *
     * <p>Each line carries exactly one of {@code commandId} (end-of-day taken with a Lunixi command) and
     * {@code settlementRef} (started on the device), plus {@code acquirerRefRaw} (the bank code as reported),
     * {@code acquirerName} and {@code deviceName}.
     *
     * @param filters the filters, or {@code null}
     * @return one page of settlement lines
     */
    public PhysicalPosPage listSettlements(Map<String, ?> filters) {
        return page(BASE + "/settlements", filters, SETTLEMENT_FILTERS, "listSettlements");
    }

    /** @return every settlement line, page by page */
    public Iterable<Map<String, Object>> iterateSettlements(Map<String, ?> filters) {
        return PhysicalPosSupport.iterate(filters, this::listSettlements);
    }

    // ── Webhooks ─────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Verifies a physical POS webhook with {@link WebhookVerifier} (default five-minute replay window) and reads it.
     * There is no way to read an unverified body.
     *
     * <pre>
     * PhysicalPosWebhookEvent event = PhysicalPosClient.parseWebhookEvent(rawBody, headers, secret);
     * </pre>
     *
     * @param rawBody the exact request body, unparsed
     * @param headers the request headers ({@code x-lunixi-signature}, {@code x-lunixi-signature-timestamp},
     *                {@code x-lunixi-event-id})
     * @param secret  the endpoint's signing secret
     * @return the event
     * @throws WebhookVerificationException when the signature does not verify, or it is not a physical POS event
     */
    public static PhysicalPosWebhookEvent parseWebhookEvent(String rawBody, Map<String, String> headers,
                                                            String secret) {
        return parseWebhookEvent(rawBody, headers, secret, WebhookVerifier.DEFAULT_TOLERANCE);
    }

    /**
     * {@link #parseWebhookEvent(String, Map, String)} with an explicit replay window.
     *
     * @param tolerance how far the signature timestamp may be from now ({@code null} skips the check — keep it on)
     */
    public static PhysicalPosWebhookEvent parseWebhookEvent(String rawBody, Map<String, String> headers,
                                                            String secret, Duration tolerance) {
        return parseWebhookEvent(new WebhookVerifier().verify(rawBody, headers, secret, tolerance));
    }

    /**
     * Reads an event already verified by {@link WebhookVerifier}. {@link WebhookEvent} can only be created by the
     * verifier, so this overload cannot be fed an unverified body. The event type comes from the signed body.
     *
     * @param verified the verifier's result
     * @return the event
     */
    public static PhysicalPosWebhookEvent parseWebhookEvent(WebhookEvent verified) {
        if (verified == null) {
            throw new WebhookVerificationException("Doğrulanmış olay zorunlu / a verified event is required.");
        }
        // The id falls back to the x-lunixi-event-id header, which is covered by the signature; the type header
        // is not signed and is never used.
        return parse(verified.envelope(), verified.id());
    }

    private static PhysicalPosWebhookEvent parse(Map<String, Object> envelope, String fallbackId) {
        if (envelope == null) {
            throw new WebhookVerificationException("Olay zarfı zorunlu / the event envelope is required.");
        }
        String type = firstText(envelope.get("type"), envelope.get("eventType"), null);
        if (type == null || !type.startsWith("physical_pos.")) {
            throw new WebhookVerificationException("Fiziksel POS olayı değil / not a physical POS event (type "
                    + type + ").");
        }
        String id = firstText(envelope.get("id"), envelope.get("eventId"), fallbackId);
        if (id == null) {
            throw new WebhookVerificationException("Olay kimliği yok / the event id is missing.");
        }
        Map<String, Object> data = payload(envelope);
        PhysicalPosMoney.validate(data);
        String category = type.startsWith("physical_pos.payment.") ? PhysicalPosWebhookEvent.CATEGORY_PAYMENT
                : type.startsWith("physical_pos.fiscal_document.") ? PhysicalPosWebhookEvent.CATEGORY_FISCAL_DOCUMENT
                : PhysicalPosWebhookEvent.CATEGORY_OTHER;
        Object seq = data.get("historySeq");
        Long historySeq = seq instanceof Number number ? Long.valueOf(number.longValue()) : null;
        return new PhysicalPosWebhookEvent(id, type, category, firstText(envelope.get("environment"), null, null),
                firstText(envelope.get("createdAt"), envelope.get("occurredAt"), firstText(envelope.get("timestamp"),
                        null, null)),
                historySeq, data, envelope);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> payload(Map<String, Object> envelope) {
        Object data = envelope.get("data");
        if (data instanceof Map<?, ?> map && !map.isEmpty()) {
            return (Map<String, Object>) map;
        }
        Object payloadJson = envelope.get("payloadJson");
        if (payloadJson instanceof Map<?, ?> map && !map.isEmpty()) {
            return (Map<String, Object>) map;
        }
        if (payloadJson instanceof String text && !text.isBlank()) {
            try {
                Map<String, Object> decoded = Json.parseObject(text);
                if (!decoded.isEmpty()) {
                    return decoded;
                }
            } catch (JsonParseException notJson) {
                throw new WebhookVerificationException("payloadJson JSON nesnesi değil / payloadJson is not a JSON"
                        + " object.");
            }
        }
        throw new WebhookVerificationException("Olay yükü boş / the event carries no payload (data / payloadJson).");
    }

    private static String firstText(Object first, Object second, String third) {
        if (first instanceof String text && !text.isEmpty()) {
            return text;
        }
        if (second instanceof String text && !text.isEmpty()) {
            return text;
        }
        return third == null || third.isEmpty() ? null : third;
    }

    // ── Internals ────────────────────────────────────────────────────────────────────────────────────────────

    private PhysicalPosWriteResult paymentWrite(String path, Map<String, Object> body, String key, int newStatus) {
        Map<String, Object> envelope = PhysicalPosSupport.paymentWrite(api, path, body, key);
        Map<String, Object> data = PhysicalPosSupport.data(envelope);
        boolean replayed = Boolean.TRUE.equals(data.get("replayed"));
        int status = PhysicalPosSupport.statusCode(envelope, replayed ? 200 : newStatus);
        return new PhysicalPosWriteResult("payment", data, status, key);
    }

    /** Card numbers in free text (O-4): reference and tax group strictly, item names with the barcode exemption. */
    @SuppressWarnings("unchecked")
    private static void rejectPanValues(Map<String, Object> body) {
        PhysicalPosSupport.rejectPanValue(body.get("merchantReference"), "merchantReference", false);
        if (body.get("items") instanceof List<?> items) {
            for (int i = 0; i < items.size(); i++) {
                Map<String, Object> item = (Map<String, Object>) items.get(i);
                PhysicalPosSupport.rejectPanValue(item.get("name"), "items[" + i + "].name", true);
                PhysicalPosSupport.rejectPanValue(item.get("taxGroupCode"), "items[" + i + "].taxGroupCode", false);
            }
        }
    }

    private Map<String, Object> read(String path, RequestOptions.Builder options) {
        return PhysicalPosSupport.send(api, "GET", path, null, options.build(), null);
    }

    private static Map<String, Object> entity(Map<String, Object> envelope, String name) {
        return PhysicalPosSupport.object(PhysicalPosSupport.data(envelope).get(name));
    }

    private PhysicalPosPage page(String path, Map<String, ?> filters, List<String> allowed, String route) {
        return new PhysicalPosPage(read(path, PhysicalPosSupport.query(filters, allowed, route)));
    }

    private Map<String, Object> report(String path, Map<String, ?> filters, List<String> allowed, String route) {
        return PhysicalPosSupport.data(read(path, PhysicalPosSupport.query(filters, allowed, route)));
    }
}
