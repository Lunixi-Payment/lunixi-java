package com.lunixi.sdk.payment;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ProductCategoryTest {
    private static final List<String> EXPECTED = List.of(
            "MOBILE_PHONE",
            "TABLET",
            "COMPUTER",
            "TV_AUDIO",
            "WHITE_GOODS",
            "SMALL_APPLIANCE",
            "CAMERA",
            "GAME_CONSOLE",
            "WEARABLE",
            "ELECTRONICS_ACCESSORY",
            "ELECTRONICS_OTHER",
            "GOLD",
            "JEWELRY",
            "WATCH",
            "APPAREL",
            "SHOES_BAGS",
            "COSMETICS",
            "FURNITURE",
            "HOME_LIVING",
            "GROCERY",
            "BOOKS_MEDIA",
            "TOYS",
            "SPORTS_OUTDOOR",
            "PET_SUPPLIES",
            "AUTO_PARTS",
            "MUSICAL_INSTRUMENT",
            "MICROMOBILITY",
            "VEHICLE",
            "TRANSPORT_TICKET",
            "EVENT_TICKET",
            "ACCOMMODATION",
            "TRAVEL_PACKAGE",
            "HEALTH_SERVICE",
            "EDUCATION",
            "SHIPPING_SERVICE",
            "DIGITAL_GOODS",
            "GIFT_CARD",
            "SUBSCRIPTION",
            "SERVICE_OTHER",
            "OTHER");

    @Test void allListsTheFortyCodesInDictionaryOrder() {
        assertEquals(EXPECTED, ProductCategory.ALL);
        assertEquals(40, new HashSet<>(ProductCategory.ALL).size());
        assertEquals("MOBILE_PHONE", ProductCategory.MOBILE_PHONE);
        assertEquals("OTHER", ProductCategory.OTHER);
        assertThrows(UnsupportedOperationException.class, () -> ProductCategory.ALL.add("NEW_CODE"));
    }
}
