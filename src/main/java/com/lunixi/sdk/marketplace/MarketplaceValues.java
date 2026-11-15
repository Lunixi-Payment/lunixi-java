package com.lunixi.sdk.marketplace;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Marketplace wire values: the same codes as the Node SDK's and the PHP SDK's marketplace constants. Each nested
 * class lists its values in {@code ALL}, in catalog order.
 */
public final class MarketplaceValues {

    /** Hosted dealer onboarding wizard ({@link #onboardingUrl(Map)} when the answer carries no {@code url}). */
    public static final String DEALER_ONBOARDING_HOSTED_BASE = "https://altbayi.lunixi.com";

    private MarketplaceValues() {
    }

    /** Sub-dealer lifecycle. Only {@code ACTIVE} dealers can receive a split. */
    public static final class DealerStatus {
        public static final String DRAFT = "DRAFT";
        public static final String PENDING_KYC = "PENDING_KYC";
        public static final String ACTIVE = "ACTIVE";
        public static final String SUSPENDED = "SUSPENDED";
        public static final String HOLD = "HOLD";
        public static final String CLOSED = "CLOSED";
        public static final List<String> ALL = List.of(DRAFT, PENDING_KYC, ACTIVE, SUSPENDED, HOLD, CLOSED);

        private DealerStatus() {
        }
    }

    /** {@code dealerType} of a sub-dealer. */
    public static final class DealerType {
        public static final String LEGAL_ENTITY = "LEGAL_ENTITY";
        public static final String INDIVIDUAL = "INDIVIDUAL";
        public static final List<String> ALL = List.of(LEGAL_ENTITY, INDIVIDUAL);

        private DealerType() {
        }
    }

    /**
     * {@code multiSellerPolicy} of a split charge: every seller leg must be eligible, or the eligible legs may go
     * through without the others.
     */
    public static final class MultiSellerPolicy {
        public static final String ALL_OR_NOTHING = "ALL_OR_NOTHING";
        public static final String PARTIAL_ALLOWED = "PARTIAL_ALLOWED";
        public static final List<String> ALL = List.of(ALL_OR_NOTHING, PARTIAL_ALLOWED);

        private MultiSellerPolicy() {
        }
    }

    /**
     * The onboarding URL of an {@code onboardingToken()} answer: its {@code url}, else the hosted wizard with its
     * {@code token}.
     *
     * @param link the answer
     * @return the URL
     */
    public static String onboardingUrl(Map<String, ?> link) {
        return onboardingUrl(link, DEALER_ONBOARDING_HOSTED_BASE);
    }

    /**
     * @param link the answer
     * @param base the hosted wizard base URL
     * @return the answer's {@code url}, else {@code <base>/m/<token>}
     */
    public static String onboardingUrl(Map<String, ?> link, String base) {
        if (link != null && link.get("url") instanceof String url && !url.isEmpty()) {
            return url;
        }
        String token = link != null && link.get("token") instanceof String text ? text : "";
        return String.valueOf(base).replaceAll("/+$", "") + "/m/"
                + URLEncoder.encode(token, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
