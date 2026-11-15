package com.lunixi.sdk.internal;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.lunixi.sdk.exception.WebhookVerificationException;
import com.lunixi.sdk.webhook.WebhookEvent;

/**
 * Reads the {@code data} of a verified webhook against the event's allow list: the type must be one of the expected
 * types, only the listed fields are kept, and money fields must be canonical minor-unit strings.
 *
 * <p>SDK internals; this type's signature may change in a minor release.
 */
public final class WebhookPayloads {

    private WebhookPayloads() {
    }

    /**
     * @param event       the verified event
     * @param types       the accepted event types
     * @param fields      the allow-listed {@code data} fields
     * @param moneyFields the fields that must be minor-unit strings when present
     * @param label       the event family, for the error message
     * @return the allow-listed {@code data}, in list order, immutable
     * @throws WebhookVerificationException when the event is missing, of another type or carries a malformed amount
     */
    public static Map<String, Object> read(WebhookEvent event, Collection<String> types, List<String> fields,
                                           Collection<String> moneyFields, String label) {
        if (event == null) {
            throw new WebhookVerificationException("A verified event is required.");
        }
        if (!types.contains(event.type())) {
            throw new WebhookVerificationException("Not " + label + " event (type " + event.type() + ").");
        }
        Map<String, Object> source = event.data() == null ? Map.of() : event.data();
        Map<String, Object> out = new LinkedHashMap<>();
        for (String field : fields) {
            if (source.containsKey(field)) {
                out.put(field, source.get(field));
            }
        }
        for (String field : moneyFields) {
            Object value = out.get(field);
            if (value != null && !(value instanceof String text && text.matches("0|[1-9][0-9]{0,18}"))) {
                throw new WebhookVerificationException("'" + field + "' is not a minor-unit amount string.");
            }
        }
        return ResponseMaps.immutableCopy(out);
    }

    /** @return the string value of a field, or empty when it is absent, {@code null} or not a string */
    public static Optional<String> string(Map<String, Object> data, String field) {
        return data.get(field) instanceof String text ? Optional.of(text) : Optional.empty();
    }
}
