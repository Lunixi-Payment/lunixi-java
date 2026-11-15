package com.lunixi.sdk.payment;

import java.util.List;

/** {@code providerSelectionMode} of a method row in {@link PaymentsClient#listPaymentMethods()}: who picks the provider. */
public final class ProviderSelectionMode {

    /**
     * The buyer picks the brand (wallet brand, lending bank, card brand). With more than one provider connected
     * {@code providerCode} is required; without it the gateway answers 400 {@code payment.provider.required}.
     */
    public static final String BUYER = "BUYER";
    /**
     * The buyer does not pick a brand. {@code providerCode} is optional: when it is omitted the platform picks one
     * of the connected providers before any money moves and keeps it for the payment. When none of them can take
     * the payment the gateway answers 422 {@code payment.provider.none_eligible}.
     */
    public static final String PLATFORM = "PLATFORM";

    /** Every value. */
    public static final List<String> ALL = List.of(BUYER, PLATFORM);

    private ProviderSelectionMode() {
    }
}
