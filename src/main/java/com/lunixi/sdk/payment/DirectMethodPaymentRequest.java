package com.lunixi.sdk.payment;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.lunixi.sdk.exception.ConfigurationException;

/**
 * Body of {@link PaymentsClient#directMethodPayment}: a payment with any payment method, from the merchant server.
 * Every rule is checked before anything is sent; a rule violation is a {@link ConfigurationException}.
 *
 * <pre>
 * // Para Transferi: the buyer's bank from the method row's bankCode options (9999 = other banks)
 * DirectMethodPaymentRequest request = DirectMethodPaymentRequest.create("10000", "TRY", "order-1001")
 *         .withMethodFields(PaymentMethod.BANK_TRANSFER, null, Map.of("bankCode", "0062"));
 *
 * // Senetle Ödeme with a schedule (amounts add up to the payment amount)
 * DirectMethodPaymentRequest notes = DirectMethodPaymentRequest.create("10000", "TRY", "order-1002")
 *         .withMethodFields(PaymentMethod.PROMISSORY_NOTE, providerCode, debtorFields)
 *         .withPromissoryNoteSchedule(List.of(
 *                 Map.of("amount", "5000", "dueDate", "2027-01-15"),
 *                 Map.of("amount", "5000", "dueDate", "2027-02-15")));
 * </pre>
 *
 * <p>{@code amount} is the only amount field: the basket total in minor units (a canonical positive string such as
 * {@code "10000"}), without any installment fee; basket item {@code price} values must add up to it. Choose the
 * method with {@link #withMethodFields} or {@link #withCard}; {@code providerCode} comes from
 * {@link PaymentsClient#listPaymentMethods()}, never from a fixed value.
 */
public final class DirectMethodPaymentRequest {

    private final Map<String, Object> payload = new LinkedHashMap<>();
    private String paymentMethod;
    private Map<String, Object> block;
    private Map<String, Object> promissoryNotePlan;

    private DirectMethodPaymentRequest(String amount, String currency, String orderId) {
        payload.put("amount", PaymentRules.positiveMinor(amount, "amount"));
        payload.put("currency", PaymentRules.currency(currency));
        payload.put("orderId", PaymentRules.boundedText(orderId, "orderId", PaymentRules.ORDER_ID_MAX_LENGTH));
    }

    /**
     * @param amount   basket total in minor units, a canonical positive string such as {@code "10000"}
     * @param currency ISO 4217 code such as {@code TRY}
     * @param orderId  your order number (at most 128 characters)
     * @return a new request
     */
    public static DirectMethodPaymentRequest create(String amount, String currency, String orderId) {
        return new DirectMethodPaymentRequest(amount, currency, orderId);
    }

    /**
     * Pays with a method other than card. {@code fields} are the provider's {@code inputFields} values (for
     * example {@code Map.of("gsmNumber", "+905321234567")} for Faturana Yansıt); an empty map when the provider asks
     * for nothing or the value comes from {@code buyer} ({@code prefill}).
     *
     * @param paymentMethod a {@link PaymentMethod} code with a block in {@link PaymentMethod#METHOD_BLOCK_FIELDS}
     * @param providerCode  the provider from {@code listPaymentMethods()}, or {@code null} when the method row
     *                      allows it ({@code providerSelection: OPTIONAL}); always {@code null} for
     *                      {@link PaymentMethod#BANK_TRANSFER}
     * @param fields        the block fields (never {@code providerCode}; for promissory notes not the schedule)
     * @return this request
     */
    public DirectMethodPaymentRequest withMethodFields(String paymentMethod, String providerCode,
                                                       Map<String, ?> fields) {
        String code = PaymentMethod.normalize(paymentMethod);
        if (PaymentMethod.CARD.equals(code)) {
            throw new ConfigurationException("Use withCard(card) to pay by card.");
        }
        String blockName = PaymentMethod.blockName(code);
        if (!PaymentMethod.METHOD_BLOCK_FIELDS.contains(blockName)) {
            throw new ConfigurationException("paymentMethod " + code + " cannot be paid through direct/payment yet; "
                    + "payments().listPaymentMethods() lists the methods that can.");
        }
        Map<String, Object> methodFields = PaymentRules.requireMethodFields(fields == null ? Map.of() : fields,
                blockName);
        if (PaymentMethod.PROMISSORY_NOTE.equals(code) && methodFields.containsKey("schedule")) {
            throw new ConfigurationException("Pass the promissory-note schedule with "
                    + "withPromissoryNoteSchedule(schedule), not in the fields.");
        }
        if (PaymentMethod.PROMISSORY_NOTE.equals(code) && methodFields.containsKey("noteCount")) {
            methodFields.put("noteCount", PromissoryNotePlan.requireNoteCount(methodFields.get("noteCount"),
                    "promissoryNote.noteCount"));
        }
        if (PaymentMethod.BANK_TRANSFER.equals(code) && providerCode != null) {
            throw new ConfigurationException("paymentMethod BANK_TRANSFER takes no providerCode: pass null and the "
                    + "buyer's bank as {bankCode}.");
        }
        Map<String, Object> methodBlock = new LinkedHashMap<>();
        if (providerCode != null) {
            methodBlock.put("providerCode", PaymentRules.requireProviderCode(providerCode));
        }
        methodBlock.putAll(methodFields);
        this.paymentMethod = code;
        this.block = methodBlock;
        return this;
    }

    /**
     * Same as {@link #withMethodFields(String, String, Map)} with no fields.
     *
     * @param paymentMethod a {@link PaymentMethod} code
     * @param providerCode  the provider, or {@code null}
     * @return this request
     */
    public DirectMethodPaymentRequest withMethodFields(String paymentMethod, String providerCode) {
        return withMethodFields(paymentMethod, providerCode, Map.of());
    }

    /**
     * Senetle Ödeme plan: one note per row, {@code [{amount, dueDate}]}. Amounts are positive canonical minor-unit
     * strings adding up to {@code amount}; due dates are {@code YYYY-MM-DD} calendar days in non-descending order,
     * at most 24 rows. Use with {@code withMethodFields(PaymentMethod.PROMISSORY_NOTE, …)}; replaces a note count.
     *
     * @param schedule the rows
     * @return this request
     */
    public DirectMethodPaymentRequest withPromissoryNoteSchedule(List<? extends Map<String, ?>> schedule) {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("schedule", PromissoryNotePlan.requireSchedule(schedule, "promissoryNote.schedule"));
        this.promissoryNotePlan = plan;
        return this;
    }

    /**
     * Senetle Ödeme plan: the number of notes (1–24, within your provider connection limits). The amount is split
     * evenly; due dates follow your connection settings. Sent as a string ({@code "3"}). Replaces a schedule.
     *
     * @param count the number of notes
     * @return this request
     */
    public DirectMethodPaymentRequest withPromissoryNoteCount(int count) {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("noteCount", PromissoryNotePlan.requireNoteCount(count, "promissoryNote.noteCount"));
        this.promissoryNotePlan = plan;
        return this;
    }

    /**
     * Pays by card: new card fields, or {@code cardUserKey} + {@code cardToken} for a stored card.
     *
     * @param card the card fields
     * @return this request
     */
    public DirectMethodPaymentRequest withCard(Map<String, ?> card) {
        if (card == null || card.isEmpty()) {
            throw new ConfigurationException("withCard requires non-empty card fields.");
        }
        this.paymentMethod = PaymentMethod.CARD;
        this.block = new LinkedHashMap<>(card);
        return this;
    }

    /**
     * Card only. {@code quoteToken} binds the charge to a quote from the installment table; the quoted installment
     * fee is added to {@code amount}.
     *
     * @param count      the installment count
     * @param quoteToken the quote token, or {@code null}
     * @return this request
     */
    public DirectMethodPaymentRequest withInstallment(int count, String quoteToken) {
        if (count < 1) {
            throw new ConfigurationException("installment must be a positive integer; got " + count + ".");
        }
        payload.put("installment", count);
        if (quoteToken != null) {
            payload.put("installmentQuoteToken", PaymentRules.text(quoteToken, "installmentQuoteToken"));
        }
        return this;
    }

    /**
     * Card only; same as {@link #withInstallment(int, String)} without a quote token.
     *
     * @param count the installment count
     * @return this request
     */
    public DirectMethodPaymentRequest withInstallment(int count) {
        return withInstallment(count, null);
    }

    /**
     * Card only. {@code true} charges with 3D Secure: the answer carries a {@code REDIRECT} action.
     *
     * @param force3D whether to use 3D Secure
     * @return this request
     */
    public DirectMethodPaymentRequest withForce3D(boolean force3D) {
        payload.put("force3D", force3D);
        return this;
    }

    /**
     * Pre-authorization: {@code true} holds the amount and the payment becomes {@code AUTHORIZED}; take it with
     * {@code capture} or release it with {@code voidPayment}. A method whose providers do not support it is refused
     * with 422 {@code payment.preauth.unsupported_for_method}.
     *
     * @param preauth whether to pre-authorize
     * @return this request
     */
    public DirectMethodPaymentRequest withPreauth(boolean preauth) {
        payload.put("preauth", preauth);
        return this;
    }

    /**
     * Where the buyer's browser returns after a {@code REDIRECT} action, with the signed result form.
     *
     * @param callbackUrl an HTTPS URL
     * @return this request
     */
    public DirectMethodPaymentRequest withCallbackUrl(String callbackUrl) {
        payload.put("callbackUrl", PaymentRules.httpsUrl(callbackUrl, "callbackUrl"));
        return this;
    }

    /** @param locale the buyer's language, e.g. {@code tr} @return this request */
    public DirectMethodPaymentRequest withLocale(String locale) {
        payload.put("locale", PaymentRules.text(locale, "locale"));
        return this;
    }

    /** @param description the payment description @return this request */
    public DirectMethodPaymentRequest withDescription(String description) {
        return put("description", description);
    }

    /** @param customerId your customer reference @return this request */
    public DirectMethodPaymentRequest withCustomerId(String customerId) {
        return put("customerId", customerId);
    }

    /** @param paymentChannel the payment channel @return this request */
    public DirectMethodPaymentRequest withPaymentChannel(String paymentChannel) {
        return put("paymentChannel", paymentChannel);
    }

    /** @param paymentGroup the payment group @return this request */
    public DirectMethodPaymentRequest withPaymentGroup(String paymentGroup) {
        return put("paymentGroup", paymentGroup);
    }

    /** @param buyer the buyer fields (also the {@code prefill} source of method fields) @return this request */
    public DirectMethodPaymentRequest withBuyer(Map<String, ?> buyer) {
        return put("buyer", buyer == null ? null : new LinkedHashMap<>(buyer));
    }

    /** @param address the billing address fields @return this request */
    public DirectMethodPaymentRequest withBillingAddress(Map<String, ?> address) {
        return put("billingAddress", address == null ? null : new LinkedHashMap<>(address));
    }

    /** @param address the shipping address fields @return this request */
    public DirectMethodPaymentRequest withShippingAddress(Map<String, ?> address) {
        return put("shippingAddress", address == null ? null : new LinkedHashMap<>(address));
    }

    /**
     * @param basketItems the basket items; each {@code price} a canonical minor-unit string, the prices adding up
     *                    to {@code amount}; required (1–100 items) for {@link PaymentMethod#SHOPPING_LOAN}
     * @return this request
     */
    public DirectMethodPaymentRequest withBasketItems(List<? extends Map<String, ?>> basketItems) {
        if (basketItems == null) {
            throw new ConfigurationException("basketItems must be a list.");
        }
        List<Map<String, Object>> items = new ArrayList<>(basketItems.size());
        for (Map<String, ?> item : basketItems) {
            if (item == null) {
                throw new ConfigurationException("basketItems must not contain null.");
            }
            items.add(new LinkedHashMap<>(item));
        }
        payload.put("basketItems", items);
        return this;
    }

    /** @param metadata your own data, not read for payment decisions @return this request */
    public DirectMethodPaymentRequest withMetadata(Map<String, ?> metadata) {
        return put("metadata", metadata == null ? null : new LinkedHashMap<>(metadata));
    }

    private DirectMethodPaymentRequest put(String key, Object value) {
        if (value == null) {
            payload.remove(key);
        } else {
            payload.put(key, value);
        }
        return this;
    }

    /**
     * The body that is sent: {@code paymentMethod}, the order and option fields, then the method's block.
     *
     * @return a new map
     * @throws ConfigurationException when no method was chosen or the fields do not fit the method
     */
    public Map<String, Object> toBody() {
        if (paymentMethod == null) {
            throw new ConfigurationException("Choose the payment method with withMethodFields(method, providerCode, "
                    + "fields) or withCard(card).");
        }
        if (!PaymentMethod.CARD.equals(paymentMethod)) {
            for (String field : PaymentMethod.CARD_ONLY_FIELDS) {
                if (payload.containsKey(field)) {
                    throw new ConfigurationException(field + " can only be sent with a card payment (withCard).");
                }
            }
        }
        Map<String, Object> methodBlock = new LinkedHashMap<>(block);
        if (PaymentMethod.PROMISSORY_NOTE.equals(paymentMethod)) {
            if (promissoryNotePlan != null) {
                if (methodBlock.containsKey("noteCount")) {
                    throw new ConfigurationException("The promissory-note plan is given twice: noteCount in the "
                            + "fields and withPromissoryNoteSchedule()/withPromissoryNoteCount(). Use one.");
                }
                methodBlock.putAll(promissoryNotePlan);
            }
            PromissoryNotePlan.assertBlock(methodBlock, payload.get("amount"), "promissoryNote");
        } else if (promissoryNotePlan != null) {
            throw new ConfigurationException("A promissory-note plan can only be sent with paymentMethod "
                    + "PROMISSORY_NOTE.");
        }
        PaymentRules.assertShoppingLoanBasket(paymentMethod, payload.get("basketItems"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("paymentMethod", paymentMethod);
        body.putAll(payload);
        body.put(PaymentMethod.CARD.equals(paymentMethod) ? "card" : PaymentMethod.blockName(paymentMethod),
                methodBlock);
        return body;
    }
}
