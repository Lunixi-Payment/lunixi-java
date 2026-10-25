package com.lunixi.sdk.fraud;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.lunixi.sdk.exception.ConfigurationException;

/**
 * The body of a decision request.
 *
 * <p>Every field is optional: send as much context as you hold, because each
 * signal is something a rule can read. The sub-objects ({@code payment},
 * {@code customer}, {@code device}, …) are passed as maps on purpose — the
 * engine's signal set grows, and a map lets you send a newly documented field
 * without waiting for an SDK release.
 *
 * <pre>
 * FraudDecision decision = client.fraud().evaluate(EvaluateRequest.create()
 *         .channel(FraudTaxonomy.Channel.STANDALONE_API)
 *         .productContext(FraudTaxonomy.ProductContext.DIRECT_API)
 *         .transactionRef("order-2026-0001")
 *         .payment(Map.of("amount", 25000, "currency", "TRY"))
 *         .customer(Map.of("email", "…", "country", "TR"))
 *         .device(Map.of("ipAddress", "…")));
 * </pre>
 *
 * <h2>Two fields are deliberately absent</h2>
 * <ul>
 *   <li><b>The tenant.</b> It is stamped from the credential that authenticated
 *       the call. The endpoint accepts the field for backward compatibility but
 *       ignores its value, so offering it here would suggest a caller can
 *       choose whose data a decision is made against.</li>
 *   <li><b>Engine-internal fields.</b> Flow overrides and evaluation switches
 *       are not part of the documented surface; they are configured in the
 *       console, not per request.</li>
 * </ul>
 */
public final class EvaluateRequest {

    /**
     * The fields the endpoint accepts.
     *
     * <p>Unknown keys are rejected by the gateway with {@code 400}. Checking
     * them here as well means the error names the offending key locally, which
     * is the difference between a readable message and a round trip spent on a
     * typo.
     */
    private static final Set<String> ALLOWED_FIELDS = Set.of(
            "integrationMode",
            "productContext",
            "transactionRef",
            "channel",
            "surface",
            "stage",
            "operationKind",
            "authModeRequested",
            "authModeApplied",
            "subMerchantRef",
            "subjectType",
            "subjectId",
            "paymentRail",
            "transferDirection",
            "merchantGroupIds",
            "paymentId",
            "payment",
            "bankTransfer",
            "cryptoTransfer",
            "walletOperation",
            "billPayment",
            "loanApplication",
            "payout",
            "customer",
            "device",
            "session",
            "merchant",
            "mobileApp",
            "telemetry",
            "metadata",
            "identitySnapshot",
            "identityAssertion");

    private final Map<String, Object> body = new LinkedHashMap<>();

    private EvaluateRequest() {
    }

    /** @return an empty request */
    public static EvaluateRequest create() {
        return new EvaluateRequest();
    }

    /**
     * Builds a request from a map of already-assembled fields, validating it the
     * same way the typed setters do.
     *
     * @param fields the request fields
     * @return the request
     * @throws ConfigurationException when a field is unknown or out of bounds
     */
    public static EvaluateRequest of(Map<String, Object> fields) {
        EvaluateRequest request = new EvaluateRequest();
        if (fields != null) {
            fields.forEach(request::set);
        }
        return request;
    }

    /**
     * Sets a field by name — the escape hatch for a field documented after this
     * SDK release.
     *
     * @param field the field name, which must be one the endpoint accepts
     * @param value the value; {@code null} removes the field
     * @return this request
     * @throws ConfigurationException when the field is not part of the surface
     */
    public EvaluateRequest set(String field, Object value) {
        if (!ALLOWED_FIELDS.contains(field)) {
            List<String> allowed = new ArrayList<>(ALLOWED_FIELDS);
            allowed.sort(String::compareTo);
            throw new ConfigurationException(
                    "Bilinmeyen alan / unknown field '" + field + "'. Kabul edilen alanlar / accepted fields: "
                            + String.join(", ", allowed) + ".");
        }
        if (value == null) {
            body.remove(field);
            return this;
        }
        if ("transactionRef".equals(field)) {
            assertBounded(String.valueOf(value), field, FraudTaxonomy.TRANSACTION_REF_MAX_LENGTH);
        } else if ("subjectId".equals(field)) {
            assertBounded(String.valueOf(value), field, FraudTaxonomy.SUBJECT_ID_MAX_LENGTH);
        } else if ("subMerchantRef".equals(field)) {
            assertSubMerchantRef(String.valueOf(value));
        }
        body.put(field, value);
        return this;
    }

    /**
     * Declares the integration mode. The only accepted value is
     * {@code standalone}, so this is a convenience over
     * {@code set("integrationMode", …)} rather than a choice.
     *
     * @return this request
     */
    public EvaluateRequest standaloneIntegrationMode() {
        return set("integrationMode", FraudTaxonomy.INTEGRATION_MODE_STANDALONE);
    }

    /**
     * @param productContext where in your product the decision is being asked for
     * @return this request
     */
    public EvaluateRequest productContext(FraudTaxonomy.ProductContext productContext) {
        return set("productContext", productContext.wireValue());
    }

    /**
     * Your own reference for the transaction — an order number, a UUID, anything
     * stable.
     *
     * <p>It is also the unit of reconciliation: the same reference asked about
     * more than once inside the same account is recognised as one transaction
     * rather than several, so reusing it for unrelated transactions merges
     * things that should stay apart.
     *
     * @param transactionRef the reference, at most 180 characters
     * @return this request
     */
    public EvaluateRequest transactionRef(String transactionRef) {
        return set("transactionRef", transactionRef);
    }

    /**
     * @param channel the channel the request comes through
     * @return this request
     */
    public EvaluateRequest channel(FraudTaxonomy.Channel channel) {
        return set("channel", channel.wireValue());
    }

    /**
     * @param surface the decision surface inside the channel
     * @return this request
     */
    public EvaluateRequest surface(FraudTaxonomy.Surface surface) {
        return set("surface", surface.wireValue());
    }

    /**
     * @param stage the moment in the transaction's life
     * @return this request
     */
    public EvaluateRequest stage(FraudTaxonomy.Stage stage) {
        return set("stage", stage.wireValue());
    }

    /**
     * @param operationKind the kind of business operation
     * @return this request
     */
    public EvaluateRequest operationKind(FraudTaxonomy.OperationKind operationKind) {
        return set("operationKind", operationKind.wireValue());
    }

    /**
     * @param authMode the authentication mode you asked the issuer for
     * @return this request
     */
    public EvaluateRequest authModeRequested(FraudTaxonomy.AuthMode authMode) {
        return set("authModeRequested", authMode.wireValue());
    }

    /**
     * @param authMode the authentication mode that was actually applied
     * @return this request
     */
    public EvaluateRequest authModeApplied(FraudTaxonomy.AuthMode authMode) {
        return set("authModeApplied", authMode.wireValue());
    }

    /**
     * The reference of a sub-merchant under your account, for a payment service
     * provider evaluating its own merchants' traffic.
     *
     * @param subMerchantRef letters, digits and {@code . _ : @ -}, at most 180
     *                       characters
     * @return this request
     */
    public EvaluateRequest subMerchantRef(String subMerchantRef) {
        return set("subMerchantRef", subMerchantRef);
    }

    /**
     * @param subjectType what is being evaluated, for example {@code card_payment}
     * @return this request
     */
    public EvaluateRequest subjectType(String subjectType) {
        return set("subjectType", subjectType);
    }

    /**
     * The identifier of the subject being evaluated — an order, customer or
     * session number.
     *
     * <p>When you collect signals with the client SDK on your own form, this
     * must be the same value you gave the SDK when the session started. If the
     * two differ, the decision is recorded with a subject-mismatch reason: the
     * signals cannot be attributed to the transaction, so they are not silently
     * credited to it.
     *
     * @param subjectId the identifier, at most 180 characters
     * @return this request
     */
    public EvaluateRequest subjectId(String subjectId) {
        return set("subjectId", subjectId);
    }

    /**
     * @param paymentRail the rail, for example {@code card}
     * @return this request
     */
    public EvaluateRequest paymentRail(String paymentRail) {
        return set("paymentRail", paymentRail);
    }

    /**
     * @param transferDirection {@code inbound} or {@code outbound}
     * @return this request
     */
    public EvaluateRequest transferDirection(String transferDirection) {
        return set("transferDirection", transferDirection);
    }

    /**
     * @param paymentId the payment identifier, when the transaction already has one
     * @return this request
     */
    public EvaluateRequest paymentId(String paymentId) {
        return set("paymentId", paymentId);
    }

    /**
     * @param payment the payment signals (amount in minor units, currency, …)
     * @return this request
     */
    public EvaluateRequest payment(Map<String, Object> payment) {
        return set("payment", payment);
    }

    /**
     * @param customer the customer signals
     * @return this request
     */
    public EvaluateRequest customer(Map<String, Object> customer) {
        return set("customer", customer);
    }

    /**
     * @param device the device signals
     * @return this request
     */
    public EvaluateRequest device(Map<String, Object> device) {
        return set("device", device);
    }

    /**
     * @param session the session signals
     * @return this request
     */
    public EvaluateRequest session(Map<String, Object> session) {
        return set("session", session);
    }

    /**
     * The merchant context — sector code, business model, country, onboarding
     * dates. A payment service provider sends the sub-merchant's values here.
     *
     * @param merchant the merchant signals
     * @return this request
     */
    public EvaluateRequest merchant(Map<String, Object> merchant) {
        return set("merchant", merchant);
    }

    /**
     * @param telemetry the behavioural telemetry collected on your form
     * @return this request
     */
    public EvaluateRequest telemetry(Map<String, Object> telemetry) {
        return set("telemetry", telemetry);
    }

    /**
     * @param metadata your own key/value pairs, carried through to the decision record
     * @return this request
     */
    public EvaluateRequest metadata(Map<String, Object> metadata) {
        return set("metadata", metadata);
    }

    /**
     * @param assertion the signed assertion produced by the client identity SDK
     * @return this request
     */
    public EvaluateRequest identityAssertion(String assertion) {
        return set("identityAssertion", assertion);
    }

    /**
     * @return the request body, ready to send
     */
    public Map<String, Object> toMap() {
        return new LinkedHashMap<>(body);
    }

    private static void assertBounded(String value, String field, int maxLength) {
        if (value.isBlank()) {
            throw new ConfigurationException(
                    "'" + field + "' boş olamaz / '" + field + "' must not be blank.");
        }
        if (value.length() > maxLength) {
            throw new ConfigurationException(
                    "'" + field + "' en fazla " + maxLength + " karakter olabilir (" + value.length()
                            + " verildi) / '" + field + "' must be at most " + maxLength + " characters (got "
                            + value.length() + ").");
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 0x20 || c == 0x7f) {
                throw new ConfigurationException(
                        "'" + field + "' kontrol karakteri içeremez / '" + field
                                + "' must not contain control characters.");
            }
        }
    }

    private static void assertSubMerchantRef(String value) {
        if (!FraudTaxonomy.SUB_MERCHANT_REF_PATTERN.matcher(value).matches()) {
            throw new ConfigurationException(
                    "'subMerchantRef' yalnız harf, rakam ve . _ : @ - içerebilir (1-180 karakter) "
                            + "/ 'subMerchantRef' accepts letters, digits and . _ : @ - (1-180 characters).");
        }
    }
}
