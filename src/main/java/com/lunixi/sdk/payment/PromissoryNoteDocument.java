package com.lunixi.sdk.payment;

/**
 * The printable notes of a Senetle Ödeme payment, one note per page
 * ({@link PaymentsClient#downloadPromissoryNoteDocument}). Built from the provider's current data and not stored by
 * Lunixi: download it when you need it.
 */
public final class PromissoryNoteDocument {

    private final String contentType;
    private final byte[] bytes;

    /**
     * @param contentType the answer's {@code Content-Type} ({@code application/pdf}), or {@code null}
     * @param bytes       the document bytes
     */
    public PromissoryNoteDocument(String contentType, byte[] bytes) {
        this.contentType = contentType;
        this.bytes = bytes == null ? new byte[0] : bytes.clone();
    }

    /** @return the {@code Content-Type} of the answer, normally {@code application/pdf} */
    public String contentType() {
        return contentType;
    }

    /** @return a copy of the PDF bytes */
    public byte[] bytes() {
        return bytes.clone();
    }

    /** @return the number of bytes */
    public int size() {
        return bytes.length;
    }

    @Override
    public String toString() {
        return "PromissoryNoteDocument[contentType=" + contentType + ", size=" + bytes.length + "]";
    }
}
