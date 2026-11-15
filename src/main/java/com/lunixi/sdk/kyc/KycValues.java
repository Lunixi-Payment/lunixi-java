package com.lunixi.sdk.kyc;

import java.util.List;
import java.util.Locale;

/**
 * KYC wire values: the same codes as the Node SDK's and the PHP SDK's KYC constants. Each nested class lists its
 * values in {@code ALL}, in catalog order.
 */
public final class KycValues {

    /** Longest session lifetime {@code ttlMinutes} accepts (one day). */
    public static final int SESSION_TTL_MAX_MINUTES = 1440;

    private KycValues() {
    }

    /** Session types. {@code EVIDENCE} is proof-of-X (address, funds, card ownership…), not only onboarding. */
    public static final class SessionType {
        /** A person's identity. */
        public static final String KYC = "KYC";
        /** A company and its beneficial owners. */
        public static final String KYB = "KYB";
        public static final String EVIDENCE = "EVIDENCE";
        public static final List<String> ALL = List.of(KYC, KYB, EVIDENCE);

        private SessionType() {
        }
    }

    /** Session status. */
    public static final class SessionStatus {
        public static final String CREATED = "CREATED";
        public static final String INITIATED = "INITIATED";
        public static final String DOCUMENT_SUBMITTED = "DOCUMENT_SUBMITTED";
        public static final String PROCESSING = "PROCESSING";
        public static final String AWAITING_PROVIDER_CALLBACK = "AWAITING_PROVIDER_CALLBACK";
        public static final String AWAITING_ADDITIONAL_DOCUMENT = "AWAITING_ADDITIONAL_DOCUMENT";
        public static final String AWAITING_USER_ACTION = "AWAITING_USER_ACTION";
        public static final String UNDER_REVIEW = "UNDER_REVIEW";
        public static final String PENDING_ADDITIONAL_INFO = "PENDING_ADDITIONAL_INFO";
        public static final String RESCREEN_REQUIRED = "RESCREEN_REQUIRED";
        public static final String REOPENED = "REOPENED";
        public static final String APPROVED = "APPROVED";
        public static final String REJECTED = "REJECTED";
        public static final String MANUAL_REVIEW = "MANUAL_REVIEW";
        public static final String EXPIRED = "EXPIRED";
        public static final String ABANDONED = "ABANDONED";
        public static final String CANCELLED = "CANCELLED";
        public static final List<String> ALL = List.of(CREATED, INITIATED, DOCUMENT_SUBMITTED, PROCESSING,
                AWAITING_PROVIDER_CALLBACK, AWAITING_ADDITIONAL_DOCUMENT, AWAITING_USER_ACTION, UNDER_REVIEW,
                PENDING_ADDITIONAL_INFO, RESCREEN_REQUIRED, REOPENED, APPROVED, REJECTED, MANUAL_REVIEW, EXPIRED,
                ABANDONED, CANCELLED);
        /** Terminal states. */
        public static final List<String> TERMINAL_STATES = List.of(APPROVED, REJECTED, MANUAL_REVIEW, EXPIRED,
                ABANDONED, CANCELLED);

        private SessionStatus() {
        }
    }

    /** {@code evidencePurpose} of an {@code EVIDENCE} session. */
    public static final class EvidencePurpose {
        public static final String IDENTITY_VERIFICATION = "IDENTITY_VERIFICATION";
        public static final String BUSINESS_VERIFICATION = "BUSINESS_VERIFICATION";
        public static final String PROOF_OF_FUNDS = "PROOF_OF_FUNDS";
        public static final String PROOF_OF_ADDRESS = "PROOF_OF_ADDRESS";
        public static final String SOURCE_OF_FUNDS = "SOURCE_OF_FUNDS";
        public static final String SOURCE_OF_WEALTH = "SOURCE_OF_WEALTH";
        public static final String TRANSACTION_EVIDENCE = "TRANSACTION_EVIDENCE";
        public static final String CARD_OWNERSHIP = "CARD_OWNERSHIP";
        public static final String PAYMENT_METHOD_OWNERSHIP = "PAYMENT_METHOD_OWNERSHIP";
        public static final String PAYOUT_EVIDENCE = "PAYOUT_EVIDENCE";
        public static final String MERCHANT_ONBOARDING = "MERCHANT_ONBOARDING";
        public static final List<String> ALL = List.of(IDENTITY_VERIFICATION, BUSINESS_VERIFICATION, PROOF_OF_FUNDS,
                PROOF_OF_ADDRESS, SOURCE_OF_FUNDS, SOURCE_OF_WEALTH, TRANSACTION_EVIDENCE, CARD_OWNERSHIP,
                PAYMENT_METHOD_OWNERSHIP, PAYOUT_EVIDENCE, MERCHANT_ONBOARDING);

        private EvidencePurpose() {
        }
    }

    /** Assurance ladder (rank-aligned with NIST 800-63 IAL / eIDAS LoA). A gate passes when achieved >= required. */
    public static final class AssuranceLevel {
        public static final String NONE = "NONE";
        public static final String NOT_PERFORMED = "NOT_PERFORMED";
        public static final String LOW = "LOW";
        public static final String SUBSTANTIAL = "SUBSTANTIAL";
        public static final String HIGH = "HIGH";
        public static final List<String> ALL = List.of(NONE, NOT_PERFORMED, LOW, SUBSTANTIAL, HIGH);

        private AssuranceLevel() {
        }
    }

    /** Domains of the achieved assurance snapshot ({@code session.decision.assuranceSnapshot.achieved}). */
    public static final class AssuranceDomain {
        public static final String IDENTITY = "identity_evidence_strength";
        public static final String DOCUMENT = "document_authenticity_level";
        public static final String BIOMETRIC = "possession_biometric_binding";
        public static final String AUTHORITATIVE = "authoritative_source_level";
        public static final String ADDRESS = "address_assurance";
        public static final String AML = "aml_freshness";
        public static final String HUMAN_REVIEW = "human_review_level";
        public static final List<String> ALL = List.of(IDENTITY, DOCUMENT, BIOMETRIC, AUTHORITATIVE, ADDRESS, AML,
                HUMAN_REVIEW);

        private AssuranceDomain() {
        }
    }

    /**
     * @param status a session status (any case)
     * @return {@code true} for {@code APPROVED}, {@code REJECTED}, {@code MANUAL_REVIEW}, {@code EXPIRED},
     *         {@code ABANDONED}, {@code CANCELLED}
     */
    public static boolean isSessionTerminal(String status) {
        return status != null && SessionStatus.TERMINAL_STATES.contains(status.toUpperCase(Locale.ROOT));
    }

    /**
     * @param level an assurance level (any case)
     * @return its rank: {@code HIGH} 3, {@code SUBSTANTIAL} 2, {@code LOW} 1, anything else 0
     */
    public static int assuranceRank(String level) {
        String upper = level == null ? "" : level.toUpperCase(Locale.ROOT);
        switch (upper) {
            case AssuranceLevel.HIGH:
                return 3;
            case AssuranceLevel.SUBSTANTIAL:
                return 2;
            case AssuranceLevel.LOW:
                return 1;
            default:
                return 0;
        }
    }

    /**
     * @param achieved the achieved level
     * @param required the required level
     * @return {@code true} when {@code achieved} meets or exceeds {@code required}
     */
    public static boolean assuranceMeets(String achieved, String required) {
        return assuranceRank(achieved) >= assuranceRank(required);
    }
}
