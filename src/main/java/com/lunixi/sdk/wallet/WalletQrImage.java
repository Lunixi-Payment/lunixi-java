package com.lunixi.sdk.wallet;

/** A wallet QR code as an image ({@link WalletQr#image}): SVG or PNG bytes. */
public final class WalletQrImage {

    private final String contentType;
    private final byte[] bytes;

    /**
     * @param contentType the answer's {@code Content-Type}, or {@code null}
     * @param bytes       the image bytes
     */
    public WalletQrImage(String contentType, byte[] bytes) {
        this.contentType = contentType;
        this.bytes = bytes == null ? new byte[0] : bytes.clone();
    }

    /** @return the {@code Content-Type} of the answer ({@code image/svg+xml} or {@code image/png}) */
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
        return "WalletQrImage[contentType=" + contentType + ", size=" + bytes.length + "]";
    }
}
