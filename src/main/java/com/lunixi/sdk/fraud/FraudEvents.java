package com.lunixi.sdk.fraud;

import java.util.List;

/**
 * The fraud webhook event types an endpoint of yours can subscribe to.
 *
 * <p>These arrive in the same signed envelope as every other platform webhook
 * and are verified with {@link com.lunixi.sdk.webhook.WebhookVerifier}. There
 * is no separate fraud webhook channel: every customer-facing fraud event goes
 * through the platform one, so one verified endpoint is enough.
 */
public final class FraudEvents {

    /**
     * An asynchronous flow result is ready; correlate it with the decision
     * request id. Advisory — it does not change the decision you already
     * received.
     */
    public static final String FLOW_ASYNC_COMPLETED = "fraud.flow.async_completed";

    /**
     * A platform-event node in one of your flows fired. The label and data are
     * the values you configured in that flow.
     */
    public static final String FLOW_ACTION_TRIGGERED = "fraud.flow.action_triggered";

    /**
     * A quota threshold was reached, or a request was rejected for being over
     * quota. Sent at most once per day and lane.
     */
    public static final String QUOTA_THRESHOLD_REACHED = "fraud.quota.threshold_reached";

    /** One of your alert rules fired. */
    public static final String ALERT_TRIGGERED = "fraud.alert.triggered";

    /** An interruption affecting evaluation was detected or declared. */
    public static final String SERVICE_DEGRADED = "fraud.service.degraded";

    /**
     * The interruption ended. Carries how long it lasted and how many decisions
     * could not be evaluated in the meantime.
     */
    public static final String SERVICE_RECOVERED = "fraud.service.recovered";

    /**
     * A scheduled report you configured finished its period and was produced.
     *
     * <p>The event carries no download link: links are short-lived and are
     * produced in the console. A report you request by hand does not send this
     * event.
     */
    public static final String REPORT_READY = "fraud.report.ready";

    private static final List<String> ALL = List.of(
            FLOW_ASYNC_COMPLETED,
            FLOW_ACTION_TRIGGERED,
            QUOTA_THRESHOLD_REACHED,
            ALERT_TRIGGERED,
            SERVICE_DEGRADED,
            SERVICE_RECOVERED,
            REPORT_READY);

    private FraudEvents() {
    }

    /** @return every fraud event type, in catalogue order */
    public static List<String> all() {
        return ALL;
    }
}
