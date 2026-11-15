package com.lunixi.sdk.paymentlink;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.lunixi.sdk.exception.ConfigurationException;
import com.lunixi.sdk.exception.LunixiApiException;
import com.lunixi.sdk.http.Envelope;
import com.lunixi.sdk.http.HttpResult;
import com.lunixi.sdk.http.HttpTransport;
import com.lunixi.sdk.internal.ApiClient;
import com.lunixi.sdk.internal.RequestOptions;

/**
 * Payment links ({@code /api/v1/payments/links}).
 *
 * <p>These routes are authenticated by the API key's per-request Ed25519 signature, so every gateway call here is
 * signed, reads included. The merchant and the environment come from the signing key: a TEST key sees and changes
 * only TEST links. The key needs the matching scope ({@code payment-link:create}, {@code :view}, {@code :manage},
 * {@code :send}); a missing scope is 403 {@code API_INSUFFICIENT_SCOPE}.
 *
 * <p>The one request that is not signed is the image PUT of {@link #uploadImage}: it goes to the storage host
 * behind a presigned URL, not to the gateway, through {@link HttpTransport#sendBytes} with only the
 * {@code uploadHeaders}. The access token and the request signature are never sent there.
 *
 * <p>Single-link answers are the envelope's {@code data}; list calls return a {@link PaymentLinkPage}. Rule
 * violations throw {@link ConfigurationException} before anything is sent.
 */
public final class PaymentLinksClient {

    private static final String BASE = "/api/v1/payments/links";
    private static final String IMAGES = BASE + "/images";
    private static final String QR_ACCEPT = "image/svg+xml, image/png, application/json";
    private static final Set<String> UPLOAD_URL_SCHEMES = Set.of("https", "http");
    private static final Set<String> TIME_FIELDS = Set.of("startsAt", "expiresAt", "eventAt");
    private static final Pattern STORAGE_ERROR_CODE = Pattern.compile("<Code>([A-Za-z0-9.]{1,64})</Code>");
    private static final List<String> SEND_OPTIONS = List.of("recipientEmail", "recipientPhone", "locale");
    private static final Set<String> CREATE_FIELDS;
    private static final Set<String> UPDATE_FIELDS = Set.copyOf(PaymentLinkValues.PAYMENT_LINK_SPEC_FIELDS);

    static {
        Set<String> create = new HashSet<>(PaymentLinkValues.PAYMENT_LINK_SPEC_FIELDS);
        create.add("environment");
        CREATE_FIELDS = Collections.unmodifiableSet(create);
    }

    private final ApiClient api;
    private final HttpTransport storage;
    private final Duration storageTimeout;

    /**
     * @param api            the gateway client
     * @param storage        the transport of the image PUT to the storage host (normally the configured transport)
     * @param storageTimeout the timeout of that PUT
     */
    public PaymentLinksClient(ApiClient api, HttpTransport storage, Duration storageTimeout) {
        this.api = api;
        this.storage = storage;
        this.storageTimeout = storageTimeout;
    }

    // ── Links ─────────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Creates a payment link ({@code POST /payments/links}, signed). The {@code Idempotency-Key} is required:
     * repeating the same key with the same body returns the stored link with {@code replayed: true} (HTTP 200)
     * instead of creating a second one; the same key with a different body is 409
     * {@code payment_link.idempotency.conflict}.
     *
     * @param request        the body
     * @param idempotencyKey 8–200 printable ASCII characters
     * @return the link ({@code data})
     */
    public Map<String, Object> create(CreatePaymentLinkRequest request, String idempotencyKey) {
        if (request == null) {
            throw new ConfigurationException("Payment link create needs a CreatePaymentLinkRequest or a body map.");
        }
        return create(request.toBody(), idempotencyKey);
    }

    /**
     * Same as {@link #create(CreatePaymentLinkRequest, String)} for a body map. Only
     * {@link PaymentLinkValues#PAYMENT_LINK_SPEC_FIELDS} and {@code environment} are accepted.
     *
     * @param body           the gateway body
     * @param idempotencyKey 8–200 printable ASCII characters
     * @return the link ({@code data})
     */
    public Map<String, Object> create(Map<String, ?> body, String idempotencyKey) {
        if (body == null) {
            throw new ConfigurationException("Payment link create needs a CreatePaymentLinkRequest or a body map.");
        }
        PaymentLinkRules.assertKeys(body.keySet(), CREATE_FIELDS, "payment link create");
        Map<String, Object> plain = plainBody(body);
        String key = PaymentLinkRules.idempotencyKey("payment link create", idempotencyKey);
        return data(api.request("POST", BASE, plain, signed().idempotencyKey(key).build()));
    }

    /** @return the first page of links, newest first */
    public PaymentLinkPage list() {
        return list(Map.of());
    }

    /**
     * One page of links, newest first ({@code GET /payments/links}, signed). Filters:
     * {@link PaymentLinkValues#LINK_LIST_FILTERS}. {@code state}, {@code usage} and {@code amountMode} take a list
     * (sent as a comma list); {@code createdFrom} / {@code createdTo} bound the half-open window {@code [from, to)};
     * {@code query} matches a short code exactly, or a title / reference prefix.
     *
     * @param filters the filters
     * @return the page
     */
    public PaymentLinkPage list(Map<String, ?> filters) {
        return new PaymentLinkPage(api.request("GET", BASE, null,
                query(filters, PaymentLinkValues.LINK_LIST_FILTERS, "list").build()));
    }

    /**
     * Every link matching the filters, following the cursors page by page.
     *
     * @param filters the filters
     * @return an iterable over the links
     */
    public Iterable<Map<String, Object>> iterate(Map<String, ?> filters) {
        return paginate(this::list, filters);
    }

    /**
     * One link ({@code GET /payments/links/{linkId}}, signed).
     *
     * @param linkId the link id
     * @return the link ({@code data})
     */
    public Map<String, Object> get(String linkId) {
        return data(api.request("GET", linkPath(linkId), null, signed().build()));
    }

    /**
     * Same as {@link #update(String, long, Map, String)} without an {@code Idempotency-Key}.
     *
     * @param linkId             the link id
     * @param expectedRowVersion the link's current {@code rowVersion}
     * @param changes            the changed fields
     * @return the new version of the link ({@code data})
     */
    public Map<String, Object> update(String linkId, long expectedRowVersion, Map<String, ?> changes) {
        return update(linkId, expectedRowVersion, changes, null);
    }

    /**
     * Changes a link and creates a new version of it ({@code PATCH /payments/links/{linkId}}, signed). Only the keys
     * present in {@code changes} are updated; a {@code null} value clears a field and a nested object
     * ({@code recipient}, {@code checkout}, …) is replaced as a whole. {@code usage}, {@code amountMode} and
     * {@code currency} cannot change. If someone changed the link in between, the call is 409
     * {@code payment_link.link.version_conflict}; amount fields are locked (409
     * {@code payment_link.link.amount_locked}) while a payment is in progress or once the link has been paid.
     *
     * @param linkId             the link id
     * @param expectedRowVersion the link's current {@code rowVersion} (non-negative)
     * @param changes            the changed fields
     * @param idempotencyKey     8–200 printable ASCII characters, or {@code null}
     * @return the new version of the link ({@code data})
     */
    public Map<String, Object> update(String linkId, long expectedRowVersion, Map<String, ?> changes,
                                      String idempotencyKey) {
        String path = linkPath(linkId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("expectedRowVersion", rowVersion(expectedRowVersion));
        body.putAll(updateBody(changes));
        return data(api.request("PATCH", path, body,
                signed().idempotencyKey(PaymentLinkRules.optionalIdempotencyKey(idempotencyKey)).build()));
    }

    /** Stops accepting payments until {@link #resume(String)}. */
    public Map<String, Object> pause(String linkId) {
        return action(linkId, "pause", null, null, null);
    }

    /** Same as {@link #pause(String)} with a version check, a reason and an optional {@code Idempotency-Key}. */
    public Map<String, Object> pause(String linkId, Long expectedRowVersion, String reason, String idempotencyKey) {
        return action(linkId, "pause", expectedRowVersion, reason, idempotencyKey);
    }

    /** Accepts payments again. */
    public Map<String, Object> resume(String linkId) {
        return action(linkId, "resume", null, null, null);
    }

    /** Same as {@link #resume(String)} with a version check, a reason and an optional {@code Idempotency-Key}. */
    public Map<String, Object> resume(String linkId, Long expectedRowVersion, String reason, String idempotencyKey) {
        return action(linkId, "resume", expectedRowVersion, reason, idempotencyKey);
    }

    /** Permanently closes the link. It cannot be reopened. */
    public Map<String, Object> deactivate(String linkId) {
        return action(linkId, "deactivate", null, null, null);
    }

    /** Same as {@link #deactivate(String)} with a version check, a reason and an optional {@code Idempotency-Key}. */
    public Map<String, Object> deactivate(String linkId, Long expectedRowVersion, String reason,
                                          String idempotencyKey) {
        return action(linkId, "deactivate", expectedRowVersion, reason, idempotencyKey);
    }

    /**
     * {@code pause}, {@code resume} or {@code deactivate} ({@code POST /payments/links/{linkId}/{action}}, signed).
     * {@code expectedRowVersion} is optional here: send it to fail with 409
     * {@code payment_link.link.version_conflict} if the link changed in between.
     *
     * @param linkId             the link id
     * @param action             one of {@link PaymentLinkValues#LINK_ACTIONS}
     * @param expectedRowVersion the link's current {@code rowVersion}, or {@code null}
     * @param reason             a note, or {@code null}
     * @param idempotencyKey     8–200 printable ASCII characters, or {@code null}
     * @return the link ({@code data})
     */
    public Map<String, Object> action(String linkId, String action, Long expectedRowVersion, String reason,
                                      String idempotencyKey) {
        if (!PaymentLinkValues.LINK_ACTIONS.contains(action)) {
            throw new ConfigurationException("Payment link action must be one of "
                    + String.join(", ", PaymentLinkValues.LINK_ACTIONS) + ".");
        }
        String path = linkPath(linkId) + "/" + action;
        Map<String, Object> body = new LinkedHashMap<>();
        if (expectedRowVersion != null) {
            body.put("expectedRowVersion", rowVersion(expectedRowVersion));
        }
        if (reason != null) {
            body.put("reason", reason);
        }
        return data(api.request("POST", path, body,
                signed().idempotencyKey(PaymentLinkRules.optionalIdempotencyKey(idempotencyKey)).build()));
    }

    /**
     * Same as {@link #send(String, String, Map, String)} to the link's {@code recipient}.
     *
     * @param linkId         the link id
     * @param channel        a {@link PaymentLinkValues.DeliveryChannel} value
     * @param idempotencyKey 8–200 printable ASCII characters
     * @return {@code {delivery, replayed}}
     */
    public Map<String, Object> send(String linkId, String channel, String idempotencyKey) {
        return send(linkId, channel, Map.of(), idempotencyKey);
    }

    /**
     * Sends the link by e-mail or SMS ({@code POST /payments/links/{linkId}/send}, signed). Without
     * {@code recipientEmail} / {@code recipientPhone} the link's {@code recipient} is used. The
     * {@code Idempotency-Key} is required so a retried call cannot send twice. A delivery can be accepted and still
     * fail: check {@code delivery.state} and {@code delivery.failureReason}.
     *
     * @param linkId         the link id
     * @param channel        a {@link PaymentLinkValues.DeliveryChannel} value
     * @param options        {@code recipientEmail}, {@code recipientPhone} and {@code locale}
     *                       ({@link PaymentLinkValues.Locale}); nothing else
     * @param idempotencyKey 8–200 printable ASCII characters
     * @return {@code {delivery, replayed}}
     */
    public Map<String, Object> send(String linkId, String channel, Map<String, String> options,
                                    String idempotencyKey) {
        String path = linkPath(linkId) + "/send";
        Map<String, String> given = options == null ? Map.of() : options;
        PaymentLinkRules.assertKeys(given.keySet(), SEND_OPTIONS, "payment link send");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("channel", PaymentLinkRules.oneOf(channel, PaymentLinkValues.DeliveryChannel.ALL, "Send channel"));
        if (given.get("recipientEmail") != null) {
            body.put("recipientEmail", given.get("recipientEmail"));
        }
        if (given.get("recipientPhone") != null) {
            body.put("recipientPhone", given.get("recipientPhone"));
        }
        if (given.get("locale") != null) {
            body.put("locale", PaymentLinkRules.oneOf(given.get("locale"), PaymentLinkValues.Locale.ALL,
                    "Send locale"));
        }
        String key = PaymentLinkRules.idempotencyKey("payment link send", idempotencyKey);
        return data(api.request("POST", path, body, signed().idempotencyKey(key).build()));
    }

    /** The link's QR code as SVG at the default size. */
    public PaymentLinkQrCode qr(String linkId) {
        return qr(linkId, PaymentLinkValues.QrFormat.SVG, null);
    }

    /**
     * The link's QR code as an image ({@code GET /payments/links/{linkId}/qr}, signed); it encodes the link URL.
     * Needs a transport that reads binary answers ({@link HttpTransport#sendBinary}).
     *
     * @param linkId the link id
     * @param format {@link PaymentLinkValues.QrFormat#SVG} or {@code PNG}
     * @param size   the size in pixels, 128–2048, or {@code null} for the default (512)
     * @return the image
     */
    public PaymentLinkQrCode qr(String linkId, String format, Integer size) {
        String path = linkPath(linkId) + "/qr";
        RequestOptions.Builder options = signed().query("format",
                PaymentLinkRules.oneOf(format, PaymentLinkValues.QrFormat.ALL, "QR format"));
        if (size != null) {
            if (size < PaymentLinkValues.QR_MIN_SIZE || size > PaymentLinkValues.QR_MAX_SIZE) {
                throw new ConfigurationException("QR size must be an integer in pixels, "
                        + PaymentLinkValues.QR_MIN_SIZE + "-" + PaymentLinkValues.QR_MAX_SIZE + ".");
            }
            options.query("size", size);
        }
        ApiClient.Binary image = api.requestBinary("GET", path, QR_ACCEPT, options.build());
        return new PaymentLinkQrCode(image.contentType(), image.bytes());
    }

    // ── Attempts and payments ─────────────────────────────────────────────────────────────────────────────────

    /** @return the first page of payment attempts on the link, newest first */
    public PaymentLinkPage listAttempts(String linkId) {
        return listAttempts(linkId, Map.of());
    }

    /**
     * Payment attempts on the link, newest first ({@code GET /payments/links/{linkId}/attempts}, signed). Filters:
     * {@link PaymentLinkValues#ATTEMPT_LIST_FILTERS}; {@code state} takes a list.
     *
     * @param linkId  the link id
     * @param filters the filters
     * @return the page
     */
    public PaymentLinkPage listAttempts(String linkId, Map<String, ?> filters) {
        String path = linkPath(linkId) + "/attempts";
        return new PaymentLinkPage(api.request("GET", path, null,
                query(filters, PaymentLinkValues.ATTEMPT_LIST_FILTERS, "listAttempts").build()));
    }

    /** Every attempt on the link matching the filters, following the cursors. */
    public Iterable<Map<String, Object>> iterateAttempts(String linkId, Map<String, ?> filters) {
        return paginate(pageFilters -> listAttempts(linkId, pageFilters), filters);
    }

    /** @return the first page of successful payments on the link */
    public PaymentLinkPage listPayments(String linkId) {
        return listPayments(linkId, Map.of());
    }

    /**
     * Successful payments on the link, with refund totals ({@code GET /payments/links/{linkId}/payments}, signed).
     * Filters: {@link PaymentLinkValues#PAYMENT_LIST_FILTERS}.
     *
     * @param linkId  the link id
     * @param filters the filters
     * @return the page
     */
    public PaymentLinkPage listPayments(String linkId, Map<String, ?> filters) {
        String path = linkPath(linkId) + "/payments";
        return new PaymentLinkPage(api.request("GET", path, null,
                query(filters, PaymentLinkValues.PAYMENT_LIST_FILTERS, "listPayments").build()));
    }

    /** Every payment on the link matching the filters, following the cursors. */
    public Iterable<Map<String, Object>> iteratePayments(String linkId, Map<String, ?> filters) {
        return paginate(pageFilters -> listPayments(linkId, pageFilters), filters);
    }

    // ── Images ────────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Link image, step 1 of 3: an upload URL for a PNG, JPEG or WebP image ({@code POST /payments/links/images},
     * signed, scope {@code payment-link:create}). Step 2 is a PUT of the file's bytes to {@code uploadUrl} with
     * exactly {@code uploadHeaders}, before {@code expiresAt}; step 3 is {@link #confirmImageUpload}.
     * {@link #uploadImage} runs all three. A declared size over the image limit (2 MiB) is refused by the gateway
     * (400 {@code media.public_asset.too_large}) before anything is uploaded.
     *
     * @param fileName    recorded only (1–255 characters)
     * @param contentType a {@link PaymentLinkValues.ImageContentType} value (SVG is not accepted)
     * @param sizeBytes   the file size in bytes (positive)
     * @return the upload target
     * @throws LunixiApiException when the gateway answer is not a usable upload target
     */
    public PaymentLinkImageUpload createImageUpload(String fileName, String contentType, long sizeBytes) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fileName", PaymentLinkRules.text(fileName, "fileName").trim());
        body.put("contentType", PaymentLinkRules.oneOf(contentType, PaymentLinkValues.ImageContentType.ALL,
                "Image contentType"));
        if (sizeBytes < 1) {
            throw new ConfigurationException("Image sizeBytes must be the file size in bytes (a positive integer); "
                    + "got " + sizeBytes + ".");
        }
        body.put("sizeBytes", sizeBytes);
        return uploadTarget(data(api.request("POST", IMAGES, body, signed().build())));
    }

    /**
     * Link image, step 3 of 3: publishes an uploaded image ({@code POST /payments/links/images/{assetId}/confirm},
     * signed). The stored file must be the declared type (PNG, JPEG or WebP), at most 2 MiB and 4096×4096 px; a file
     * that fails is deleted (400) and has to be uploaded again from {@link #createImageUpload}. Confirming before
     * the PUT has landed is 400 {@code media.confirm.object_missing} and can be retried.
     *
     * @param assetId the asset id from {@link #createImageUpload}
     * @return the published image
     * @throws LunixiApiException when the answer is not a published image
     */
    public PaymentLinkImage confirmImageUpload(String assetId) {
        String path = IMAGES + "/" + encode(assetId(assetId)) + "/confirm";
        Map<String, Object> image = data(api.request("POST", path, new LinkedHashMap<>(), signed().build()));
        if (!(image.get("assetId") instanceof String id) || !PaymentLinkImage.PUBLISHED.equals(image.get("state"))) {
            throw new LunixiApiException("Image confirmation did not return a published image.", 0, null, image,
                    null);
        }
        Object url = image.get("url");
        return new PaymentLinkImage(id, url instanceof String text ? text : null, PaymentLinkImage.PUBLISHED);
    }

    /**
     * Uploads a link image in one call: {@link #createImageUpload}, the PUT of {@code content} to the returned
     * {@code uploadUrl}, then {@link #confirmImageUpload}. The PUT goes straight to the storage host with only
     * {@code uploadHeaders} through {@link HttpTransport#sendBytes}; it does not go through the gateway client, so
     * the access token and the request signature are never sent there. A failed PUT is not retried: it throws
     * {@link LunixiApiException} with the storage host's HTTP status (and its error code, e.g. {@code AccessDenied}
     * once the URL has expired); call {@code uploadImage} again for a new upload URL.
     *
     * @param fileName    recorded only (1–255 characters)
     * @param contentType a {@link PaymentLinkValues.ImageContentType} value
     * @param content     the file bytes (not empty)
     * @return the published image
     */
    public PaymentLinkImage uploadImage(String fileName, String contentType, byte[] content) {
        if (content == null || content.length == 0) {
            throw new ConfigurationException("Image content must be a non-empty byte array.");
        }
        byte[] bytes = content.clone();
        PaymentLinkImageUpload target = createImageUpload(fileName, contentType, bytes.length);
        putToStorage(target, bytes);
        return confirmImageUpload(target.assetId());
    }

    // ── Internals ─────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * The PUT to the presigned upload URL. It deliberately does NOT use {@link ApiClient}: that adds the bearer
     * access token and the Ed25519 signature headers, which must never reach the storage host (a presigned URL
     * also rejects a request that carries an {@code Authorization} header). Only the signed-for headers are sent.
     */
    private void putToStorage(PaymentLinkImageUpload target, byte[] bytes) {
        HttpResult response;
        try {
            response = storage.sendBytes("PUT", target.uploadUrl(), new LinkedHashMap<>(target.uploadHeaders()),
                    bytes, storageTimeout);
        } catch (LunixiApiException transportFailure) {
            throw new LunixiApiException("Image upload to storage failed: " + transportFailure.getMessage(),
                    transportFailure);
        }
        if (!response.isHttpSuccess()) {
            Matcher match = STORAGE_ERROR_CODE.matcher(response.body());
            String code = match.find() ? match.group(1) : null;
            throw new LunixiApiException("Image upload to storage failed (HTTP " + response.statusCode()
                    + (code == null ? "" : ", " + code) + ").", response.statusCode(), code, Map.of(), null);
        }
    }

    /**
     * An upload target is used only if the SDK can PUT to it as the contract describes: an asset id, an http(s)
     * URL, the PUT method and string headers. Anything else fails before a byte is sent.
     */
    private static PaymentLinkImageUpload uploadTarget(Map<String, Object> target) {
        Object assetId = target.get("assetId");
        Object uploadUrl = target.get("uploadUrl");
        Object headers = target.get("uploadHeaders");
        boolean usable = assetId instanceof String id && PaymentLinkValues.ASSET_ID_PATTERN.matcher(id).matches()
                && uploadUrl instanceof String url && isUploadUrl(url)
                && "PUT".equals(target.get("uploadMethod"))
                && headers instanceof Map<?, ?> map && map.entrySet().stream()
                        .allMatch(entry -> entry.getKey() instanceof String && entry.getValue() instanceof String);
        if (!usable) {
            throw new LunixiApiException("Image upload creation did not return a usable upload target.", 0, null,
                    target, null);
        }
        Map<String, String> uploadHeaders = new LinkedHashMap<>();
        ((Map<?, ?>) headers).forEach((name, value) -> uploadHeaders.put((String) name, (String) value));
        Object expiresAt = target.get("expiresAt");
        return new PaymentLinkImageUpload((String) assetId, (String) uploadUrl, "PUT", uploadHeaders,
                expiresAt instanceof String text ? text : null);
    }

    private static boolean isUploadUrl(String value) {
        try {
            URI uri = URI.create(value);
            return uri.getScheme() != null && UPLOAD_URL_SCHEMES.contains(uri.getScheme().toLowerCase(java.util.Locale.ROOT))
                    && uri.getHost() != null;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private static Map<String, Object> updateBody(Map<String, ?> changes) {
        if (changes == null) {
            throw new ConfigurationException("Payment link changes must be a map.");
        }
        if (changes.isEmpty()) {
            throw new ConfigurationException("Payment link update needs at least one changed field.");
        }
        for (String key : changes.keySet()) {
            if (PaymentLinkValues.PAYMENT_LINK_IMMUTABLE_FIELDS.contains(key)) {
                throw new ConfigurationException("'" + key + "' is fixed when a payment link is created and cannot "
                        + "be updated. Create a new link instead.");
            }
        }
        PaymentLinkRules.assertKeys(changes.keySet(), UPDATE_FIELDS, "payment link update");
        return plainBody(changes);
    }

    /** A copy with builder parts turned into maps and the time fields as ISO 8601; {@code null} values are kept. */
    private static Map<String, Object> plainBody(Map<String, ?> body) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : body.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (TIME_FIELDS.contains(key)) {
                out.put(key, PaymentLinkRules.isoOrNull(value, key));
            } else if (value instanceof List<?> list) {
                out.put(key, list.stream().map(PaymentLinksClient::plain).toList());
            } else {
                out.put(key, plain(value));
            }
        }
        return out;
    }

    private static Object plain(Object value) {
        if (value instanceof PaymentLinkItem item) {
            return item.toBody();
        }
        if (value instanceof PaymentLinkCustomField field) {
            return field.toBody();
        }
        return value;
    }

    private static RequestOptions.Builder query(Map<String, ?> filters, List<String> allowed, String operation) {
        RequestOptions.Builder options = signed();
        if (filters == null) {
            return options;
        }
        PaymentLinkRules.assertKeys(filters.keySet(), allowed, "payment link " + operation + " filter");
        for (String key : allowed) {
            Object value = filters.get(key);
            if (value == null || "".equals(value) || (value instanceof List<?> list && list.isEmpty())) {
                continue;
            }
            options.query(key, PaymentLinkRules.queryValue(key, value));
        }
        return options;
    }

    private static Iterable<Map<String, Object>> paginate(Function<Map<String, Object>, PaymentLinkPage> fetch,
                                                          Map<String, ?> filters) {
        Map<String, Object> seed = filters == null ? Map.of() : new LinkedHashMap<>(filters);
        return () -> new Iterator<>() {
            private String cursor = seed.get("cursor") instanceof String text ? text : null;
            private Iterator<Map<String, Object>> page = Collections.emptyIterator();
            private boolean exhausted;

            @Override
            public boolean hasNext() {
                while (!page.hasNext() && !exhausted) {
                    Map<String, Object> pageFilters = new LinkedHashMap<>(seed);
                    pageFilters.remove("cursor");
                    if (cursor != null) {
                        pageFilters.put("cursor", cursor);
                    }
                    PaymentLinkPage fetched = fetch.apply(pageFilters);
                    page = fetched.items().iterator();
                    if (!fetched.hasMore() || fetched.nextCursor().isEmpty()) {
                        exhausted = true;
                    } else {
                        cursor = fetched.nextCursor().get();
                    }
                }
                return page.hasNext();
            }

            @Override
            public Map<String, Object> next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return page.next();
            }
        };
    }

    private static RequestOptions.Builder signed() {
        return RequestOptions.builder().stepUp(true);
    }

    private static Map<String, Object> data(Map<String, Object> response) {
        return Envelope.data(response);
    }

    private static String linkPath(String linkId) {
        return BASE + "/" + encode(PaymentLinkRules.text(linkId, "linkId").trim());
    }

    private static long rowVersion(long value) {
        if (value < 0) {
            throw new ConfigurationException("expectedRowVersion must be the link's current rowVersion (a "
                    + "non-negative integer).");
        }
        return value;
    }

    private static String assetId(String value) {
        String id = value == null ? "" : value.trim();
        if (!PaymentLinkValues.ASSET_ID_PATTERN.matcher(id).matches()) {
            throw new ConfigurationException("assetId must be the image's asset id (uuid); got "
                    + PaymentLinkRules.quote(value) + ".");
        }
        return id;
    }

    private static String encode(String segment) {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
