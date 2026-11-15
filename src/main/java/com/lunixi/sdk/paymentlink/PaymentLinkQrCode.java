package com.lunixi.sdk.paymentlink;

/** The QR code image of a link ({@link PaymentLinksClient#qr}); it encodes the link URL. */
public final class PaymentLinkQrCode {

    private final String contentType;
    private final byte[] bytes;

    /**
     * @param contentType the answer's {@code Content-Type} ({@code image/svg+xml} or {@code image/png}), or
     *                    {@code null}
     * @param bytes       the image bytes
     */
    public PaymentLinkQrCode(String contentType, byte[] bytes) {
        this.contentType = contentType;
        this.bytes = bytes == null ? new byte[0] : bytes.clone();
    }

    /** @return the {@code Content-Type} of the answer */
    public String contentType() {
        return contentType;
    }

    /** @return a copy of the image bytes */
    public byte[] bytes() {
        return bytes.clone();
    }

    /** @return the number of bytes */
    public int size() {
        return bytes.length;
    }

    @Override
    public String toString() {
        return "PaymentLinkQrCode[contentType=" + contentType + ", size=" + bytes.length + "]";
    }
}
