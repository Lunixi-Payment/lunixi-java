package com.lunixi.sdk.paymentlink;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A typed question the buyer answers on the payment page (no file uploads). */
public final class PaymentLinkCustomField {

    private final Map<String, Object> payload = new LinkedHashMap<>();

    private PaymentLinkCustomField(String key, String label, String fieldType) {
        payload.put("key", PaymentLinkRules.text(key, "custom field key"));
        payload.put("label", PaymentLinkRules.text(label, "custom field label"));
        payload.put("fieldType", PaymentLinkRules.oneOf(fieldType, PaymentLinkValues.CustomFieldType.ALL,
                "fieldType"));
    }

    /**
     * @param key       the answer's key
     * @param label     the question shown to the buyer
     * @param fieldType a {@link PaymentLinkValues.CustomFieldType} value
     * @return a new field
     */
    public static PaymentLinkCustomField create(String key, String label, String fieldType) {
        return new PaymentLinkCustomField(key, label, fieldType);
    }

    public PaymentLinkCustomField withRequired(boolean required) {
        payload.put("required", required);
        return this;
    }

    /**
     * @param options the choices of a {@code SELECT} field
     * @return this field
     */
    public PaymentLinkCustomField withOptions(List<String> options) {
        payload.put("options", PaymentLinkRules.stringList(options, "options"));
        return this;
    }

    public PaymentLinkCustomField withMaxLength(int maxLength) {
        payload.put("maxLength", maxLength);
        return this;
    }

    public PaymentLinkCustomField withHelpText(String helpText) {
        payload.put("helpText", String.valueOf(helpText));
        return this;
    }

    /** @return the field as it is sent, a new map */
    public Map<String, Object> toBody() {
        return new LinkedHashMap<>(payload);
    }
}
