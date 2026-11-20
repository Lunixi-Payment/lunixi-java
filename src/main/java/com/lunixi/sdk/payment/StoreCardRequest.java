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
 *         StoreCardRequest.create(cardFields, customer.cardUserKey(), "https://merchant.example/cards/return")
 *                 .withBuyer(buyer)
 *                 .withBillingAddress(billingAddress),
 *         persistedOperationKey);
 * </pre>
 *
 * <p>The buyer and the billing address are required ({@link OrderDetails}, no basket); later stored-card charges
 * take the buyer from this first payment. The card fields are sent as given and are never shown in an error
 * message or in {@link #toString()}; neither are the buyer and the address. A rule violation is a
 * {@link ConfigurationException}.
 */
public final class StoreCardRequest {

    private final Map<String, Object> card;
    private final String cardUserKey;
    private final String callbackUrl;
    private String currency;
    private Map<String, Object> buyer;
    private Map<String, Object> billingAddress;

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

    /**
     * @param buyer the card holder as the buyer (required): {@link OrderDetails#BUYER_REQUIRED_FIELDS}
     * @return this request
     */
    public StoreCardRequest withBuyer(Map<String, ?> buyer) {
        if (buyer == null) {
            throw new ConfigurationException("buyer must be a map of the buyer fields.");
        }
        this.buyer = new LinkedHashMap<>(buyer);
        return this;
    }

    /**
     * @param billingAddress the billing address (required): {@link OrderDetails#ADDRESS_REQUIRED_FIELDS}
     * @return this request
     */
    public StoreCardRequest withBillingAddress(Map<String, ?> billingAddress) {
        if (billingAddress == null) {
            throw new ConfigurationException("billingAddress must be a map of the address fields.");
        }
        this.billingAddress = new LinkedHashMap<>(billingAddress);
        return this;
    }

    /**
     * @return the body that is sent, a new map: {@code card}, {@code cardUserKey}, {@code callbackUrl},
     *     {@code currency}, {@code buyer}, {@code billingAddress}
     */
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
        if (buyer != null) {
            body.put("buyer", new LinkedHashMap<>(buyer));
        }
        if (billingAddress != null) {
            body.put("billingAddress", new LinkedHashMap<>(billingAddress));
        }
        return body;
    }

    @Override
    public String toString() {
        return "StoreCardRequest[card=<redacted>, cardUserKey=" + cardUserKey + ", callbackUrl=" + callbackUrl
                + ", currency=" + currency + "]";
    }
}
