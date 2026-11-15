package com.lunixi.sdk.payment;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.lunixi.sdk.exception.ConfigurationException;

/**
 * Body of {@link PaymentsClient#installmentOptions(InstallmentOptionsRequest)}
 * ({@code POST /api/v1/bin/installments}): the installment table of an amount, and with a BIN and
 * {@code installment > 1} the binding {@code quoteToken} to pass to
 * {@link DirectMethodPaymentRequest#withInstallment(int, String)}.
 *
 * <pre>
 * Map&lt;String, Object&gt; table = client.payments().installmentOptions(
 *         InstallmentOptionsRequest.create("10000", "TRY")
 *                 .withBinOrPan("454360")
 *                 .withEnabledInstallments(List.of(1, 2, 3)));
 * </pre>
 *
 * <p>Every value is checked when it is set; a rule violation is a {@link ConfigurationException}. The card number
 * never appears in an error message or in {@link #toString()}.
 */
public final class InstallmentOptionsRequest {

    private final Map<String, Object> payload = new LinkedHashMap<>();

    private InstallmentOptionsRequest(String amount, String currency) {
        payload.put("amount", PaymentRules.positiveMinor(amount, "amount"));
        payload.put("currency", CardRules.currency(currency));
    }

    /**
     * @param amount   the basket total in minor units, a canonical positive string such as {@code "10000"}
     * @param currency ISO 4217 code such as {@code TRY}
     * @return a new request
     */
    public static InstallmentOptionsRequest create(String amount, String currency) {
        return new InstallmentOptionsRequest(amount, currency);
    }

    /**
     * @param binOrPan a 6- or 8-digit BIN or the card number (6–19 digits)
     * @return this request
     */
    public InstallmentOptionsRequest withBinOrPan(String binOrPan) {
        payload.put("binOrPan", CardRules.binOrPan(binOrPan));
        return this;
    }

    /**
     * The installment count to quote (1–36); with {@code installment > 1} and a BIN the answer carries the
     * {@code quoteToken}.
     *
     * @param installment the installment count
     * @return this request
     */
    public InstallmentOptionsRequest withInstallment(int installment) {
        payload.put("installment", CardRules.installment(installment));
        return this;
    }

    /**
     * @param cardBrand the card brand, from {@link PaymentsClient#cardTaxonomy()}
     * @return this request
     */
    public InstallmentOptionsRequest withCardBrand(String cardBrand) {
        payload.put("cardBrand", PaymentRules.text(cardBrand, "cardBrand"));
        return this;
    }

    /**
     * Limits the table to these installment counts (e.g. {@code List.of(1, 2, 3)} for a product page that allows
     * at most 3). Same rule as the {@code enabledInstallments} of {@link PaymentsClient#createIntent}: distinct
     * integers 1–36 that include 1; sent in ascending order.
     *
     * @param counts the installment counts
     * @return this request
     */
    public InstallmentOptionsRequest withEnabledInstallments(List<Integer> counts) {
        payload.put("enabledInstallments", EnabledInstallments.require(counts, "enabledInstallments"));
        return this;
    }

    /** @return the body that is sent, a new map */
    public Map<String, Object> toBody() {
        return new LinkedHashMap<>(payload);
    }

    @Override
    public String toString() {
        Map<String, Object> shown = new LinkedHashMap<>(payload);
        shown.computeIfPresent("binOrPan", (key, value) -> "<redacted>");
        return "InstallmentOptionsRequest" + shown;
    }
}
