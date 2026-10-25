package com.lunixi.sdk.fraud;

import java.util.List;
import java.util.regex.Pattern;

/**
 * The closed dictionaries a decision request may declare.
 *
 * <p>Each list mirrors the gateway's own dictionary. A value outside it is
 * answered with {@code 400}: the request validator rejects unknown values
 * rather than passing them through, so an invented value never reaches the
 * engine and never matches a rule.
 *
 * <p>Two of the dictionaries are deliberately <i>narrower</i> than the engine's
 * full set, and that narrowing is a security boundary rather than an omission.
 * {@link Channel} and {@link ProductContext} leave out the values that identify
 * traffic as coming from the platform's own services. If an external caller
 * could declare one of those, it could reach flows, rules and quota lanes that
 * are bound to internal traffic — including lanes that are not billed. Those
 * values are therefore rejected from outside, and this SDK does not offer them.
 */
public final class FraudTaxonomy {

    private FraudTaxonomy() {
    }

    /**
     * The channel a request came through.
     *
     * <p>Only these two may be declared from outside. The platform's own
     * channels are not declarable.
     */
    public enum Channel {
        /** Your own form or app, with the Lunixi client SDK collecting signals. */
        MERCHANT_FORM("merchant_form"),
        /** Server-to-server: you ask for a decision and apply it in your own flow. */
        STANDALONE_API("standalone_api");

        private final String wireValue;

        Channel(String wireValue) {
            this.wireValue = wireValue;
        }

        /** @return the value sent on the wire */
        public String wireValue() {
            return wireValue;
        }
    }

    /** The decision surface inside a channel. */
    public enum Surface {
        FORM_GATE("form_gate"),
        TRANSACTION("transaction"),
        WITHDRAWAL_GATE("withdrawal_gate"),
        SETTLEMENT("settlement");

        private final String wireValue;

        Surface(String wireValue) {
            this.wireValue = wireValue;
        }

        public String wireValue() {
            return wireValue;
        }
    }

    /** The moment in the transaction's life at which the decision is asked for. */
    public enum Stage {
        PRE_AUTHORIZATION("pre_authorization"),
        POST_AUTHENTICATION("post_authentication"),
        POST_HOC("post_hoc"),
        ASYNC_EVENT("async_event"),
        SCHEDULED("scheduled");

        private final String wireValue;

        Stage(String wireValue) {
            this.wireValue = wireValue;
        }

        public String wireValue() {
            return wireValue;
        }
    }

    /** The kind of business operation being evaluated. */
    public enum OperationKind {
        CARD_SALE("card_sale"),
        CARD_PREAUTH("card_preauth"),
        W2W_TRANSFER("w2w_transfer"),
        W2IBAN_WITHDRAWAL("w2iban_withdrawal"),
        BULK_PAYOUT("bulk_payout"),
        AGENT_CASH_OUT("agent_cash_out"),
        BILL_PAYMENT("bill_payment"),
        MERCHANT_PAYMENT("merchant_payment"),
        QR_PAYMENT("qr_payment"),
        CARD_TOPUP("card_topup"),
        AGENT_TOPUP("agent_topup"),
        BANK_TRANSFER_TOPUP("bank_transfer_topup"),
        OUTBOUND_TRANSFER("outbound_transfer"),
        INBOUND_TRANSFER("inbound_transfer");

        private final String wireValue;

        OperationKind(String wireValue) {
            this.wireValue = wireValue;
        }

        public String wireValue() {
            return wireValue;
        }
    }

    /**
     * Cardholder authentication mode. The mode you asked for and the mode that
     * was actually applied travel in separate fields, because a decision may
     * change one into the other.
     */
    public enum AuthMode {
        TWO_D("2d"),
        THREE_D("3d");

        private final String wireValue;

        AuthMode(String wireValue) {
            this.wireValue = wireValue;
        }

        public String wireValue() {
            return wireValue;
        }
    }

    /**
     * The product context a request may declare.
     *
     * <p>The contexts reserved for the platform's own senders are absent: they
     * belong to an unbilled lane and declaring one from outside is rejected.
     */
    public enum ProductContext {
        LIVE_SESSION("live_session"),
        PAYMENT_FORM("payment_form"),
        HOSTED_PAYMENT_FORM("hosted_payment_form"),
        DIRECT_API("direct_api");

        private final String wireValue;

        ProductContext(String wireValue) {
            this.wireValue = wireValue;
        }

        public String wireValue() {
            return wireValue;
        }
    }

    /**
     * The only integration mode an external caller may declare. Anything else
     * is answered with {@code 400}.
     */
    public static final String INTEGRATION_MODE_STANDALONE = "standalone";

    /** Maximum length of {@code transactionRef}. */
    public static final int TRANSACTION_REF_MAX_LENGTH = 180;

    /** Maximum length of {@code subjectId}. */
    public static final int SUBJECT_ID_MAX_LENGTH = 180;

    /** {@code subMerchantRef} accepts letters, digits and {@code . _ : @ -}. */
    public static final Pattern SUB_MERCHANT_REF_PATTERN = Pattern.compile("^[A-Za-z0-9._:@-]{1,180}$");

    /** @return every declarable channel's wire value */
    public static List<String> channelValues() {
        return wireValues(Channel.values(), Channel::wireValue);
    }

    /** @return every surface's wire value */
    public static List<String> surfaceValues() {
        return wireValues(Surface.values(), Surface::wireValue);
    }

    /** @return every stage's wire value */
    public static List<String> stageValues() {
        return wireValues(Stage.values(), Stage::wireValue);
    }

    /** @return every operation kind's wire value */
    public static List<String> operationKindValues() {
        return wireValues(OperationKind.values(), OperationKind::wireValue);
    }

    /** @return every authentication mode's wire value */
    public static List<String> authModeValues() {
        return wireValues(AuthMode.values(), AuthMode::wireValue);
    }

    /** @return every declarable product context's wire value */
    public static List<String> productContextValues() {
        return wireValues(ProductContext.values(), ProductContext::wireValue);
    }

    private static <E> List<String> wireValues(E[] values, java.util.function.Function<E, String> reader) {
        return java.util.Arrays.stream(values).map(reader).collect(java.util.stream.Collectors.toUnmodifiableList());
    }
}
