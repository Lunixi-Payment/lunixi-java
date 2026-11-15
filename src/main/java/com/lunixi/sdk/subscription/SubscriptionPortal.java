package com.lunixi.sdk.subscription;

import java.util.Map;

import com.lunixi.sdk.http.RoutePage;
import com.lunixi.sdk.internal.Checks;
import com.lunixi.sdk.internal.RouteRequest;

/**
 * The customer portal. Embedded: {@link #createSdkSession} mints an origin-bound session for the browser portal SDK
 * ({@code origin} must be allow-listed). Hosted: {@link #createPortalLink} returns a link whose {@code token} opens the
 * hosted portal ({@link SubscriptionValues#portalUrl(Object)}); the customer verifies with a one-time code. The link
 * routes live under {@code /api/panel/v1/subscriptions/portal}.
 */
public final class SubscriptionPortal {

    private final RouteRequest routes;

    SubscriptionPortal(RouteRequest routes) {
        this.routes = routes;
    }

    /**
     * @param customerId the customer id
     * @param origin     the allow-listed browser origin
     * @return the session (see {@link #createSdkSession(String, String, String, String)})
     */
    public Map<String, Object> createSdkSession(String customerId, String origin) {
        return createSdkSession(customerId, origin, null, null);
    }

    /**
     * Mints a portal SDK session ({@code POST /portal/sdk-session}). Returns {@code {portalToken, sessionProof,
     * proofNonce, proofExpiresAt, …}} (the gateway answers at the envelope root; {@code nonce} / {@code expiresAt}
     * are copied to {@code proofNonce} / {@code proofExpiresAt} when those are missing); hand those four to the
     * browser.
     *
     * @param customerId   the customer id
     * @param origin       the allow-listed browser origin
     * @param sdkMode      {@code FULL}, {@code HEADLESS} or {@code HYBRID}, or {@code null}
     * @param portalLinkId a portal link to bind the session to, or {@code null}
     * @return the session
     */
    public Map<String, Object> createSdkSession(String customerId, String origin, String sdkMode,
                                                String portalLinkId) {
        if (sdkMode != null && !sdkMode.isEmpty()) {
            SubscriptionChecks.oneOf(sdkMode, SubscriptionValues.PortalSdkMode.ALL, "sdkMode");
        }
        Map<String, Object> body = RouteRequest.compact(
                "customerId", Checks.text(customerId, "customerId"),
                "origin", Checks.text(origin, "origin"),
                "sdkMode", RouteRequest.optionalText(sdkMode, "sdkMode"),
                "portalLinkId", RouteRequest.optionalText(portalLinkId, "portalLinkId"));
        Map<String, Object> session = RouteRequest.mergeRoot(routes.send("portal.createSdkSession", Map.of(), null,
                body, null));
        if (!session.containsKey("proofNonce") && session.containsKey("nonce")) {
            session.put("proofNonce", session.get("nonce"));
        }
        if (!session.containsKey("proofExpiresAt") && session.containsKey("expiresAt")) {
            session.put("proofExpiresAt", session.get("expiresAt"));
        }
        return session;
    }

    /**
     * @param customerId the customer id
     * @return the portal link ({@code data})
     */
    public Map<String, Object> createPortalLink(String customerId) {
        return createPortalLink(customerId, Map.of());
    }

    /**
     * Creates a hosted portal link.
     *
     * @param customerId the customer id
     * @param options    {@code allowedPlanIds}, {@code defaultPlanId}, {@code permissions},
     *                   {@code sessionDurationMinutes}, {@code expiresAt}, {@code requireEmailVerification}, …
     * @return the portal link ({@code data}), with its {@code token}
     */
    public Map<String, Object> createPortalLink(String customerId, Map<String, ?> options) {
        Map<String, Object> body = SubscriptionChecks.body(options, "createPortalLink options");
        body.put("customerId", Checks.text(customerId, "customerId"));
        return RouteRequest.data(routes.send("portal.createPortalLink", Map.of(), null, body, null));
    }

    /** @return the first page of portal links */
    public RoutePage listLinks() {
        return listLinks(Map.of());
    }

    /**
     * @param filters {@code customerId}, {@code status}, {@code limit}
     * @return one page of portal links
     */
    public RoutePage listLinks(Map<String, ?> filters) {
        return new RoutePage(routes.send("portal.listLinks", Map.of(), filters, null, null));
    }

    /**
     * @param linkId the portal link id
     * @return the portal link ({@code data})
     */
    public Map<String, Object> getLink(String linkId) {
        return RouteRequest.data(routes.send("portal.getLink", RouteRequest.params("linkId", linkId), null, null,
                null));
    }

    /**
     * @param linkId  the portal link id
     * @param changes the changes
     * @return the portal link ({@code data})
     */
    public Map<String, Object> updateLink(String linkId, Map<String, ?> changes) {
        return RouteRequest.data(routes.send("portal.updateLink", RouteRequest.params("linkId", linkId), null,
                SubscriptionChecks.body(changes, "portal.updateLink body"), null));
    }

    /**
     * @param linkId the portal link id
     * @return the portal link ({@code data})
     */
    public Map<String, Object> revokeLink(String linkId) {
        return revokeLink(linkId, null);
    }

    /**
     * @param linkId the portal link id
     * @param reason the reason, or {@code null}
     * @return the portal link ({@code data})
     */
    public Map<String, Object> revokeLink(String linkId, String reason) {
        return RouteRequest.data(routes.send("portal.revokeLink", RouteRequest.params("linkId", linkId), null,
                RouteRequest.compact("reason", RouteRequest.optionalText(reason, "reason")), null));
    }

    /**
     * @param linkId the portal link id
     * @return the link's activity ({@code data})
     */
    public Map<String, Object> linkActivity(String linkId) {
        return RouteRequest.data(routes.send("portal.linkActivity", RouteRequest.params("linkId", linkId), null,
                null, null));
    }

    /**
     * Emails a portal invite: {@code {customerId, email?, allowedPlanIds?, …}}.
     *
     * @param invite the invite
     * @return the answer ({@code data})
     */
    public Map<String, Object> sendInvite(Map<String, ?> invite) {
        return RouteRequest.data(routes.send("portal.sendInvite", Map.of(), null,
                SubscriptionChecks.body(invite, "portal.sendInvite body"), null));
    }

    /** @return the portal settings (branding, allowed origins) ({@code data}) */
    public Map<String, Object> getSettings() {
        return RouteRequest.data(routes.send("portal.getSettings"));
    }

    /**
     * @param settings the settings
     * @return the portal settings ({@code data})
     */
    public Map<String, Object> updateSettings(Map<String, ?> settings) {
        return RouteRequest.data(routes.send("portal.updateSettings", Map.of(), null,
                SubscriptionChecks.body(settings, "portal.updateSettings body"), null));
    }
}
