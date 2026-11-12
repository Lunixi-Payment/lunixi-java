package com.lunixi.sdk.payment;

import java.util.List;

/**
 * Lunixi product category codes for the optional {@code basketItems[].productCategory} field of a gateway body
 * (for example the {@code createIntent} body map).
 *
 * <p>Payment methods with legal installment limits (e.g. shopping loan) and other platform features use them. The
 * gateway answers an unknown code with 400. The dictionary only grows by appending, so the field stays a plain
 * string and the SDK does not check a value against {@link #ALL} before sending: a newer code can be sent before
 * this SDK declares a constant for it.
 */
public final class ProductCategory {

    public static final String MOBILE_PHONE = "MOBILE_PHONE";
    public static final String TABLET = "TABLET";
    public static final String COMPUTER = "COMPUTER";
    public static final String TV_AUDIO = "TV_AUDIO";
    public static final String WHITE_GOODS = "WHITE_GOODS";
    public static final String SMALL_APPLIANCE = "SMALL_APPLIANCE";
    public static final String CAMERA = "CAMERA";
    public static final String GAME_CONSOLE = "GAME_CONSOLE";
    public static final String WEARABLE = "WEARABLE";
    public static final String ELECTRONICS_ACCESSORY = "ELECTRONICS_ACCESSORY";
    public static final String ELECTRONICS_OTHER = "ELECTRONICS_OTHER";
    public static final String GOLD = "GOLD";
    public static final String JEWELRY = "JEWELRY";
    public static final String WATCH = "WATCH";
    public static final String APPAREL = "APPAREL";
    public static final String SHOES_BAGS = "SHOES_BAGS";
    public static final String COSMETICS = "COSMETICS";
    public static final String FURNITURE = "FURNITURE";
    public static final String HOME_LIVING = "HOME_LIVING";
    public static final String GROCERY = "GROCERY";
    public static final String BOOKS_MEDIA = "BOOKS_MEDIA";
    public static final String TOYS = "TOYS";
    public static final String SPORTS_OUTDOOR = "SPORTS_OUTDOOR";
    public static final String PET_SUPPLIES = "PET_SUPPLIES";
    public static final String AUTO_PARTS = "AUTO_PARTS";
    public static final String MUSICAL_INSTRUMENT = "MUSICAL_INSTRUMENT";
    public static final String MICROMOBILITY = "MICROMOBILITY";
    public static final String VEHICLE = "VEHICLE";
    public static final String TRANSPORT_TICKET = "TRANSPORT_TICKET";
    public static final String EVENT_TICKET = "EVENT_TICKET";
    public static final String ACCOMMODATION = "ACCOMMODATION";
    public static final String TRAVEL_PACKAGE = "TRAVEL_PACKAGE";
    public static final String HEALTH_SERVICE = "HEALTH_SERVICE";
    public static final String EDUCATION = "EDUCATION";
    public static final String SHIPPING_SERVICE = "SHIPPING_SERVICE";
    public static final String DIGITAL_GOODS = "DIGITAL_GOODS";
    public static final String GIFT_CARD = "GIFT_CARD";
    public static final String SUBSCRIPTION = "SUBSCRIPTION";
    public static final String SERVICE_OTHER = "SERVICE_OTHER";
    public static final String OTHER = "OTHER";

    /** Every code known to this SDK version, in dictionary order (unmodifiable). */
    public static final List<String> ALL = List.of(
            MOBILE_PHONE,
            TABLET,
            COMPUTER,
            TV_AUDIO,
            WHITE_GOODS,
            SMALL_APPLIANCE,
            CAMERA,
            GAME_CONSOLE,
            WEARABLE,
            ELECTRONICS_ACCESSORY,
            ELECTRONICS_OTHER,
            GOLD,
            JEWELRY,
            WATCH,
            APPAREL,
            SHOES_BAGS,
            COSMETICS,
            FURNITURE,
            HOME_LIVING,
            GROCERY,
            BOOKS_MEDIA,
            TOYS,
            SPORTS_OUTDOOR,
            PET_SUPPLIES,
            AUTO_PARTS,
            MUSICAL_INSTRUMENT,
            MICROMOBILITY,
            VEHICLE,
            TRANSPORT_TICKET,
            EVENT_TICKET,
            ACCOMMODATION,
            TRAVEL_PACKAGE,
            HEALTH_SERVICE,
            EDUCATION,
            SHIPPING_SERVICE,
            DIGITAL_GOODS,
            GIFT_CARD,
            SUBSCRIPTION,
            SERVICE_OTHER,
            OTHER);

    private ProductCategory() {
    }
}
