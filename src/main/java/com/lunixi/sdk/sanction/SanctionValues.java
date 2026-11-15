package com.lunixi.sdk.sanction;

import java.util.List;

/** Sanction screening wire values ({@code /api/v1/sanction}). The same sets as the Node and PHP SDKs. */
public final class SanctionValues {

    private SanctionValues() {
    }

    /** Risk domains a screening can be narrowed to ({@code riskDomains}). Omitted: every domain. */
    public static final class RiskDomain {
        public static final String SANCTIONS = "sanctions";
        public static final String PEP = "pep";
        public static final String RCA = "rca";
        public static final String WANTED = "wanted";
        public static final String REGULATORY = "regulatory";
        public static final String DEBARMENT = "debarment";
        public static final String OWNERSHIP = "ownership";
        public static final String VESSEL = "vessel";
        public static final String SECURITIES = "securities";
        public static final List<String> ALL = List.of(SANCTIONS, PEP, RCA, WANTED, REGULATORY, DEBARMENT,
                OWNERSHIP, VESSEL, SECURITIES);

        private RiskDomain() {
        }
    }

    /** Name-screening presets ({@code mode}). Sent with {@code riskDomains}, their intersection is screened. */
    public static final class ScreenMode {
        public static final String SANCTIONS_ONLY = "sanctions_only";
        public static final String SANCTIONS_PEP = "sanctions_pep";
        public static final String FULL_RISK = "full_risk";
        public static final List<String> ALL = List.of(SANCTIONS_ONLY, SANCTIONS_PEP, FULL_RISK);

        private ScreenMode() {
        }
    }

    /** Wallet-screening depth; the wider modes add exposure and crypto-intelligence hits. */
    public static final class WalletScreenMode {
        public static final String WALLET_SANCTIONS = "wallet_sanctions";
        public static final String WALLET_RISK = "wallet_risk";
        public static final String WALLET_FULL_AML = "wallet_full_aml";
        public static final List<String> ALL = List.of(WALLET_SANCTIONS, WALLET_RISK, WALLET_FULL_AML);

        private WalletScreenMode() {
        }
    }

    /** {@code subjectType}. */
    public static final class SubjectType {
        public static final String PERSON = "person";
        public static final String ORGANIZATION = "organization";
        public static final List<String> ALL = List.of(PERSON, ORGANIZATION);

        private SubjectType() {
        }
    }

    /**
     * How much of the requested scope a screening covered. {@code COMPLETE}: every requested risk domain was
     * screened. {@code PARTIAL}: some requested domain has no list ({@code uncoveredRiskDomains},
     * {@code uncoveredSources}); no match is clear only for what was screened. {@code DEGRADED}: the candidate search
     * was limited; run the screening again.
     */
    public static final class CoverageStatus {
        public static final String COMPLETE = "COMPLETE";
        public static final String PARTIAL = "PARTIAL";
        public static final String DEGRADED = "DEGRADED";
        public static final List<String> ALL = List.of(COMPLETE, PARTIAL, DEGRADED);

        private CoverageStatus() {
        }
    }

    /** {@code hitType} of a match. */
    public static final class HitType {
        public static final String EXACT = "exact";
        public static final String PROBABLE = "probable";
        public static final String POSSIBLE = "possible";
        public static final List<String> ALL = List.of(EXACT, PROBABLE, POSSIBLE);

        private HitType() {
        }
    }
}
