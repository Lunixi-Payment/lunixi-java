package com.lunixi.sdk.wallet;

import java.util.Map;

import com.lunixi.sdk.http.Envelope;

/**
 * Bulk payouts ({@code client.wallet().bulkPayouts()}). Maker-checker: the approver must be a DIFFERENT key than the
 * creator (the principal is the signing key id). Creating needs {@code wallet:bulk:create}; submit, approve, process
 * and retry-failed need {@code wallet:bulk:approve} — so the maker key holds both, the checker key holds
 * {@code wallet:bulk:approve}.
 */
public final class WalletBulkPayouts {

    private final WalletInvoker invoker;

    WalletBulkPayouts(WalletInvoker invoker) {
        this.invoker = invoker;
    }

    /**
     * Creates a batch ({@code POST /wallet/bulk-payouts}).
     *
     * @param request        {@code programId}, {@code sourceWalletAccountId}, {@code currency}, {@code items}
     *                       ({@code targetType} {@link WalletValues.BulkTargetType}, {@code targetRef},
     *                       {@code amount}; optional {@code beneficiaryName}); optional {@code reason}
     * @param idempotencyKey required
     * @return the batch ({@code data})
     */
    public Map<String, Object> create(Map<String, ?> request, String idempotencyKey) {
        return Envelope.data(invoker.call("createBulkPayout", Map.of(), null, request, idempotencyKey));
    }

    /** @return the first page of batches */
    public WalletPage list() {
        return list(Map.of());
    }

    /** Batches ({@code GET /wallet/bulk-payouts}). Filters: {@code programId}, {@code status}, {@code limit}. */
    public WalletPage list(Map<String, ?> filters) {
        return new WalletPage(invoker.call("listBulkPayouts", Map.of(), filters, null, null));
    }

    /** @return one batch ({@code GET /wallet/bulk-payouts/{batchId}}) */
    public Map<String, Object> get(String batchId) {
        return Envelope.data(invoker.call("getBulkPayout", WalletInvoker.path("batchId", batchId)));
    }

    /** @return the first page of the batch's items */
    public WalletPage listItems(String batchId) {
        return listItems(batchId, Map.of());
    }

    /** The batch's items ({@code GET …/items}). Filters: {@code status}, {@code limit}. */
    public WalletPage listItems(String batchId, Map<String, ?> filters) {
        return new WalletPage(invoker.call("listBulkPayoutItems", WalletInvoker.path("batchId", batchId), filters,
                null, null));
    }

    /** Submits the batch for approval ({@code POST …/submit}). */
    public Map<String, Object> submit(String batchId) {
        return Envelope.data(invoker.call("submitBulkPayout", WalletInvoker.path("batchId", batchId)));
    }

    /** Approves the batch ({@code POST …/approve} with {@code approve: true}). */
    public Map<String, Object> approve(String batchId) {
        return approve(batchId, true, null);
    }

    /**
     * Approves or declines the batch ({@code POST …/approve}).
     *
     * @param batchId the batch id
     * @param approve {@code false} declines the batch with {@code reason}
     * @param reason  a note, or {@code null}
     * @return the batch ({@code data})
     */
    public Map<String, Object> approve(String batchId, boolean approve, String reason) {
        return Envelope.data(invoker.call("approveBulkPayout", WalletInvoker.path("batchId", batchId), null,
                WalletInvoker.compact("approve", approve, "reason", reason), null));
    }

    /** Rejects the batch ({@code POST …/reject}). */
    public Map<String, Object> reject(String batchId) {
        return reject(batchId, null);
    }

    /** Rejects the batch with a reason ({@code POST …/reject}). */
    public Map<String, Object> reject(String batchId, String reason) {
        return Envelope.data(invoker.call("rejectBulkPayout", WalletInvoker.path("batchId", batchId), null,
                WalletInvoker.compact("reason", reason), null));
    }

    /** Pays out an approved batch ({@code POST …/process}). */
    public Map<String, Object> process(String batchId) {
        return Envelope.data(invoker.call("processBulkPayout", WalletInvoker.path("batchId", batchId)));
    }

    /** Retries the failed items of a processed batch ({@code POST …/retry-failed}). */
    public Map<String, Object> retryFailed(String batchId) {
        return Envelope.data(invoker.call("retryFailedBulkPayout", WalletInvoker.path("batchId", batchId)));
    }
}
