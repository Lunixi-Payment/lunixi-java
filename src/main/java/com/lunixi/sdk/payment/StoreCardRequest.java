package com.lunixi.sdk.payment;

import java.util.LinkedHashMap;
import java.util.Map;

import com.lunixi.sdk.exception.ConfigurationException;

/**
 * Body of {@link PaymentsClient#storeCard(StoreCardRequest, String)} ({@code POST /api/v1/payments/cards}): stores
 * a card for a card owner and returns its {@code storedCardToken}.
 *
 * <pre>
 * Map&lt;String, Object&gt; stored = client.payments().storeCard(
 *         StoreCardRequest.create(cardFields, customer.cardUserKey(), "https://merchant.example/cards/return"),
 *         persistedOperationKey);
 * </pre>
 *
 * <p>The card fields are sent as given and are never shown in an error message or in {@link #toString()}. A
 * rule violation is a {@link ConfigurationException}.
 */
public final class StoreCardRequest {

    private final Map<String, Object> card;
    private final String cardUserKey;
    private final String callbackUrl;
    private String currency;

    private StoreCardRequest(Map<String, ?> card, String cardUserKey, String callbackUrl) {
        this.card = CardRules.card(card);
        this.cardUserKey = cardUserKey == null ? null : PaymentRules.text(cardUserKey, "cardUserKey");
        this.callbackUrl = callbackUrl == null ? null : PaymentRules.httpsUrl(callbackUrl, "callbackUrl");
    }

    /**
     * @param card        the card fields
     * @param cardUserKey the card owner's key, or {@code null} for a new owner (the answer carries the new key)
     * @param callbackUrl the HTTPS return URL when the card is verified with 3D Secure, or {@code null}
     * @return a new request
     */
    public static StoreCardRequest create(Map<String, ?> card, String cardUserKey, String callbackUrl) {
        return new StoreCardRequest(card, cardUserKey, callbackUrl);
    }

    /**
     * @param currency ISO 4217 code such as {@code TRY}
     * @return this request
     */
    public StoreCardRequest withCurrency(String currency) {
        this.currency = CardRules.currency(currency);
        return this;
    }

    /** @return the body that is sent, a new map: {@code card}, {@code cardUserKey}, {@code callbackUrl}, {@code currency} */
    public Map<String, Object> toBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("card", new LinkedHashMap<>(card));
        if (cardUserKey != null) {
            body.put("cardUserKey", cardUserKey);
        }
        if (callbackUrl != null) {
            body.put("callbackUrl", callbackUrl);
        }
        if (currency != null) {
            body.put("currency", currency);
        }
        return body;
    }

    @Override
    public String toString() {
        return "StoreCardRequest[card=<redacted>, cardUserKey=" + cardUserKey + ", callbackUrl=" + callbackUrl
                + ", currency=" + currency + "]";
    }
}
