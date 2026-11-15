package com.lunixi.sdk.payment;

import java.util.List;

/** What the buyer must do when a payment response carries {@code data.action} ({@code action.type}). */
public final class PaymentActionType {

    /** Send the one-time code with {@link PaymentsClient#confirmPaymentAction}. */
    public static final String OTP = "OTP";
    /** Send the buyer to {@code action.redirect.url} (for cards: the bank's 3D Secure page). */
    public static final String REDIRECT = "REDIRECT";
    /**
     * Show the buyer something to act on outside the page: a QR code, or for {@link PaymentMethod#BANK_TRANSFER}
     * the transfer instruction ({@link PaymentBankTransferInstruction}).
     */
    public static final String DISPLAY = "DISPLAY";
    /** The buyer approves in the provider's app; the result arrives by webhook. */
    public static final String APP_APPROVAL = "APP_APPROVAL";
    /** Send the fields listed in {@code action.input.fields} with {@link PaymentsClient#confirmPaymentAction}. */
    public static final String INPUT = "INPUT";

    /** Every action type. */
    public static final List<String> ALL = List.of(OTP, REDIRECT, DISPLAY, APP_APPROVAL, INPUT);

    private PaymentActionType() {
    }
}
