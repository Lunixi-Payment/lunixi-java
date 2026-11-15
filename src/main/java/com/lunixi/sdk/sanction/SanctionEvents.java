package com.lunixi.sdk.sanction;

import java.util.List;

/**
 * Sanction screening webhook event types, delivered in the same signed envelope as every Lunixi webhook. They also
 * fire from the KYC product's AML screening, so reconcile them by the carried customer or session reference.
 */
public final class SanctionEvents {

    /** A screening found a match. */
    public static final String MATCH_DETECTED = "screening.match.detected";
    /** A match was cleared as a false positive. */
    public static final String FALSE_POSITIVE_CLEARED = "screening.false_positive.cleared";
    /** Ongoing monitoring raised an alert. */
    public static final String MONITORING_ALERT_CREATED = "ongoing_monitoring.alert.created";
    /** Ongoing monitoring found nothing new. */
    public static final String MONITORING_CLEAR = "ongoing_monitoring.clear";
    /** Every event type known to this release. */
    public static final List<String> ALL = List.of(MATCH_DETECTED, FALSE_POSITIVE_CLEARED, MONITORING_ALERT_CREATED,
            MONITORING_CLEAR);

    private SanctionEvents() {
    }
}
