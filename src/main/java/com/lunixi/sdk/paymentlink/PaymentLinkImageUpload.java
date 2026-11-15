package com.lunixi.sdk.paymentlink;

import java.util.Map;

/**
 * Step 1 of a link image upload ({@link PaymentLinksClient#createImageUpload}): where to PUT the file. Send the
 * bytes to {@code uploadUrl} with exactly {@code uploadHeaders} before {@code expiresAt}, then call
 * {@link PaymentLinksClient#confirmImageUpload}. {@code uploadUrl} is the storage host, not the gateway: send it no
 * access token and no signature.
 *
 * @param assetId       the image's asset id (uuid)
 * @param uploadUrl     the presigned URL
 * @param uploadMethod  always {@code PUT}
 * @param uploadHeaders the headers the URL was signed for
 * @param expiresAt     when the URL expires (ISO 8601), or {@code null}
 */
public record PaymentLinkImageUpload(String assetId, String uploadUrl, String uploadMethod,
                                     Map<String, String> uploadHeaders, String expiresAt) {

    public PaymentLinkImageUpload {
        uploadHeaders = Map.copyOf(uploadHeaders);
    }

    @Override
    public String toString() {
        return "PaymentLinkImageUpload[assetId=" + assetId + ", uploadMethod=" + uploadMethod + ", expiresAt="
                + expiresAt + "]";
    }
}
