package com.lunixi.sdk.insurance;

/**
 * A policy file ({@link InsuranceClient#getPolicyDocument}, {@link InsuranceClient#getPolicyReceipt},
 * {@link InsuranceClient#getPolicyCertificate}): the PDF bytes as the provider returned them. It is not stored at
 * Lunixi and is read from the provider on every call.
 */
public final class InsuranceDocument {

    private final String contentType;
    private final byte[] bytes;

    /**
     * @param contentType the answer's {@code Content-Type} ({@code application/pdf}), or {@code null}
     * @param bytes       the file bytes
     */
    public InsuranceDocument(String contentType, byte[] bytes) {
        this.contentType = contentType;
        this.bytes = bytes == null ? new byte[0] : bytes.clone();
    }

    /** @return the {@code Content-Type} of the answer */
    public String contentType() {
        return contentType;
    }

    /** @return a copy of the file bytes */
    public byte[] bytes() {
        return bytes.clone();
    }

    /** @return the number of bytes */
    public int size() {
        return bytes.length;
    }

    @Override
    public String toString() {
        return "InsuranceDocument[contentType=" + contentType + ", size=" + bytes.length + "]";
    }
}
