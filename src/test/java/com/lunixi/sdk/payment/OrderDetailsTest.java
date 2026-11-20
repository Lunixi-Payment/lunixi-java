package com.lunixi.sdk.payment;

import static org.junit.jupiter.api.Assertions.*;

import com.lunixi.sdk.exception.ConfigurationException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Order details every payment requires (same values as the Node, PHP and Python SDKs). */
final class OrderDetailsTest {
    private static Map<String, Object> complete(String itemType) {
        Map<String, Object> buyer = new LinkedHashMap<>(DurableCommerceFixture.BUYER); buyer.put("ip", "203.0.113.10");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("buyer", buyer);
        body.put("billingAddress", DurableCommerceFixture.ADDRESS);
        body.put("basketItems", List.of(Map.of("id", "SKU-1", "name", "Service", "category1", "General", "itemType", itemType, "price", "1000")));
        return body;
    }

    @Test void aCompleteBodyMissesNothing() {
        assertEquals(List.of(), OrderDetails.missing(complete("VIRTUAL"), true, false));
        assertEquals(List.of("buyer", "billingAddress", "basketItems"), OrderDetails.ORDER_DETAILS_REQUIRED_FIELDS);
    }

    @Test void theMissingFieldsAreNamedByTheirDottedPath() {
        assertEquals(List.of("buyer", "billingAddress", "basketItems"), OrderDetails.missing(Map.of(), true, false));
        Map<String, Object> body = complete("PHYSICAL");
        assertEquals(List.of("shippingAddress"), OrderDetails.missing(body, true, false));
        body.put("shippingAddress", Map.of("city", "Ankara"));
        assertEquals(List.of("shippingAddress.contactName", "shippingAddress.address", "shippingAddress.country", "shippingAddress.zipCode"),
            OrderDetails.missing(body, true, false));
        assertEquals(List.of("buyer", "billingAddress"), OrderDetails.missing(Map.of(), false, false), "card storage: no basket");
    }

    @Test void aCheckoutIntentMayLeaveOutTheBuyerIp() {
        Map<String, Object> body = complete("VIRTUAL");
        Map<String, Object> buyer = new LinkedHashMap<>(DurableCommerceFixture.BUYER);
        body.put("buyer", buyer);
        assertEquals(List.of(), OrderDetails.missing(body, true, true));
        assertEquals(List.of("buyer.ip"), OrderDetails.missing(body, true, false));
        assertEquals(List.of("ip"), OrderDetails.CHECKOUT_INTENT_BUYER_OPTIONAL_FIELDS);
    }

    @Test void assertCompleteNamesEveryMissingField() {
        ConfigurationException refused = assertThrows(ConfigurationException.class,
            () -> OrderDetails.assertComplete(Map.of(), "A payment intent", true, true));
        assertEquals("A payment intent requires the order details: buyer, billingAddress, basketItems.", refused.getMessage());
    }

    @Test void theSnapshotKeepsItsIdentityWithoutTheOrderDetails() {
        OrderSnapshot bare = new OrderSnapshot("m", "o", "1000", "TRY");
        OrderSnapshot full = DurableCommerceFixture.withDetails(bare);
        assertEquals(bare, full);
        assertEquals(bare.fingerprint(), full.fingerprint());
        assertEquals(bare.toMap(), full.toMap());
        assertEquals(Map.of(), bare.orderDetails());
        assertEquals(List.of("buyer", "billingAddress", "basketItems"), List.copyOf(full.orderDetails().keySet()));
        assertThrows(UnsupportedOperationException.class, () -> full.buyer().put("name", "x"));
    }
}
