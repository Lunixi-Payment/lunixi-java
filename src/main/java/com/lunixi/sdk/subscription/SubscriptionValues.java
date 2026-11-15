package com.lunixi.sdk.subscription;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Subscription wire values: the same codes as the Node SDK's and the PHP SDK's subscription constants. Each nested
 * class lists its values in {@code ALL}, in catalog order.
 */
public final class SubscriptionValues {

    /** Hosted customer portal; a portal link's {@code token} opens it ({@link #portalUrl(Object)}). */
    public static final String PORTAL_HOSTED_BASE = "https://portal.lunixi.com";

    private SubscriptionValues() {
    }

    /** Subscription status. */
    public static final class Status {
        /** {@code startsAt} is in the future. */
        public static final String PENDING = "PENDING";
        public static final String TRIALING = "TRIALING";
        public static final String ACTIVE = "ACTIVE";
        /** A charge failed; dunning runs. */
        public static final String PAST_DUE = "PAST_DUE";
        /** Dunning exhausted; grace period. */
        public static final String UNPAID = "UNPAID";
        /** Paused by the merchant; no charges. */
        public static final String PAUSED = "PAUSED";
        public static final String UPGRADED = "UPGRADED";
        public static final String DOWNGRADED = "DOWNGRADED";
        public static final String CANCELED = "CANCELED";
        /** The recurrence ran out. */
        public static final String EXPIRED = "EXPIRED";
        public static final List<String> ALL = List.of(PENDING, TRIALING, ACTIVE, PAST_DUE, UNPAID, PAUSED,
                UPGRADED, DOWNGRADED, CANCELED, EXPIRED);
        /** Live states: the subscription may be billed. */
        public static final List<String> ACTIVE_STATES = List.of(TRIALING, ACTIVE, PAST_DUE);
        /** Terminal states: no further transition. */
        public static final List<String> TERMINAL_STATES = List.of(UPGRADED, DOWNGRADED, CANCELED, EXPIRED);

        private Status() {
        }
    }

    /** Invoice status: {@code DRAFT → OPEN → PAID | UNCOLLECTIBLE | VOID}. */
    public static final class InvoiceStatus {
        public static final String DRAFT = "DRAFT";
        public static final String OPEN = "OPEN";
        public static final String PAID = "PAID";
        public static final String UNCOLLECTIBLE = "UNCOLLECTIBLE";
        public static final String VOID = "VOID";
        public static final List<String> ALL = List.of(DRAFT, OPEN, PAID, UNCOLLECTIBLE, VOID);

        private InvoiceStatus() {
        }
    }

    /** How invoices are collected ({@code collectionMethod}). */
    public static final class CollectionMethod {
        /** The saved card is charged each cycle (default). */
        public static final String AUTO_CHARGE = "AUTO_CHARGE";
        /** The invoice is finalised but not charged. */
        public static final String SEND_INVOICE = "SEND_INVOICE";
        /** Collection is up to the merchant. */
        public static final String MANUAL = "MANUAL";
        public static final List<String> ALL = List.of(AUTO_CHARGE, SEND_INVOICE, MANUAL);

        private CollectionMethod() {
        }
    }

    /** {@code billing.frequency} of a plan. */
    public static final class BillingFrequency {
        public static final String DAILY = "DAILY";
        public static final String WEEKLY = "WEEKLY";
        public static final String MONTHLY = "MONTHLY";
        public static final String YEARLY = "YEARLY";
        public static final List<String> ALL = List.of(DAILY, WEEKLY, MONTHLY, YEARLY);

        private BillingFrequency() {
        }
    }

    /** {@code verificationMode} of a saved card. */
    public static final class CardVerificationMode {
        public static final String NONE = "NONE";
        public static final String ON_ADD = "ON_ADD";
        public static final String FIRST_CHARGE = "FIRST_CHARGE";
        public static final List<String> ALL = List.of(NONE, ON_ADD, FIRST_CHARGE);

        private CardVerificationMode() {
        }
    }

    /** {@code behavior} of a plan change: now (prorated) or at the next period. */
    public static final class PlanChangeBehavior {
        public static final String IMMEDIATE = "IMMEDIATE";
        public static final String NEXT_PERIOD = "NEXT_PERIOD";
        public static final List<String> ALL = List.of(IMMEDIATE, NEXT_PERIOD);

        private PlanChangeBehavior() {
        }
    }

    /** {@code sdkMode} of a portal SDK session. */
    public static final class PortalSdkMode {
        public static final String FULL = "FULL";
        public static final String HEADLESS = "HEADLESS";
        public static final String HYBRID = "HYBRID";
        public static final List<String> ALL = List.of(FULL, HEADLESS, HYBRID);

        private PortalSdkMode() {
        }
    }

    /**
     * @param subscription a subscription map (its {@code status}) or a status string
     * @return {@code true} while the subscription may be billed ({@code TRIALING}, {@code ACTIVE}, {@code PAST_DUE})
     */
    public static boolean isActive(Object subscription) {
        return Status.ACTIVE_STATES.contains(statusOf(subscription));
    }

    /**
     * @param subscription a subscription map (its {@code status}) or a status string
     * @return {@code true} for {@code UPGRADED}, {@code DOWNGRADED}, {@code CANCELED}, {@code EXPIRED}
     */
    public static boolean isTerminal(Object subscription) {
        return Status.TERMINAL_STATES.contains(statusOf(subscription));
    }

    /**
     * The hosted portal URL of a portal link.
     *
     * @param link the {@code portal().createPortalLink()} answer (its {@code token}) or the token
     * @return {@code https://portal.lunixi.com/?token=…}
     */
    public static String portalUrl(Object link) {
        return portalUrl(link, PORTAL_HOSTED_BASE);
    }

    /**
     * @param link the portal link answer or its token
     * @param base the hosted portal base URL
     * @return {@code <base>/?token=…}
     */
    public static String portalUrl(Object link, String base) {
        String token = link instanceof String text ? text
                : link instanceof Map<?, ?> map && map.get("token") instanceof String text ? text : "";
        String root = String.valueOf(base).replaceAll("/+$", "");
        return root + "/?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String statusOf(Object subscription) {
        if (subscription instanceof String text) {
            return text;
        }
        return subscription instanceof Map<?, ?> map && map.get("status") instanceof String text ? text : "";
    }
}
