package com.lunixi.sdk.paymentlink;

/**
 * A published link image ({@link PaymentLinksClient#confirmImageUpload}). Use {@code assetId} as the
 * {@code imageAssetId} of a link or an item.
 *
 * @param assetId the asset id (uuid)
 * @param url     the public image URL, or {@code null} when the public image host is not configured
 * @param state   always {@code PUBLISHED}
 */
public record PaymentLinkImage(String assetId, String url, String state) {

    /** The only state a confirmed image has. */
    public static final String PUBLISHED = "PUBLISHED";
}
