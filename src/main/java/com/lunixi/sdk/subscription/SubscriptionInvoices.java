package com.lunixi.sdk.subscription;

import java.util.Map;

import com.lunixi.sdk.internal.RouteRequest;

/** Invoices ({@code /api/v1/subscriptions/invoices}): billing history, void and charge retry. */
public final class SubscriptionInvoices {

    private final RouteRequest routes;

    SubscriptionInvoices(RouteRequest routes) {
        this.routes = routes;
    }

    /** @return the first page of invoices */
    public SubscriptionPage list() {
        return list(Map.of());
    }

    /**
     * @param filters {@code subscriptionId}, {@code status}, {@code pageSize}, {@code pageToken}
     * @return one page of invoices
     */
    public SubscriptionPage list(Map<String, ?> filters) {
        return new SubscriptionPage(routes.send("invoices.list", Map.of(), filters, null, null));
    }

    /**
     * @param invoiceId the invoice id
     * @return the invoice ({@code data})
     */
    public Map<String, Object> get(String invoiceId) {
        return RouteRequest.data(routes.send("invoices.get", RouteRequest.params("invoiceId", invoiceId), null, null,
                null));
    }

    /**
     * Voids an invoice ({@code invoices.void} in the catalog; {@code void} is a Java keyword).
     *
     * @param invoiceId the invoice id
     * @return the invoice ({@code data})
     */
    public Map<String, Object> voidInvoice(String invoiceId) {
        return voidInvoice(invoiceId, null);
    }

    /**
     * @param invoiceId the invoice id
     * @param reason    the reason, or {@code null}
     * @return the invoice ({@code data})
     */
    public Map<String, Object> voidInvoice(String invoiceId, String reason) {
        return RouteRequest.data(routes.send("invoices.void", RouteRequest.params("invoiceId", invoiceId), null,
                RouteRequest.compact("reason", RouteRequest.optionalText(reason, "reason")), null));
    }

    /**
     * @param invoiceId the invoice id
     * @return the invoice ({@code data})
     */
    public Map<String, Object> retryCharge(String invoiceId) {
        return retryCharge(invoiceId, null);
    }

    /**
     * Charges an open invoice again.
     *
     * @param invoiceId      the invoice id
     * @param idempotencyKey the key, or {@code null}
     * @return the invoice ({@code data})
     */
    public Map<String, Object> retryCharge(String invoiceId, String idempotencyKey) {
        return RouteRequest.data(routes.send("invoices.retryCharge", RouteRequest.params("invoiceId", invoiceId),
                null, null, idempotencyKey));
    }
}
