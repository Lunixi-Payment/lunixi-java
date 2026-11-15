package com.lunixi.sdk.payment;

import java.util.Map;
import java.util.Optional;

import com.lunixi.sdk.http.Envelope;

/**
 * The transfer instruction of a {@link PaymentMethod#BANK_TRANSFER} payment: the {@code DISPLAY} action's
 * {@code bankTransfer} object ({@code data.action.bankTransfer}) of the {@link PaymentsClient#directMethodPayment}
 * answer (HTTP 202, {@code status: PROCESSING}). Show it to the buyer; the money goes to your own bank account and
 * the payment is matched by {@link #paymentCode()} written in the transfer description.
 *
 * <pre>
 * PaymentBankTransferInstruction.fromResponse(response).ifPresent(instruction -&gt;
 *         show(instruction.bankName(), instruction.holderName(), instruction.iban(), instruction.paymentCode()));
 * </pre>
 *
 * @param bankCode    4-digit bank code of your bank the money is sent to
 * @param bankName    the bank's name
 * @param holderName  account holder name; the buyer enters it as the recipient name
 * @param iban        IBAN without spaces
 * @param amountMinor amount to send (canonical minor-unit string)
 * @param currency    ISO 4217 code
 * @param paymentCode code the buyer writes in the transfer description; it matches the transfer to the payment
 * @param expiresAt   ISO 8601 deadline
 * @param otherBank   {@code true}: the buyer chose "Other banks" ({@link PaymentMethod#BANK_TRANSFER_OTHER_BANKS_CODE});
 *                    the instruction is for EFT / FAST
 */
public record PaymentBankTransferInstruction(String bankCode, String bankName, String holderName, String iban,
                                             String amountMinor, String currency, String paymentCode,
                                             String expiresAt, boolean otherBank) {

    /**
     * Reads the instruction from a payment answer (the envelope returned by {@code directMethodPayment}).
     *
     * @param response the gateway envelope
     * @return the instruction, or empty when there is no action, the action is not {@code DISPLAY} or it carries
     *         no {@code bankTransfer}
     */
    public static Optional<PaymentBankTransferInstruction> fromResponse(Map<String, Object> response) {
        return fromAction(Envelope.data(response).get("action"));
    }

    /**
     * Reads the instruction from an {@code action} object.
     *
     * @param action the {@code data.action} value, possibly {@code null}
     * @return the instruction, or empty when the action is not a {@code DISPLAY} with {@code bankTransfer}
     */
    public static Optional<PaymentBankTransferInstruction> fromAction(Object action) {
        if (!(action instanceof Map<?, ?> map) || !PaymentActionType.DISPLAY.equals(map.get("type"))
                || !(map.get("bankTransfer") instanceof Map<?, ?> instruction)) {
            return Optional.empty();
        }
        return Optional.of(new PaymentBankTransferInstruction(text(instruction, "bankCode"),
                text(instruction, "bankName"), text(instruction, "holderName"), text(instruction, "iban"),
                text(instruction, "amountMinor"), text(instruction, "currency"), text(instruction, "paymentCode"),
                text(instruction, "expiresAt"), Boolean.TRUE.equals(instruction.get("otherBank"))));
    }

    private static String text(Map<?, ?> source, String key) {
        return source.get(key) instanceof String value ? value : null;
    }
}
