package com.lunixi.sdk.support;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Synthetic order details every payment endpoint requires ({@code OrderDetails}): buyer, billing address, basket. */
public final class OrderFixture {

    /** A complete synthetic buyer, {@code ip} included. */
    public static final Map<String, Object> BUYER = buyer();
    /** A complete synthetic address. */
    public static final Map<String, Object> ADDRESS = address();

    private OrderFixture() {
    }

    /** @return one {@code VIRTUAL} basket item priced at {@code amount} */
    public static List<Map<String, Object>> basket(String amount) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", "SKU-1");
        item.put("name", "Service");
        item.put("category1", "General");
        item.put("itemType", "VIRTUAL");
        item.put("price", amount);
        return List.of(item);
    }

    /** @return {@code body} plus the buyer, the billing address and one virtual item for {@code amount}, a new map */
    public static Map<String, Object> withOrderDetails(Map<String, ?> body, String amount) {
        Map<String, Object> out = new LinkedHashMap<>(body);
        out.put("buyer", BUYER);
        out.put("billingAddress", ADDRESS);
        out.put("basketItems", basket(amount));
        return out;
    }

    /** @return {@code body} plus the buyer and the billing address (card storage: no basket), a new map */
    public static Map<String, Object> withBuyerAndAddress(Map<String, ?> body) {
        Map<String, Object> out = new LinkedHashMap<>(body);
        out.put("buyer", BUYER);
        out.put("billingAddress", ADDRESS);
        return out;
    }

    /** @return the JSON members the order details add to a body, as {@link #withOrderDetails} puts them */
    public static String json(String amount) {
        return "\"buyer\":" + com.lunixi.sdk.json.Json.stringify(BUYER) + ",\"billingAddress\":"
                + com.lunixi.sdk.json.Json.stringify(ADDRESS) + ",\"basketItems\":"
                + com.lunixi.sdk.json.Json.stringify(basket(amount));
    }

    private static Map<String, Object> buyer() {
        Map<String, Object> buyer = new LinkedHashMap<>();
        buyer.put("name", "Ada");
        buyer.put("surname", "Yilmaz");
        buyer.put("identityNumber", "11111111111");
        buyer.put("email", "ada@example.com");
        buyer.put("gsmNumber", "+905350000000");
        buyer.put("city", "Istanbul");
        buyer.put("country", "Turkey");
        buyer.put("zipCode", "34000");
        buyer.put("ip", "203.0.113.10");
        return java.util.Collections.unmodifiableMap(buyer);
    }

    private static Map<String, Object> address() {
        Map<String, Object> address = new LinkedHashMap<>();
        address.put("contactName", "Ada Yilmaz");
        address.put("address", "Example Street 1");
        address.put("city", "Istanbul");
        address.put("country", "Turkey");
        address.put("zipCode", "34000");
        return java.util.Collections.unmodifiableMap(address);
    }
}
