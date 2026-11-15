package com.lunixi.sdk.insurance;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Insurance wire values, request fields and query filters ({@code /api/v1/insurance}, product
 * {@code INSURANCE_SERVICE}). The same sets and lists as the Node SDK's {@code insurance-values.js}.
 */
public final class InsuranceValues {

    /** The type objects of the type body, one per insurance type ({@code InsuranceProduct.typeObject}). */
    public static final List<String> TYPE_OBJECT_FIELDS = List.of("tickets", "stay", "device", "product", "vehicle",
            "trip", "rental", "instrument", "event", "accident", "procedure", "pet", "residence", "abroadStay");
    /** Top-level fields of {@link InsuranceClient#createQuote} (the gateway answers any other with 400). */
    public static final List<String> QUOTE_FIELDS = concat(List.of("insuranceType", "schemaVersion", "currency",
            "paymentIntentId", "context", "merchantOrderId", "policyholder", "insured"), TYPE_OBJECT_FIELDS);
    /**
     * Top-level fields of {@link InsuranceClient#createLead}: the type body of a quote without the basket, currency
     * and order fields, plus {@code shipment} and {@code company} (lead schemas only) and the free {@code note}.
     */
    public static final List<String> LEAD_FIELDS = concat(concat(List.of("insuranceType", "schemaVersion",
            "policyholder", "insured"), TYPE_OBJECT_FIELDS), List.of("shipment", "company", "note"));
    /** Fields of {@link InsuranceClient#searchProducts}. */
    public static final List<String> PRODUCT_SEARCH_FIELDS = List.of("currency", "insuranceType", "context");
    /** Fields of {@link InsuranceClient#createOrder}. */
    public static final List<String> ORDER_FIELDS = List.of("quoteId", "merchantOrderId", "consent");
    /** Fields of {@code consent} ({@link InsuranceClient#createOrder}). */
    public static final List<String> CONSENT_FIELDS = List.of("termsVersion", "termsSha256", "acceptedAt");
    /** Fields of {@link InsuranceClient#confirmPayment} (a premium collected outside Lunixi). */
    public static final List<String> EXTERNAL_PAYMENT_FIELDS = List.of("externalPaymentId", "paidAmount",
            "paidCurrency", "paidAt", "paymentProvider");
    /** Fields of {@code externalRefund} ({@link InsuranceClient#refundPremium} outside Lunixi). */
    public static final List<String> EXTERNAL_REFUND_FIELDS = List.of("externalRefundId", "refundedAt");
    /** Query filters each read accepts (the gateway rejects any other with 400). */
    public static final Map<String, List<String>> FILTERS = Map.of(
            "listProducts", List.of("currency", "paymentIntentId", "insuranceType"),
            "listReferenceValues", List.of("parentCode", "limit", "cursor"),
            "listOrders", List.of("limit", "cursor", "paymentIntentId", "merchantOrderId"),
            "getReconciliationSummary", List.of("from", "to", "connectionId", "insuranceType", "currency"),
            "listReconciliationOrders", List.of("from", "to", "connectionId", "insuranceType", "currency", "limit",
                    "cursor"),
            "exportReconciliation", List.of("from", "to", "connectionId", "insuranceType", "currency", "cursor"),
            "listLeads", List.of("limit", "cursor", "insuranceType", "status"));
    /** {@code Idempotency-Key} of the insurance routes: 8–200 printable ASCII characters, no spaces. */
    public static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile("^[\\x21-\\x7E]{8,200}$");
    /** Largest page ({@code limit} 1–100). */
    public static final int MAX_LIMIT = 100;
    /** Most basket items one {@code context.items} may carry. */
    public static final int MAX_CONTEXT_ITEMS = 100;
    /** Orders in one reconciliation CSV file; a longer export ends with a continuation line. */
    public static final int EXPORT_ORDER_CAP = 20000;
    /** Longest lead {@code note} (characters). */
    public static final int LEAD_NOTE_MAX_LENGTH = 1000;

    private InsuranceValues() {
    }

    private static List<String> concat(List<String> first, List<String> second) {
        List<String> out = new ArrayList<>(first);
        out.addAll(second);
        return List.copyOf(out);
    }

    /** Who collects the premium. {@code PROVIDER} orders cannot be completed through this API yet. */
    public static final class PremiumCollectedBy {
        public static final String MERCHANT = "MERCHANT";
        public static final String PROVIDER = "PROVIDER";
        public static final List<String> ALL = List.of(MERCHANT, PROVIDER);

        private PremiumCollectedBy() {
        }
    }

    /** Status of a quote option. Only a {@code QUOTED} option that has not expired becomes an order. */
    public static final class QuoteStatus {
        public static final String REQUESTED = "REQUESTED";
        public static final String QUOTED = "QUOTED";
        public static final String EXPIRED = "EXPIRED";
        public static final String REJECTED = "REJECTED";
        public static final String ERROR = "ERROR";
        public static final List<String> ALL = List.of(REQUESTED, QUOTED, EXPIRED, REJECTED, ERROR);

        private QuoteStatus() {
        }
    }

    /**
     * The order's overall status. It is not stored: every read derives it from the four axes, in this order —
     * {@code REFUNDED}, {@code CANCELLED}, {@code FAILED}, {@code ACTION_REQUIRED}, {@code PROCESSING},
     * {@code PARTIALLY_ACTIVE}, {@code ACTIVE}, {@code CREATED}.
     */
    public static final class OrderStatus {
        public static final String CREATED = "CREATED";
        public static final String PROCESSING = "PROCESSING";
        public static final String ACTION_REQUIRED = "ACTION_REQUIRED";
        public static final String PARTIALLY_ACTIVE = "PARTIALLY_ACTIVE";
        public static final String ACTIVE = "ACTIVE";
        public static final String FAILED = "FAILED";
        public static final String CANCELLED = "CANCELLED";
        public static final String REFUNDED = "REFUNDED";
        public static final List<String> ALL = List.of(CREATED, PROCESSING, ACTION_REQUIRED, PARTIALLY_ACTIVE, ACTIVE,
                FAILED, CANCELLED, REFUNDED);

        private OrderStatus() {
        }
    }

    /** Premium collection axis ({@code collectionStatus}). */
    public static final class CollectionStatus {
        public static final String NOT_STARTED = "NOT_STARTED";
        public static final String PENDING = "PENDING";
        public static final String ACTION_REQUIRED = "ACTION_REQUIRED";
        public static final String COLLECTED = "COLLECTED";
        public static final String FAILED = "FAILED";
        public static final String UNKNOWN = "UNKNOWN";
        public static final List<String> ALL = List.of(NOT_STARTED, PENDING, ACTION_REQUIRED, COLLECTED, FAILED,
                UNKNOWN);

        private CollectionStatus() {
        }
    }

    /** Policy axis ({@code policyStatus}) of an order and of each of its policies. */
    public static final class PolicyStatus {
        public static final String NOT_REQUESTED = "NOT_REQUESTED";
        public static final String PENDING = "PENDING";
        public static final String PARTIALLY_ISSUED = "PARTIALLY_ISSUED";
        public static final String ISSUED = "ISSUED";
        public static final String FAILED_RETRYABLE = "FAILED_RETRYABLE";
        public static final String FAILED_FINAL = "FAILED_FINAL";
        public static final String UNKNOWN = "UNKNOWN";
        public static final String CANCEL_PENDING = "CANCEL_PENDING";
        public static final String CANCELLED = "CANCELLED";
        public static final List<String> ALL = List.of(NOT_REQUESTED, PENDING, PARTIALLY_ISSUED, ISSUED,
                FAILED_RETRYABLE, FAILED_FINAL, UNKNOWN, CANCEL_PENDING, CANCELLED);

        private PolicyStatus() {
        }
    }

    /** Premium refund axis ({@code refundStatus}). */
    public static final class RefundStatus {
        public static final String NONE = "NONE";
        public static final String REQUIRED = "REQUIRED";
        public static final String PENDING = "PENDING";
        public static final String REFUNDED = "REFUNDED";
        public static final String FAILED = "FAILED";
        public static final List<String> ALL = List.of(NONE, REQUIRED, PENDING, REFUNDED, FAILED);

        private RefundStatus() {
        }
    }

    /** Policy document axis ({@code documentStatus}); it does not change the overall status. */
    public static final class DocumentStatus {
        public static final String NOT_AVAILABLE = "NOT_AVAILABLE";
        public static final String PENDING = "PENDING";
        public static final String AVAILABLE = "AVAILABLE";
        public static final String FAILED = "FAILED";
        public static final List<String> ALL = List.of(NOT_AVAILABLE, PENDING, AVAILABLE, FAILED);

        private DocumentStatus() {
        }
    }

    /** How the premium of an order is paid ({@code paymentMode}). */
    public static final class PaymentMode {
        public static final String LUNIXI_PAYMENT = "LUNIXI_PAYMENT";
        public static final String EXTERNAL_PAYMENT = "EXTERNAL_PAYMENT";
        public static final String NO_PAYMENT = "NO_PAYMENT";
        public static final List<String> ALL = List.of(LUNIXI_PAYMENT, EXTERNAL_PAYMENT, NO_PAYMENT);

        private PaymentMode() {
        }
    }

    /** Where an order was created ({@code surface}). */
    public static final class OrderSurface {
        public static final String MERCHANT_API = "MERCHANT_API";
        public static final String CHECKOUT_FORM = "CHECKOUT_FORM";
        public static final String PANEL = "PANEL";
        public static final List<String> ALL = List.of(MERCHANT_API, CHECKOUT_FORM, PANEL);

        private OrderSurface() {
        }
    }

    /**
     * Status of a lead: what Lunixi knows about sending it to the insurance provider. {@code PENDING}: the provider
     * call is still running. {@code SUBMITTED}: the provider received it ({@code insurance.lead.submitted}).
     * {@code UNKNOWN}: the outcome is not known; it is never sent again (open a new lead with a new key if needed).
     * {@code FAILED}: refused, or it never reached the provider ({@code errorCode}).
     */
    public static final class LeadStatus {
        public static final String PENDING = "PENDING";
        public static final String SUBMITTED = "SUBMITTED";
        public static final String UNKNOWN = "UNKNOWN";
        public static final String FAILED = "FAILED";
        public static final List<String> ALL = List.of(PENDING, SUBMITTED, UNKNOWN, FAILED);

        private LeadStatus() {
        }
    }

    /**
     * Lunixi reference code lists ({@link InsuranceClient#listReferenceValues}). {@code DISTRICT},
     * {@code DEVICE_MODEL}, {@code VEHICLE_MODEL}, {@code BANK_BRANCH} and {@code PET_BREED} need {@code parentCode}
     * (province, brand, bank, {@code CAT} | {@code DOG}); {@code COUNTRY}, {@code CITY} and {@code BANK} take none.
     */
    public static final class ReferenceKind {
        public static final String COUNTRY = "COUNTRY";
        public static final String CITY = "CITY";
        public static final String DISTRICT = "DISTRICT";
        public static final String DEVICE_BRAND = "DEVICE_BRAND";
        public static final String DEVICE_MODEL = "DEVICE_MODEL";
        public static final String VEHICLE_BRAND = "VEHICLE_BRAND";
        public static final String VEHICLE_MODEL = "VEHICLE_MODEL";
        public static final String VEHICLE_TYPE = "VEHICLE_TYPE";
        public static final String PRODUCT_CATEGORY = "PRODUCT_CATEGORY";
        public static final String VEHICLE_USAGE = "VEHICLE_USAGE";
        public static final String LIABILITY_LIMIT = "LIABILITY_LIMIT";
        public static final String OCCUPATION = "OCCUPATION";
        public static final String PET_BREED = "PET_BREED";
        public static final String DWELLING_TYPE = "DWELLING_TYPE";
        public static final String DWELLING_FLOOR = "DWELLING_FLOOR";
        public static final String BANK = "BANK";
        public static final String BANK_BRANCH = "BANK_BRANCH";
        public static final String FINANCE_INSTITUTION = "FINANCE_INSTITUTION";
        public static final String MEDICAL_PROCEDURE_COVER = "MEDICAL_PROCEDURE_COVER";
        public static final List<String> ALL = List.of(COUNTRY, CITY, DISTRICT, DEVICE_BRAND, DEVICE_MODEL,
                VEHICLE_BRAND, VEHICLE_MODEL, VEHICLE_TYPE, PRODUCT_CATEGORY, VEHICLE_USAGE, LIABILITY_LIMIT,
                OCCUPATION, PET_BREED, DWELLING_TYPE, DWELLING_FLOOR, BANK, BANK_BRANCH, FINANCE_INSTITUTION,
                MEDICAL_PROCEDURE_COVER);

        private ReferenceKind() {
        }
    }
}
