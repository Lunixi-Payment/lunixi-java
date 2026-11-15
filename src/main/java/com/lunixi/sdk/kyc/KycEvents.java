package com.lunixi.sdk.kyc;

import java.util.List;

/**
 * KYC webhook event types (the same signed envelope as payment events). {@code data} always carries
 * {@code sessionId} and {@code externalCustomerId}; terminal events carry the decision. A rescreen or screening hit on
 * an approved session must revoke access.
 */
public final class KycEvents {

    public static final String SESSION_CREATED = "kyc.session.created";
    public static final String SESSION_INITIATED = "kyc.session.initiated";
    public static final String SESSION_EXPIRED = "kyc.session.expired";
    public static final String SESSION_ABANDONED = "kyc.session.abandoned";
    public static final String SESSION_CANCELLED = "kyc.session.cancelled";
    public static final String DOCUMENT_RECEIVED = "kyc.document.received";
    public static final String DOCUMENT_REJECTED = "kyc.document.rejected";
    public static final String LIVENESS_COMPLETED = "kyc.liveness.completed";
    public static final String PROCESSING_STARTED = "kyc.processing.started";
    public static final String MANUAL_REVIEW_REQUIRED = "kyc.manual_review.required";
    public static final String APPROVED = "kyc.approved";
    public static final String REJECTED = "kyc.rejected";
    public static final String RESCREEN_REQUIRED = "kyc.rescreen.required";
    public static final String KYB_SESSION_CREATED = "kyb.session.created";
    public static final String KYB_COMPANY_VERIFIED = "kyb.company.verified";
    public static final String KYB_COMPANY_NOT_FOUND = "kyb.company.not_found";
    public static final String KYB_UBO_PENDING = "kyb.ubo.pending";
    public static final String KYB_RELATED_KYC_REQUIRED = "kyb.related_kyc.required";
    public static final String KYB_RELATED_KYC_COMPLETED = "kyb.related_kyc.completed";
    public static final String KYB_MANUAL_REVIEW_REQUIRED = "kyb.manual_review.required";
    public static final String KYB_APPROVED = "kyb.approved";
    public static final String KYB_REJECTED = "kyb.rejected";
    public static final String SCREENING_MATCH_DETECTED = "screening.match.detected";
    public static final String SCREENING_FALSE_POSITIVE_CLEARED = "screening.false_positive.cleared";
    public static final String MONITORING_ALERT_CREATED = "ongoing_monitoring.alert.created";

    /** Every event type, in catalog order. */
    public static final List<String> ALL = List.of(SESSION_CREATED, SESSION_INITIATED, SESSION_EXPIRED,
            SESSION_ABANDONED, SESSION_CANCELLED, DOCUMENT_RECEIVED, DOCUMENT_REJECTED, LIVENESS_COMPLETED,
            PROCESSING_STARTED, MANUAL_REVIEW_REQUIRED, APPROVED, REJECTED, RESCREEN_REQUIRED, KYB_SESSION_CREATED,
            KYB_COMPANY_VERIFIED, KYB_COMPANY_NOT_FOUND, KYB_UBO_PENDING, KYB_RELATED_KYC_REQUIRED,
            KYB_RELATED_KYC_COMPLETED, KYB_MANUAL_REVIEW_REQUIRED, KYB_APPROVED, KYB_REJECTED,
            SCREENING_MATCH_DETECTED, SCREENING_FALSE_POSITIVE_CLEARED, MONITORING_ALERT_CREATED);

    private KycEvents() {
    }
}
