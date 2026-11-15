package com.lunixi.sdk.payment;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import com.lunixi.sdk.exception.ConfigurationException;

/**
 * Public payment method codes ({@code paymentMethod}) and the body field lists of the payment-method endpoints:
 *
 * <pre>
 * POST /api/v1/payments/direct/payment                          (start a payment with any method)
 * POST /api/v1/payments/{paymentId}/actions/{actionId}/confirm  (OTP, consent, requested fields)
 * POST /api/v1/payments/{paymentId}/actions/{actionId}/resend   (new one-time code)
 * GET  /api/v1/payments/methods[?paymentMethod=]                (methods open for the merchant)
 * </pre>
 *
 * <p>The gateway refuses a body key outside its field list with 400; the lists below are the gateway's, and the
 * SDK checks a body against them before sending. Provider codes are never fixed in code: read them from
 * {@link PaymentsClient#listPaymentMethods()} ({@code providers[].providerCode}).
 */
public final class PaymentMethod {

    public static final String CARD = "CARD";
    public static final String BNPL = "BNPL";
    public static final String SHOPPING_LOAN = "SHOPPING_LOAN";
    public static final String PROMISSORY_NOTE = "PROMISSORY_NOTE";
    public static final String MEAL_CARD = "MEAL_CARD";
    public static final String GIFT_CARD = "GIFT_CARD";
    public static final String CASH = "CASH";
    public static final String CASH_ON_DELIVERY = "CASH_ON_DELIVERY";
    public static final String WALLET = "WALLET";
    /** Para Transferi (havale / EFT / FAST) to the merchant's own bank account. */
    public static final String BANK_TRANSFER = "BANK_TRANSFER";
    public static final String OPEN_BANKING = "OPEN_BANKING";
    /** Faturana Yansıt (charge to the mobile phone bill). */
    public static final String MOBILE_BILLING = "MOBILE_BILLING";
    public static final String QR_CODE = "QR_CODE";
    public static final String CRYPTO = "CRYPTO";
    public static final String OTHER = "OTHER";

    /** Accepted alias of {@link #QR_CODE}; {@link #normalize(String)} turns it into {@code QR_CODE}. */
    public static final String QR = "QR";

    /** Every method code, in the platform's catalog order. */
    public static final List<String> ALL = List.of(CARD, BNPL, SHOPPING_LOAN, PROMISSORY_NOTE, MEAL_CARD, GIFT_CARD,
            CASH, CASH_ON_DELIVERY, WALLET, BANK_TRANSFER, OPEN_BANKING, MOBILE_BILLING, QR_CODE, CRYPTO, OTHER);

    /**
     * Method blocks of {@code direct/payment} besides {@code card}. The block of the chosen method is required; a
     * block for another method is refused. A method without a block here cannot be paid through
     * {@code direct/payment} yet; {@link PaymentsClient#listPaymentMethods()} lists only the methods that can.
     */
    public static final List<String> METHOD_BLOCK_FIELDS = List.of(
            "mobileBilling", "wallet", "promissoryNote", "mealCard", "openBanking", "shoppingLoan", "bankTransfer");

    /** Fields that belong to a card payment only ({@code paymentMethod: CARD}). */
    public static final List<String> CARD_ONLY_FIELDS = List.of("card", "installment", "installmentQuoteToken",
            "force3D");

    /** Body fields of {@code POST /payments/direct/payment}: order, card and option fields plus the method blocks. */
    public static final List<String> DIRECT_METHOD_PAYMENT_FIELDS = List.of(
            "paymentMethod", "amount", "currency", "orderId", "paymentChannel", "paymentGroup", "callbackUrl",
            "locale", "description", "customerId", "preauth", "buyer", "shippingAddress", "billingAddress",
            "basketItems", "card", "installment", "installmentQuoteToken", "force3D", "metadata",
            "mobileBilling", "wallet", "promissoryNote", "mealCard", "openBanking", "shoppingLoan", "bankTransfer");

    /**
     * Body fields of {@code POST /payments/{id}/actions/{actionId}/confirm} that a merchant server sends. The
     * checkout token fields ({@code token}, {@code checkoutSessionProof}) belong to the hosted form; a signed
     * request that also carries a token is refused, so the SDK never sends them.
     */
    public static final List<String> PAYMENT_ACTION_CONFIRM_FIELDS = List.of("otp", "consentAccepted", "fields");

    /**
     * Most basket items a shopping-loan payment ({@link #SHOPPING_LOAN}) can carry. The basket is required: the
     * bank sets the loan term from the items' product categories.
     */
    public static final int SHOPPING_LOAN_MAX_BASKET_ITEMS = 100;

    /** The "Other banks" option of the {@code bankCode} field of a {@link #BANK_TRANSFER} payment (EFT / FAST). */
    public static final String BANK_TRANSFER_OTHER_BANKS_CODE = "9999";

    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]{0,31}");

    private PaymentMethod() {
    }

    /**
     * The canonical method code; {@code QR} becomes {@code QR_CODE}.
     *
     * @param paymentMethod a method code such as {@code MOBILE_BILLING}
     * @return the canonical code
     * @throws ConfigurationException when the value is not a method code (upper-case letters, digits, {@code _})
     */
    public static String normalize(String paymentMethod) {
        if (paymentMethod == null || !CODE_PATTERN.matcher(paymentMethod).matches()) {
            throw new ConfigurationException("paymentMethod must be a payment method code such as "
                    + MOBILE_BILLING + "; got " + quote(paymentMethod) + ".");
        }
        return QR.equals(paymentMethod) ? QR_CODE : paymentMethod;
    }

    /**
     * Name of the method's block in the request body: the camelCase form of the method code
     * ({@code MOBILE_BILLING} → {@code mobileBilling}, {@code BANK_TRANSFER} → {@code bankTransfer}). It is also
     * returned as {@code blockName} by {@link PaymentsClient#listPaymentMethods()}.
     *
     * @param paymentMethod a method code
     * @return the block name
     */
    public static String blockName(String paymentMethod) {
        String code = normalize(paymentMethod).toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(code.length());
        boolean upper = false;
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '_' && i + 1 < code.length() && code.charAt(i + 1) != '_') {
                upper = true;
                continue;
            }
            out.append(upper ? Character.toUpperCase(c) : c);
            upper = false;
        }
        return out.toString();
    }

    static String quote(Object value) {
        return value instanceof String text ? "\"" + text + "\"" : String.valueOf(value);
    }
}
