package com.lunixi.sdk.paymentlink;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.lunixi.sdk.exception.ConfigurationException;
import com.lunixi.sdk.payment.EnabledInstallments;

/**
 * Body of {@link PaymentLinksClient#create(CreatePaymentLinkRequest, String)} ({@code POST /api/v1/payments/links}).
 *
 * <pre>
 * Map&lt;String, Object&gt; link = client.paymentLinks().create(
 *         CreatePaymentLinkRequest.fixed(PaymentLinkValues.Usage.SINGLE_USE, "150000", "TRY", "Invoice 2026-001")
 *                 .withRecipient(Map.of("email", "buyer@example.com"))
 *                 .withExpiresAt(Instant.now().plus(Duration.ofDays(7))),
 *         persistedOperationKey);
 * </pre>
 *
 * <p>Amounts are canonical strings in the currency's minor unit: {@code "150000"} is 1,500.00 TRY. {@code usage},
 * {@code amountMode} and {@code currency} are fixed at creation. The builder checks types and value sets and
 * throws {@link ConfigurationException}; the business rules (title length, amount ranges, which fields a mode
 * allows) are enforced by the gateway, which answers 422 {@code payment_link.validation.invalid} with
 * {@code details.errors}. Keys are sent in the order they are set.
 */
public final class CreatePaymentLinkRequest {

    /** Keys of {@code buyerFields}. */
    public static final List<String> BUYER_FIELD_KEYS = List.of("name", "email", "phone", "address", "identityNumber");
    /** Keys of {@code recipient}. */
    public static final List<String> RECIPIENT_KEYS = List.of("name", "email", "phone");
    /** Keys of {@code prefill}. */
    public static final List<String> PREFILL_KEYS = List.of("name", "surname", "email", "phone", "identityNumber",
            "address", "city", "country", "zipCode");

    private final Map<String, Object> payload = new LinkedHashMap<>();

    private CreatePaymentLinkRequest(String usage, String amountMode, String currency, String title) {
        payload.put("usage", PaymentLinkRules.oneOf(usage, PaymentLinkValues.Usage.ALL, "usage"));
        payload.put("amountMode", PaymentLinkRules.oneOf(amountMode, PaymentLinkValues.AmountMode.ALL, "amountMode"));
        payload.put("currency", PaymentLinkRules.text(currency == null ? null : currency.toUpperCase(Locale.ROOT),
                "currency"));
        payload.put("title", PaymentLinkRules.text(title, "title"));
    }

    /**
     * A link for a set amount.
     *
     * @param usage       a {@link PaymentLinkValues.Usage} value
     * @param amountMinor the amount, a canonical minor-unit string
     * @param currency    ISO 4217 code such as {@code TRY}
     * @param title       the title shown to the buyer
     * @return a new request
     */
    public static CreatePaymentLinkRequest fixed(String usage, String amountMinor, String currency, String title) {
        return new CreatePaymentLinkRequest(usage, PaymentLinkValues.AmountMode.FIXED, currency, title)
                .withAmountMinor(amountMinor);
    }

    /**
     * A link where the buyer enters the amount (donations, deposits).
     *
     * @param usage          a {@link PaymentLinkValues.Usage} value
     * @param currency       ISO 4217 code
     * @param title          the title shown to the buyer
     * @param minAmountMinor the smallest amount, required
     * @return a new request; add {@link #withMaxAmountMinor} and {@link #withSuggestedAmountsMinor} as needed
     */
    public static CreatePaymentLinkRequest open(String usage, String currency, String title, String minAmountMinor) {
        return new CreatePaymentLinkRequest(usage, PaymentLinkValues.AmountMode.OPEN, currency, title)
                .withMinAmountMinor(minAmountMinor);
    }

    /**
     * A link where the buyer picks quantities of the given lines (1–50 items).
     *
     * @param usage    a {@link PaymentLinkValues.Usage} value
     * @param currency ISO 4217 code
     * @param title    the title shown to the buyer
     * @param items    the lines
     * @return a new request
     */
    public static CreatePaymentLinkRequest itemized(String usage, String currency, String title,
                                                    List<PaymentLinkItem> items) {
        return new CreatePaymentLinkRequest(usage, PaymentLinkValues.AmountMode.ITEMIZED, currency, title)
                .withItems(items);
    }

    /**
     * Optional. The API key already decides the environment; when set, it must equal the key's environment or the
     * gateway answers 403 {@code payment_link.environment.mismatch}.
     */
    public CreatePaymentLinkRequest withEnvironment(String environment) {
        payload.put("environment", PaymentLinkRules.oneOf(environment, PaymentLinkValues.Environment.ALL,
                "environment"));
        return this;
    }

    public CreatePaymentLinkRequest withAmountMinor(String amountMinor) {
        payload.put("amountMinor", PaymentLinkRules.minor(amountMinor, "amountMinor"));
        return this;
    }

    public CreatePaymentLinkRequest withMinAmountMinor(String minAmountMinor) {
        payload.put("minAmountMinor", PaymentLinkRules.minor(minAmountMinor, "minAmountMinor"));
        return this;
    }

    public CreatePaymentLinkRequest withMaxAmountMinor(String maxAmountMinor) {
        payload.put("maxAmountMinor", PaymentLinkRules.minor(maxAmountMinor, "maxAmountMinor"));
        return this;
    }

    /** Up to six ascending suggested amounts of an {@code OPEN} link. */
    public CreatePaymentLinkRequest withSuggestedAmountsMinor(List<String> amounts) {
        payload.put("suggestedAmountsMinor", PaymentLinkRules.minorList(amounts, "suggestedAmountsMinor"));
        return this;
    }

    /** Lets the buyer choose a quantity ({@code FIXED} + {@code MULTI_USE} only), 1–999. */
    public CreatePaymentLinkRequest withQuantity(int minQuantity, int maxQuantity) {
        payload.put("quantityEnabled", true);
        payload.put("minQuantity", minQuantity);
        payload.put("maxQuantity", maxQuantity);
        return this;
    }

    public CreatePaymentLinkRequest withItems(List<PaymentLinkItem> items) {
        if (items == null || items.isEmpty()) {
            throw new ConfigurationException("An ITEMIZED payment link needs at least one item.");
        }
        List<Map<String, Object>> lines = new ArrayList<>();
        for (PaymentLinkItem item : items) {
            if (item == null) {
                throw new ConfigurationException("items must not contain null.");
            }
            lines.add(item.toBody());
        }
        payload.put("items", lines);
        return this;
    }

    @SuppressWarnings("unchecked")
    public CreatePaymentLinkRequest addItem(PaymentLinkItem item) {
        if (item == null) {
            throw new ConfigurationException("item must not be null.");
        }
        List<Map<String, Object>> lines = new ArrayList<>();
        if (payload.get("items") instanceof List<?> existing) {
            lines.addAll((List<Map<String, Object>>) existing);
        }
        lines.add(item.toBody());
        payload.put("items", lines);
        return this;
    }

    /** Total units the link can sell across all payments ({@code MULTI_USE} only); {@code null} = unlimited. */
    public CreatePaymentLinkRequest withCapacity(Integer capacity) {
        payload.put("capacity", capacity);
        return this;
    }

    /** Plain text, shown as text (no HTML or Markdown). */
    public CreatePaymentLinkRequest withDescription(String description) {
        payload.put("description", String.valueOf(description));
        return this;
    }

    /** A published payment-link image of your organisation (uuid); {@code null} = none. */
    public CreatePaymentLinkRequest withImageAssetId(String imageAssetId) {
        payload.put("imageAssetId", PaymentLinkRules.assetIdOrNull(imageAssetId, "imageAssetId"));
        return this;
    }

    public CreatePaymentLinkRequest withSuccessMessage(String successMessage) {
        payload.put("successMessage", String.valueOf(successMessage));
        return this;
    }

    /** {@code https://} only; {@code null} clears it. */
    public CreatePaymentLinkRequest withSuccessRedirectUrl(String successRedirectUrl) {
        payload.put("successRedirectUrl", successRedirectUrl == null ? null
                : PaymentLinkRules.text(successRedirectUrl, "successRedirectUrl"));
        return this;
    }

    public CreatePaymentLinkRequest withTaxNote(String taxNote) {
        payload.put("taxNote", String.valueOf(taxNote));
        return this;
    }

    /** Language of the payment page: {@link PaymentLinkValues.Locale}. */
    public CreatePaymentLinkRequest withLocale(String locale) {
        payload.put("locale", PaymentLinkRules.oneOf(locale, PaymentLinkValues.Locale.ALL, "locale"));
        return this;
    }

    /** An ISO 8601 string, {@code Instant}, {@code OffsetDateTime} or {@code ZonedDateTime}; {@code null} clears it. */
    public CreatePaymentLinkRequest withStartsAt(Object startsAt) {
        payload.put("startsAt", PaymentLinkRules.isoOrNull(startsAt, "startsAt"));
        return this;
    }

    /** Same value types as {@link #withStartsAt(Object)}. */
    public CreatePaymentLinkRequest withExpiresAt(Object expiresAt) {
        payload.put("expiresAt", PaymentLinkRules.isoOrNull(expiresAt, "expiresAt"));
        return this;
    }

    /** Display only (e.g. the event date of a ticket). Same value types as {@link #withStartsAt(Object)}. */
    public CreatePaymentLinkRequest withEventAt(Object eventAt) {
        payload.put("eventAt", PaymentLinkRules.isoOrNull(eventAt, "eventAt"));
        return this;
    }

    /** A {@link PaymentLinkValues.ButtonLabel} value; {@code null} = your organisation's default. */
    public CreatePaymentLinkRequest withButtonLabelKey(String buttonLabelKey) {
        payload.put("buttonLabelKey", buttonLabelKey == null ? null
                : PaymentLinkRules.oneOf(buttonLabelKey, PaymentLinkValues.ButtonLabel.ALL, "buttonLabelKey"));
        return this;
    }

    public CreatePaymentLinkRequest withTermsAcceptanceRequired(boolean required) {
        payload.put("requireTermsAcceptance", required);
        return this;
    }

    /**
     * Each of {@link #BUYER_FIELD_KEYS}: a {@link PaymentLinkValues.BuyerFieldRequirement} value; {@code null}
     * clears it.
     */
    public CreatePaymentLinkRequest withBuyerFields(Map<String, String> buyerFields) {
        if (buyerFields != null) {
            PaymentLinkRules.assertKeys(buyerFields.keySet(), BUYER_FIELD_KEYS, "buyerFields");
            buyerFields.forEach((key, requirement) -> PaymentLinkRules.oneOf(requirement,
                    PaymentLinkValues.BuyerFieldRequirement.ALL, "buyerFields." + key));
        }
        payload.put("buyerFields", buyerFields == null ? null : new LinkedHashMap<>(buyerFields));
        return this;
    }

    public CreatePaymentLinkRequest withCustomFields(List<PaymentLinkCustomField> fields) {
        if (fields == null) {
            throw new ConfigurationException("customFields must be a list.");
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (PaymentLinkCustomField field : fields) {
            if (field == null) {
                throw new ConfigurationException("customFields must not contain null.");
            }
            out.add(field.toBody());
        }
        payload.put("customFields", out);
        return this;
    }

    @SuppressWarnings("unchecked")
    public CreatePaymentLinkRequest addCustomField(PaymentLinkCustomField field) {
        if (field == null) {
            throw new ConfigurationException("custom field must not be null.");
        }
        List<Map<String, Object>> out = new ArrayList<>();
        if (payload.get("customFields") instanceof List<?> existing) {
            out.addAll((List<Map<String, Object>>) existing);
        }
        out.add(field.toBody());
        payload.put("customFields", out);
        return this;
    }

    /** Who the link is for ({@link #RECIPIENT_KEYS}; {@code phone} in E.164). Stored encrypted. */
    public CreatePaymentLinkRequest withRecipient(Map<String, String> recipient) {
        payload.put("recipient", PaymentLinkRules.stringObject(recipient, RECIPIENT_KEYS, "recipient"));
        return this;
    }

    /**
     * Buyer details filled in on the server side and never sent to the browser ({@link #PREFILL_KEYS};
     * {@code country} as ISO 3166-1 alpha-2).
     */
    public CreatePaymentLinkRequest withPrefill(Map<String, String> prefill) {
        payload.put("prefill", PaymentLinkRules.stringObject(prefill, PREFILL_KEYS, "prefill"));
        return this;
    }

    /** Requires the buyer to confirm a one-time code sent to {@code recipient.email} before paying (only {@code EMAIL}). */
    public CreatePaymentLinkRequest withVerification(boolean required, String channel) {
        Map<String, Object> verification = new LinkedHashMap<>();
        verification.put("required", required);
        verification.put("channel", PaymentLinkRules.oneOf(channel, PaymentLinkValues.DeliveryChannel.ALL,
                "verification channel"));
        payload.put("verification", verification);
        return this;
    }

    /** Same as {@link #withVerification(boolean, String)} with {@code EMAIL}. */
    public CreatePaymentLinkRequest withVerification(boolean required) {
        return withVerification(required, PaymentLinkValues.DeliveryChannel.EMAIL);
    }

    /** Reminder e-mails ({@code SINGLE_USE} with a recipient): every 24–168 hours, at most 1–3 times. */
    public CreatePaymentLinkRequest withReminders(int intervalHours, int maxCount) {
        Map<String, Object> reminders = new LinkedHashMap<>();
        reminders.put("enabled", true);
        reminders.put("intervalHours", intervalHours);
        reminders.put("maxCount", maxCount);
        payload.put("reminders", reminders);
        return this;
    }

    /**
     * Narrows the checkout: {@code requireThreeDs} can only tighten, {@code paymentMethods} is a subset of your form
     * settings (empty = your form settings), {@code enabledInstallments} lists the installment counts the form may
     * offer (empty = no restriction; otherwise distinct integers 1–36 that include 1, sent in ascending order).
     */
    public CreatePaymentLinkRequest withCheckout(boolean requireThreeDs, List<String> paymentMethods,
                                                 List<Integer> enabledInstallments) {
        Map<String, Object> checkout = new LinkedHashMap<>();
        checkout.put("requireThreeDs", requireThreeDs);
        checkout.put("paymentMethods", PaymentLinkRules.stringList(paymentMethods == null ? List.of() : paymentMethods,
                "paymentMethods"));
        if (enabledInstallments != null && !enabledInstallments.isEmpty()) {
            checkout.put("enabledInstallments", EnabledInstallments.require(enabledInstallments,
                    "checkout.enabledInstallments"));
        }
        payload.put("checkout", checkout);
        return this;
    }

    public CreatePaymentLinkRequest withReference(String reference) {
        payload.put("reference", String.valueOf(reference));
        return this;
    }

    public CreatePaymentLinkRequest withTags(List<String> tags) {
        payload.put("tags", PaymentLinkRules.stringList(tags, "tags"));
        return this;
    }

    public CreatePaymentLinkRequest withBranchCode(String branchCode) {
        payload.put("branchCode", String.valueOf(branchCode));
        return this;
    }

    public CreatePaymentLinkRequest withSalesChannel(String salesChannel) {
        payload.put("salesChannel", String.valueOf(salesChannel));
        return this;
    }

    public CreatePaymentLinkRequest withCampaign(String campaign) {
        payload.put("campaign", String.valueOf(campaign));
        return this;
    }

    public CreatePaymentLinkRequest withAgentCode(String agentCode) {
        payload.put("agentCode", String.valueOf(agentCode));
        return this;
    }

    public CreatePaymentLinkRequest withMerchantNotifyEmails(List<String> emails) {
        payload.put("merchantNotifyEmails", PaymentLinkRules.stringList(emails, "merchantNotifyEmails"));
        return this;
    }

    /** Up to 20 string key/value pairs returned to you in webhooks; {@code null} clears them. */
    public CreatePaymentLinkRequest withMetadata(Map<String, String> metadata) {
        payload.put("metadata", PaymentLinkRules.stringRecord(metadata, "metadata"));
        return this;
    }

    /** @return the body that is sent, a new map */
    public Map<String, Object> toBody() {
        return new LinkedHashMap<>(payload);
    }
}
