package com.lunixi.sdk.payment;

import com.lunixi.sdk.exception.ConfigurationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Order details every payment endpoint requires: the buyer, the billing address
 * and at least one basket item; a basket with a {@code PHYSICAL} item also
 * requires the shipping address. Payment providers do not start a payment
 * without them.
 *
 * <p>The gateway refuses a missing field with {@code payment.order.field_required}
 * and an invalid one with {@code payment.order.field_invalid}; {@code details.field}
 * is the dotted path of the first failing field. The SDK checks the same rule
 * before sending and names the same paths. Same values as the Node SDK's
 * {@code order-details-values.js}, the PHP SDK's {@code Payment\OrderDetails}
 * and the Python SDK's {@code lunixi.payments.order_details}.
 */
public final class OrderDetails {
    /** Order objects required on every payment endpoint. */
    public static final List<String> ORDER_DETAILS_REQUIRED_FIELDS = List.of("buyer", "billingAddress", "basketItems");
    /** {@code buyer} fields that must be non-empty strings ({@code identityNumber}: Turkish ID or passport; {@code ip}: the buyer's browser). */
    public static final List<String> BUYER_REQUIRED_FIELDS = List.of(
        "name", "surname", "identityNumber", "email", "gsmNumber", "city", "country", "zipCode", "ip");
    /**
     * {@code buyer} fields a checkout intent may leave out: the Lunixi form takes
     * the IP from the buyer's browser. A given {@code ip} is still sent and wins.
     */
    public static final List<String> CHECKOUT_INTENT_BUYER_OPTIONAL_FIELDS = List.of("ip");
    /** Fields of {@code billingAddress} and {@code shippingAddress} that must be non-empty strings. */
    public static final List<String> ADDRESS_REQUIRED_FIELDS = List.of("contactName", "address", "city", "country", "zipCode");
    /** Fields of each {@code basketItems[]} entry the gateway requires. */
    public static final List<String> BASKET_ITEM_REQUIRED_FIELDS = List.of("id", "name", "category1", "itemType", "price");
    /** Error code of a missing order field. */
    public static final String FIELD_REQUIRED = "payment.order.field_required";
    /** Error code of an invalid order field. */
    public static final String FIELD_INVALID = "payment.order.field_invalid";

    private OrderDetails() {}

    /**
     * Paths of the missing order fields of a payment body, in the gateway's format
     * ({@code buyer}, {@code buyer.email}, {@code billingAddress.city},
     * {@code shippingAddress}). An empty list means the order details are
     * complete. {@code withBasket = false} checks the buyer and the billing address
     * only; {@code checkoutIntent = true} does not require
     * {@link #CHECKOUT_INTENT_BUYER_OPTIONAL_FIELDS}.
     */
    public static List<String> missing(Map<String, ?> body, boolean withBasket, boolean checkoutIntent) {
        List<String> buyerFields = checkoutIntent
            ? BUYER_REQUIRED_FIELDS.stream().filter(field -> !CHECKOUT_INTENT_BUYER_OPTIONAL_FIELDS.contains(field)).toList()
            : BUYER_REQUIRED_FIELDS;
        List<String> missing = new ArrayList<>();
        missing.addAll(missingFieldsOf(body.get("buyer"), buyerFields, "buyer"));
        missing.addAll(missingFieldsOf(body.get("billingAddress"), ADDRESS_REQUIRED_FIELDS, "billingAddress"));
        if (!withBasket) {
            return missing;
        }
        if (!(body.get("basketItems") instanceof List<?> items) || items.isEmpty()) {
            missing.add("basketItems");
            return missing;
        }
        boolean physical = false;
        for (int index = 0; index < items.size(); index++) {
            if (!(items.get(index) instanceof Map<?, ?> item)) {
                missing.add("basketItems." + index);
                continue;
            }
            for (String field : BASKET_ITEM_REQUIRED_FIELDS) {
                Object value = item.get(field);
                if (value == null || (value instanceof String text && text.isBlank())) {
                    missing.add("basketItems." + index + "." + field);
                }
            }
            physical = physical || "PHYSICAL".equals(item.get("itemType"));
        }
        Object shipping = body.get("shippingAddress");
        if (physical || shipping != null) {
            missing.addAll(missingFieldsOf(shipping, ADDRESS_REQUIRED_FIELDS, "shippingAddress"));
        }
        return missing;
    }

    /** Throws {@link ConfigurationException} naming the missing order fields (see {@link #missing}). */
    public static void assertComplete(Map<String, ?> body, String label, boolean withBasket, boolean checkoutIntent) {
        List<String> missing = missing(body, withBasket, checkoutIntent);
        if (!missing.isEmpty()) {
            throw new ConfigurationException(label + " requires the order details: " + String.join(", ", missing) + ".");
        }
    }

    private static List<String> missingFieldsOf(Object object, List<String> fields, String path) {
        if (!(object instanceof Map<?, ?> map)) {
            return List.of(path);
        }
        List<String> missing = new ArrayList<>();
        for (String field : fields) {
            if (!(map.get(field) instanceof String text) || text.isBlank()) {
                missing.add(path + "." + field);
            }
        }
        return missing;
    }
}
