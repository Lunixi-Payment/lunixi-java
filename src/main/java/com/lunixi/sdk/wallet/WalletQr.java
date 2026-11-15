package com.lunixi.sdk.wallet;

import java.util.Map;

import com.lunixi.sdk.exception.ConfigurationException;
import com.lunixi.sdk.http.Envelope;
import com.lunixi.sdk.internal.ApiClient;
import com.lunixi.sdk.internal.CursorPages;

/** Wallet QR codes ({@code client.wallet().qr()}). */
public final class WalletQr {

    /** Smallest {@code scale} of {@link #image}. */
    public static final int IMAGE_SCALE_MIN = 1;
    /** Largest {@code scale} of {@link #image}. */
    public static final int IMAGE_SCALE_MAX = 20;

    private final WalletInvoker invoker;

    WalletQr(WalletInvoker invoker) {
        this.invoker = invoker;
    }

    /**
     * Creates a QR code ({@code POST /wallet/qr}). Static codes carry no amount; dynamic ones require it.
     *
     * @param request        {@code qrType} ({@link WalletValues.QrType}), {@code currency}; optional
     *                       {@code targetEndUserId}, {@code targetExternalCustomerId}, {@code programId},
     *                       {@code amount}, {@code expiresInSeconds}, {@code metadata}
     * @param idempotencyKey required
     * @return the QR code ({@code data})
     */
    public Map<String, Object> create(Map<String, ?> request, String idempotencyKey) {
        return Envelope.data(invoker.call("createQr", Map.of(), null, request, idempotencyKey));
    }

    /** @return the first page of QR codes */
    public WalletPage list() {
        return list(Map.of());
    }

    /**
     * QR codes ({@code GET /wallet/qr}). Filters: {@code programId}, {@code qrType}, {@code status},
     * {@code targetEndUserId}, {@code limit}, {@code cursor}.
     *
     * @param filters the filters
     * @return the page
     */
    public WalletPage list(Map<String, ?> filters) {
        return new WalletPage(invoker.call("listQr", Map.of(), filters, null, null));
    }

    /** Every QR code matching the filters, following the cursors. */
    public Iterable<Map<String, Object>> iterate(Map<String, ?> filters) {
        return CursorPages.iterate(filters, this::list, WalletPage::items, page -> page.nextCursor().orElse(null));
    }

    /** @return one QR code ({@code GET /wallet/qr/{qrId}}) */
    public Map<String, Object> get(String qrId) {
        return Envelope.data(invoker.call("getQr", WalletInvoker.path("qrId", qrId)));
    }

    /** @return the QR code as SVG at the default scale */
    public WalletQrImage image(String qrId) {
        return image(qrId, null, null);
    }

    /**
     * The QR code as an image ({@code GET /wallet/qr/{qrId}/image}). Needs a transport that reads binary answers
     * ({@link com.lunixi.sdk.http.HttpTransport#sendBinary}).
     *
     * @param qrId   the QR id
     * @param format {@link WalletValues.QrImageFormat#SVG} (the gateway default) or {@code PNG}, or {@code null}
     * @param scale  1–20, or {@code null}
     * @return the image
     */
    public WalletQrImage image(String qrId, String format, Integer scale) {
        if (format != null && !WalletValues.QrImageFormat.ALL.contains(format)) {
            throw new ConfigurationException("QR image format must be one of "
                    + String.join(", ", WalletValues.QrImageFormat.ALL) + ".");
        }
        if (scale != null && (scale < IMAGE_SCALE_MIN || scale > IMAGE_SCALE_MAX)) {
            throw new ConfigurationException("QR image scale must be an integer " + IMAGE_SCALE_MIN + "-"
                    + IMAGE_SCALE_MAX + ".");
        }
        ApiClient.Binary image = invoker.binary("getQrImage", WalletInvoker.path("qrId", qrId),
                WalletInvoker.compact("format", format, "scale", scale));
        return new WalletQrImage(image.contentType(), image.bytes());
    }

    /** Revokes a QR code ({@code POST /wallet/qr/{qrId}/revoke}). */
    public Map<String, Object> revoke(String qrId) {
        return Envelope.data(invoker.call("revokeQr", WalletInvoker.path("qrId", qrId)));
    }

    /** Decodes a scanned QR payload ({@code POST /wallet/qr/parse}). */
    public Map<String, Object> parse(String payload) {
        return Envelope.data(invoker.call("parseQr", Map.of(), null, WalletInvoker.compact("payload", payload),
                null));
    }
}
