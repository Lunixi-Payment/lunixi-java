package com.lunixi.sdk.wallet;

import java.util.List;
import java.util.Map;

import com.lunixi.sdk.http.Envelope;
import com.lunixi.sdk.internal.CursorPages;

/** Corporate accounts and their employees ({@code client.wallet().corporate()}). */
public final class WalletCorporate {

    private final WalletInvoker invoker;

    WalletCorporate(WalletInvoker invoker) {
        this.invoker = invoker;
    }

    /**
     * Creates a corporate account ({@code POST /wallet/corporate-accounts}).
     *
     * @param request        {@code programId}, {@code code}, {@code name}, {@code ownerEndUserId}; optional
     *                       {@code fundingCurrency}
     * @param idempotencyKey required
     * @return the account ({@code data})
     */
    public Map<String, Object> create(Map<String, ?> request, String idempotencyKey) {
        return Envelope.data(invoker.call("createCorporateAccount", Map.of(), null, request, idempotencyKey));
    }

    /** @return the first page of corporate accounts */
    public WalletPage list() {
        return list(Map.of());
    }

    /**
     * Corporate accounts ({@code GET /wallet/corporate-accounts}). Filters: {@code programId}, {@code status}
     * ({@link WalletValues.CorporateAccountStatus}), {@code limit}, {@code cursor}.
     *
     * @param filters the filters
     * @return the page
     */
    public WalletPage list(Map<String, ?> filters) {
        return new WalletPage(invoker.call("listCorporateAccounts", Map.of(), filters, null, null));
    }

    /** Every corporate account matching the filters, following the cursors. */
    public Iterable<Map<String, Object>> iterate(Map<String, ?> filters) {
        return CursorPages.iterate(filters, this::list, WalletPage::items, page -> page.nextCursor().orElse(null));
    }

    /**
     * Imports up to 1000 employees at once; all or nothing (a 422 writes nothing).
     *
     * @param corporateAccountId the corporate account id
     * @param rows               {@code phone}; optional {@code displayName}, {@code externalCustomerId},
     *                           {@code kycRef}
     * @param idempotencyKey     required
     * @return the import result ({@code data})
     */
    public Map<String, Object> importEmployees(String corporateAccountId, List<? extends Map<String, ?>> rows,
                                               String idempotencyKey) {
        return Envelope.data(invoker.call("importCorporateEmployees",
                WalletInvoker.path("corporateAccountId", corporateAccountId), null,
                WalletInvoker.compact("rows", rows), idempotencyKey));
    }

    /**
     * Offboards employees ({@code POST …/employees/offboard}).
     *
     * @param corporateAccountId the corporate account id
     * @param rows               each with {@code endUserId} or {@code externalCustomerId}
     * @param reason             a note, or {@code null}
     * @param idempotencyKey     required
     * @return the result ({@code data})
     */
    public Map<String, Object> offboardEmployees(String corporateAccountId, List<? extends Map<String, ?>> rows,
                                                 String reason, String idempotencyKey) {
        return Envelope.data(invoker.call("offboardCorporateEmployees",
                WalletInvoker.path("corporateAccountId", corporateAccountId), null,
                WalletInvoker.compact("rows", rows, "reason", reason), idempotencyKey));
    }
}
