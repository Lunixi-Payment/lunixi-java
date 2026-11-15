package com.lunixi.sdk.paymentlink;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One line of an {@code ITEMIZED} payment link.
 *
 * <p>{@code itemKey} is the line's permanent identity: stock counters hang off it, so it cannot change once the
 * link exists. Leave it out and the server assigns one ({@code it_…}).
 */
public final class PaymentLinkItem {

    private final Map<String, Object> payload = new LinkedHashMap<>();

    private PaymentLinkItem(String name, String unitPriceMinor) {
        payload.put("name", PaymentLinkRules.text(name, "PaymentLinkItem name"));
        payload.put("unitPriceMinor", PaymentLinkRules.minor(unitPriceMinor, "unitPriceMinor"));
    }

    /**
     * @param name           the line's name
     * @param unitPriceMinor the unit price, a canonical minor-unit string such as {@code "15000"}
     * @return a new item
     */
    public static PaymentLinkItem create(String name, String unitPriceMinor) {
        return new PaymentLinkItem(name, unitPriceMinor);
    }

    public PaymentLinkItem withItemKey(String itemKey) {
        payload.put("itemKey", PaymentLinkRules.text(itemKey, "itemKey"));
        return this;
    }

    public PaymentLinkItem withDescription(String description) {
        payload.put("description", String.valueOf(description));
        return this;
    }

    /**
     * Buyer-selectable quantity range of this line; {@code 0} as the minimum makes the line optional.
     *
     * @param minQuantity the smallest quantity
     * @param maxQuantity the largest quantity
     * @return this item
     */
    public PaymentLinkItem withQuantityRange(int minQuantity, int maxQuantity) {
        payload.put("minQuantity", minQuantity);
        payload.put("maxQuantity", maxQuantity);
        return this;
    }

    /**
     * @param capacity units available for this line across all payments, or {@code null} for unlimited
     * @return this item
     */
    public PaymentLinkItem withCapacity(Integer capacity) {
        payload.put("capacity", capacity);
        return this;
    }

    /**
     * @param imageAssetId a published payment-link image of your organisation ({@link PaymentLinksClient#uploadImage}),
     *                     or {@code null} for none
     * @return this item
     */
    public PaymentLinkItem withImageAssetId(String imageAssetId) {
        payload.put("imageAssetId", PaymentLinkRules.assetIdOrNull(imageAssetId, "imageAssetId"));
        return this;
    }

    /**
     * Lunixi product category ({@link com.lunixi.sdk.payment.ProductCategory}). Not checked against the list: the
     * list only grows, and the gateway answers an unknown value with 400.
     *
     * @param productCategory the category code
     * @return this item
     */
    public PaymentLinkItem withProductCategory(String productCategory) {
        payload.put("productCategory", PaymentLinkRules.text(productCategory, "productCategory"));
        return this;
    }

    /** @return the item as it is sent, a new map */
    public Map<String, Object> toBody() {
        return new LinkedHashMap<>(payload);
    }
}
