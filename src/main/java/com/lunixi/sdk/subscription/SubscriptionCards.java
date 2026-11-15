package com.lunixi.sdk.subscription;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.lunixi.sdk.exception.ConfigurationException;
import com.lunixi.sdk.internal.Checks;
import com.lunixi.sdk.internal.RouteRequest;

/**
 * Saved cards for automatic charges ({@code /api/v1/subscriptions/cards}). A card is added from the checkout SDK's
 * {@code paymentMethodToken}; a card number or security code is never sent (the fields are refused, and the refusal
 * names only the field).
 */
public final class SubscriptionCards {

    private final RouteRequest routes;

    SubscriptionCards(RouteRequest routes) {
        this.routes = routes;
    }

    /**
     * Same as {@link #add(Map, String)} without an {@code Idempotency-Key}.
     *
     * @param request the card
     * @return the card ({@code data})
     */
    public Map<String, Object> add(Map<String, ?> request) {
        return add(request, null);
    }

    /**
     * Saves a card: {@code customerId}, {@code paymentMethodToken}, and optionally {@code brand}, {@code last4},
     * {@code bin}, {@code expireMonth}, {@code expireYear}, {@code cardholderName}, {@code isDefault},
     * {@code nickname}, {@code verificationMode}, {@code supportedCurrencies}.
     *
     * @param request        the card
     * @param idempotencyKey the key, or {@code null}
     * @return the card ({@code data})
     */
    public Map<String, Object> add(Map<String, ?> request, String idempotencyKey) {
        Map<String, Object> body = SubscriptionChecks.body(request, "cards.add request");
        if (body.get("verificationMode") != null) {
            SubscriptionChecks.oneOf(body.get("verificationMode"), SubscriptionValues.CardVerificationMode.ALL,
                    "verificationMode");
        }
        SubscriptionChecks.countField(body, "expireMonth", 1, "expireMonth");
        SubscriptionChecks.countField(body, "expireYear", 1, "expireYear");
        SubscriptionChecks.booleanField(body, "isDefault", "isDefault");
        if (body.get("supportedCurrencies") != null) {
            if (!(body.get("supportedCurrencies") instanceof Collection<?> currencies)) {
                throw new ConfigurationException("supportedCurrencies must be a list.");
            }
            List<String> codes = new ArrayList<>();
            for (Object currency : currencies) {
                codes.add(SubscriptionChecks.supportedCurrency(currency, "supportedCurrencies"));
            }
            body.put("supportedCurrencies", codes);
        }
        return RouteRequest.data(routes.send("cards.add", Map.of(), null, body, idempotencyKey));
    }

    /**
     * @param cardId the card id
     * @return the card ({@code data})
     */
    public Map<String, Object> get(String cardId) {
        return RouteRequest.data(routes.send("cards.get", RouteRequest.params("cardId", cardId), null, null, null));
    }

    /**
     * @param customerId the customer id
     * @return the first page of the customer's active cards
     */
    public SubscriptionPage list(String customerId) {
        return list(customerId, Map.of());
    }

    /**
     * A customer's cards.
     *
     * @param customerId the customer id
     * @param options    {@code includeInactive} (default {@code false}), {@code pageSize}, {@code pageToken}
     * @return one page of cards
     */
    public SubscriptionPage list(String customerId, Map<String, ?> options) {
        Map<String, Object> given = Checks.plainMap(options == null ? Map.of() : options, "cards.list options");
        Map<String, Object> query = RouteRequest.compact("customerId", Checks.text(customerId, "customerId"),
                "includeInactive", given.getOrDefault("includeInactive", false));
        given.forEach((field, value) -> {
            if (!"includeInactive".equals(field)) {
                query.put(field, value);
            }
        });
        return new SubscriptionPage(routes.send("cards.list", Map.of(), query, null, null));
    }

    /**
     * @param cardId the card id
     * @return the card ({@code data})
     */
    public Map<String, Object> setDefault(String cardId) {
        return RouteRequest.data(routes.send("cards.setDefault", RouteRequest.params("cardId", cardId), null, null,
                null));
    }

    /**
     * @param cardId the card id
     * @return the card ({@code data})
     */
    public Map<String, Object> deactivate(String cardId) {
        return deactivate(cardId, null);
    }

    /**
     * @param cardId the card id
     * @param reason the reason, or {@code null}
     * @return the card ({@code data})
     */
    public Map<String, Object> deactivate(String cardId, String reason) {
        return RouteRequest.data(routes.send("cards.deactivate", RouteRequest.params("cardId", cardId), null,
                RouteRequest.compact("reason", RouteRequest.optionalText(reason, "reason")), null));
    }

    /**
     * @param cardId the card id
     * @return the verification ({@code data})
     */
    public Map<String, Object> verify(String cardId) {
        return verify(cardId, Map.of());
    }

    /**
     * Verifies a saved card (a small authorisation or a 3-D check).
     *
     * @param cardId  the card id
     * @param options the options as the gateway documents them
     * @return the verification ({@code data})
     */
    public Map<String, Object> verify(String cardId, Map<String, ?> options) {
        return RouteRequest.data(routes.send("cards.verify", RouteRequest.params("cardId", cardId), null,
                SubscriptionChecks.body(options, "cards.verify options"), null));
    }

    /**
     * Card metadata (label, expiry, billing…); never a card number.
     *
     * @param cardId  the card id
     * @param changes the changes
     * @return the card ({@code data})
     */
    public Map<String, Object> update(String cardId, Map<String, ?> changes) {
        return RouteRequest.data(routes.send("cards.update", RouteRequest.params("cardId", cardId), null,
                SubscriptionChecks.body(changes, "cards.update body"), null));
    }
}
