package com.lunixi.sdk.payment;

import java.util.List;

/** {@code providerSelection} of a method row in {@link PaymentsClient#listPaymentMethods()}. */
public final class ProviderSelection {

    /** Card: routing picks the provider; {@code providerCode} is not sent. */
    public static final String ROUTED = "ROUTED";
    /**
     * {@code providerCode} may be omitted: one provider is connected, or the platform picks the provider
     * ({@link ProviderSelectionMode#PLATFORM}).
     */
    public static final String OPTIONAL = "OPTIONAL";
    /** The buyer picks the brand and more than one provider is connected; {@code providerCode} must be sent. */
    public static final String REQUIRED = "REQUIRED";

    /** Every value. */
    public static final List<String> ALL = List.of(ROUTED, OPTIONAL, REQUIRED);

    private ProviderSelection() {
    }
}
