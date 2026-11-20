package com.lunixi.sdk.paymentlink;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Closed value sets of the payment-link API ({@code /api/v1/payments/links}). The gateway checks each of them with
 * a fixed list and answers an unknown value with 400, so they are constants instead of strings typed at each call
 * site.
 */
public final class PaymentLinkValues {

    private PaymentLinkValues() {
    }

    /** {@code usage}: fixed at creation. */
    public static final class Usage {
        /** Can be paid once: an invoice or a personal payment request. */
        public static final String SINGLE_USE = "SINGLE_USE";
        /** Can be paid many times: donations, tickets, a product page. */
        public static final String MULTI_USE = "MULTI_USE";
        public static final List<String> ALL = List.of(SINGLE_USE, MULTI_USE);

        private Usage() {
        }
    }

    /** {@code amountMode}: fixed at creation. */
    public static final class AmountMode {
        /** The merchant sets the amount ({@code amountMinor}). */
        public static final String FIXED = "FIXED";
        /** The buyer enters the amount, bounded by {@code minAmountMinor} / {@code maxAmountMinor}. */
        public static final String OPEN = "OPEN";
        /** The buyer picks quantities from {@code items}; the amount is the sum of the lines. */
        public static final String ITEMIZED = "ITEMIZED";
        public static final List<String> ALL = List.of(FIXED, OPEN, ITEMIZED);

        private AmountMode() {
        }
    }

    /** {@code state} of a link. */
    public static final class State {
        public static final String ACTIVE = "ACTIVE";
        public static final String PAUSED = "PAUSED";
        public static final String COMPLETED = "COMPLETED";
        public static final String EXPIRED = "EXPIRED";
        public static final String DEACTIVATED = "DEACTIVATED";
        /** Removed by Lunixi. Every write on the link answers 409 {@code payment_link.link.taken_down}. */
        public static final String TAKEN_DOWN = "TAKEN_DOWN";
        public static final List<String> ALL = List.of(ACTIVE, PAUSED, COMPLETED, EXPIRED, DEACTIVATED, TAKEN_DOWN);

        private State() {
        }
    }

    /** {@code state} of a payment attempt on a link. */
    public static final class AttemptState {
        public static final String OPEN = "OPEN";
        public static final String BOUND = "BOUND";
        public static final String SUCCEEDED = "SUCCEEDED";
        public static final String FAILED = "FAILED";
        public static final String EXPIRED = "EXPIRED";
        public static final String ABANDONED = "ABANDONED";
        public static final String NEEDS_REVIEW = "NEEDS_REVIEW";
        public static final List<String> ALL = List.of(OPEN, BOUND, SUCCEEDED, FAILED, EXPIRED, ABANDONED,
                NEEDS_REVIEW);

        private AttemptState() {
        }
    }

    /** {@code environment}: the API key already decides it; when sent it must equal the key's environment. */
    public static final class Environment {
        public static final String TEST = "TEST";
        public static final String LIVE = "LIVE";
        public static final List<String> ALL = List.of(TEST, LIVE);

        private Environment() {
        }
    }

    /**
     * Value of a {@code buyerFields} entry. Payment providers require the buyer, the identity number and the
     * billing address, so the link page always asks all five fields: {@code REQUIRED} is the only value (the
     * gateway answers any other with 400; leaving a field out means {@code REQUIRED}).
     */
    public static final class BuyerFieldRequirement {
        public static final String REQUIRED = "REQUIRED";
        public static final List<String> ALL = List.of(REQUIRED);

        private BuyerFieldRequirement() {
        }
    }

    /** {@code fieldType} of a custom field (no file uploads). */
    public static final class CustomFieldType {
        public static final String TEXT = "TEXT";
        public static final String NUMBER = "NUMBER";
        public static final String EMAIL = "EMAIL";
        public static final String PHONE = "PHONE";
        public static final String DATE = "DATE";
        public static final String SELECT = "SELECT";
        public static final String CHECKBOX = "CHECKBOX";
        public static final String TCKN = "TCKN";
        public static final String VKN = "VKN";
        public static final List<String> ALL = List.of(TEXT, NUMBER, EMAIL, PHONE, DATE, SELECT, CHECKBOX, TCKN,
                VKN);

        private CustomFieldType() {
        }
    }

    /** {@code buttonLabelKey} of the payment page. */
    public static final class ButtonLabel {
        public static final String PAY = "PAY";
        public static final String DONATE = "DONATE";
        public static final String BUY = "BUY";
        public static final String BOOK = "BOOK";
        public static final List<String> ALL = List.of(PAY, DONATE, BUY, BOOK);

        private ButtonLabel() {
        }
    }

    /** {@code channel} of {@code send} and of {@code verification}. */
    public static final class DeliveryChannel {
        public static final String EMAIL = "EMAIL";
        public static final String SMS = "SMS";
        public static final List<String> ALL = List.of(EMAIL, SMS);

        private DeliveryChannel() {
        }
    }

    /** {@code format} of the QR code image. */
    public static final class QrFormat {
        public static final String SVG = "svg";
        public static final String PNG = "png";
        public static final List<String> ALL = List.of(SVG, PNG);

        private QrFormat() {
        }
    }

    /** Language of the payment page and of a sent message. */
    public static final class Locale {
        public static final String TR = "tr";
        public static final String EN = "en";
        public static final List<String> ALL = List.of(TR, EN);

        private Locale() {
        }
    }

    /** Declared type of a link image; the stored bytes are checked against it on confirmation. SVG is never accepted. */
    public static final class ImageContentType {
        public static final String PNG = "image/png";
        public static final String JPEG = "image/jpeg";
        public static final String WEBP = "image/webp";
        public static final List<String> ALL = List.of(PNG, JPEG, WEBP);

        private ImageContentType() {
        }
    }

    /**
     * Every field of a payment-link definition, in the gateway's REST spelling. Create and update bodies carry only
     * these keys (plus {@code environment} on create, {@code expectedRowVersion} on update); the gateway rejects
     * any other key with 400.
     */
    public static final List<String> PAYMENT_LINK_SPEC_FIELDS = List.of(
            "usage",
            "amountMode",
            "currency",
            "amountMinor",
            "minAmountMinor",
            "maxAmountMinor",
            "suggestedAmountsMinor",
            "quantityEnabled",
            "minQuantity",
            "maxQuantity",
            "items",
            "capacity",
            "title",
            "description",
            "imageAssetId",
            "successMessage",
            "successRedirectUrl",
            "taxNote",
            "locale",
            "startsAt",
            "expiresAt",
            "eventAt",
            "buttonLabelKey",
            "requireTermsAcceptance",
            "buyerFields",
            "customFields",
            "recipient",
            "prefill",
            "verification",
            "reminders",
            "checkout",
            "reference",
            "tags",
            "branchCode",
            "salesChannel",
            "campaign",
            "agentCode",
            "merchantNotifyEmails",
            "metadata");

    /** Fixed when the link is created; an update that names one answers 422 {@code payment_link.link.immutable_field}. */
    public static final List<String> PAYMENT_LINK_IMMUTABLE_FIELDS = List.of("usage", "amountMode", "currency");

    /** Filters of {@link PaymentLinksClient#list(java.util.Map)}. */
    public static final List<String> LINK_LIST_FILTERS = List.of("limit", "cursor", "includeTotal", "state", "usage",
            "amountMode", "environment", "tag", "branchCode", "createdById", "query", "createdFrom", "createdTo");

    /** Filters of {@link PaymentLinksClient#listAttempts(String, java.util.Map)}. */
    public static final List<String> ATTEMPT_LIST_FILTERS = List.of("limit", "cursor", "includeTotal", "state");

    /** Filters of {@link PaymentLinksClient#listPayments(String, java.util.Map)}. */
    public static final List<String> PAYMENT_LIST_FILTERS = List.of("limit", "cursor", "includeTotal");

    /** Actions of {@link PaymentLinksClient#action}. */
    public static final List<String> LINK_ACTIONS = List.of("pause", "resume", "deactivate");

    /** Smallest QR code size in pixels. */
    public static final int QR_MIN_SIZE = 128;
    /** Largest QR code size in pixels. */
    public static final int QR_MAX_SIZE = 2048;

    /** {@code Idempotency-Key} format of the payment-link routes: 8–200 printable ASCII characters, no spaces. */
    public static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile("[\\x21-\\x7E]{8,200}");

    /** Shape of a media asset id (a UUID; ownership and state are the server's). */
    public static final Pattern ASSET_ID_PATTERN = Pattern.compile(
            "(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
}
