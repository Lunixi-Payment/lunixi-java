package com.lunixi.sdk.paymentlink;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.lunixi.sdk.LunixiClient;
import com.lunixi.sdk.LunixiConfig;
import com.lunixi.sdk.auth.CanonicalRequest;
import com.lunixi.sdk.auth.Ed25519KeyPair;
import com.lunixi.sdk.exception.ConfigurationException;
import com.lunixi.sdk.exception.LunixiApiException;
import com.lunixi.sdk.http.HttpResult;
import com.lunixi.sdk.http.HttpTransport;
import com.lunixi.sdk.support.RecordingTransport;

/** Payment links: bodies, signing, keys, lists, QR code and the three-step image upload. */
class PaymentLinksClientTest {
    static final String KEY = "plink-key-0001";
    static final String GATEWAY = "https://gateway.invalid";
    static final String BASE = GATEWAY + "/api/v1/payments/links";
    static final String LINK = "{\"status\":\"success\",\"code\":\"SUCCESS\",\"data\":{\"id\":\"pl_1\",\"rowVersion\":3}}";
    static final String ASSET = "3f2504e0-4f89-11d3-9a0c-0305e82c3301";
    RecordingTransport transport;
    PaymentLinksClient links;

    @BeforeEach void setup() {
        transport = new RecordingTransport("/api/v1/auth/token", "fixture-only");
        links = LunixiClient.create(LunixiConfig.builder().baseUrl(GATEWAY).keyId("test-key")
                .privateKey(Ed25519KeyPair.generate().privateKeyPem()).transport(transport).maxRetries(0).build())
                .paymentLinks();
    }

    static void assertSigned(RecordingTransport.Call call) {
        assertNotNull(call.headers.get(CanonicalRequest.HEADER_SIGNATURE), call.url);
        assertEquals("test-key", call.headers.get(CanonicalRequest.HEADER_KEY_ID));
        assertNotNull(call.headers.get(CanonicalRequest.HEADER_NONCE));
    }

    void assertNothingSent() { assertTrue(transport.apiCalls().isEmpty(), "no request may be sent"); }

    // ── Values ────────────────────────────────────────────────────────────────────────────────────────────────

    @Test void valuesMatchTheGateway() {
        assertEquals(List.of("SINGLE_USE", "MULTI_USE"), PaymentLinkValues.Usage.ALL);
        assertEquals(List.of("FIXED", "OPEN", "ITEMIZED"), PaymentLinkValues.AmountMode.ALL);
        assertEquals(List.of("ACTIVE", "PAUSED", "COMPLETED", "EXPIRED", "DEACTIVATED", "TAKEN_DOWN"),
                PaymentLinkValues.State.ALL);
        assertEquals(List.of("OPEN", "BOUND", "SUCCEEDED", "FAILED", "EXPIRED", "ABANDONED", "NEEDS_REVIEW"),
                PaymentLinkValues.AttemptState.ALL);
        assertEquals(List.of("TEXT", "NUMBER", "EMAIL", "PHONE", "DATE", "SELECT", "CHECKBOX", "TCKN", "VKN"),
                PaymentLinkValues.CustomFieldType.ALL);
        assertEquals(List.of("image/png", "image/jpeg", "image/webp"), PaymentLinkValues.ImageContentType.ALL);
        assertEquals(List.of("svg", "png"), PaymentLinkValues.QrFormat.ALL);
        assertEquals(List.of("tr", "en"), PaymentLinkValues.Locale.ALL);
        assertEquals(39, PaymentLinkValues.PAYMENT_LINK_SPEC_FIELDS.size());
        assertEquals("usage", PaymentLinkValues.PAYMENT_LINK_SPEC_FIELDS.get(0));
        assertEquals("metadata", PaymentLinkValues.PAYMENT_LINK_SPEC_FIELDS.get(38));
        assertEquals(List.of("usage", "amountMode", "currency"), PaymentLinkValues.PAYMENT_LINK_IMMUTABLE_FIELDS);
    }

    // ── Create ────────────────────────────────────────────────────────────────────────────────────────────────

    @Test void createSendsTheBuilderBodyInOrderWithTheRequiredKey() {
        transport.enqueue(201, LINK);
        Map<String, Object> link = links.create(CreatePaymentLinkRequest
                .fixed(PaymentLinkValues.Usage.SINGLE_USE, "150000", "try", "Invoice 2026-001")
                .withRecipient(Map.of("email", "buyer@example.com"))
                .withExpiresAt(Instant.parse("2026-12-01T00:00:00Z"))
                .withCheckout(true, List.of(), List.of(3, 1))
                .withMetadata(Map.of("order", "o-1")), KEY);
        assertEquals("pl_1", link.get("id"));
        RecordingTransport.Call call = transport.onlyApiCall();
        assertEquals("POST", call.method);
        assertEquals(BASE, call.url);
        assertEquals(KEY, call.headers.get("Idempotency-Key"));
        assertSigned(call);
        assertEquals("{\"usage\":\"SINGLE_USE\",\"amountMode\":\"FIXED\",\"currency\":\"TRY\",\"title\":\"Invoice 2026-001\","
                + "\"amountMinor\":\"150000\",\"recipient\":{\"email\":\"buyer@example.com\"},"
                + "\"expiresAt\":\"2026-12-01T00:00:00Z\",\"checkout\":{\"requireThreeDs\":true,\"paymentMethods\":[],"
                + "\"enabledInstallments\":[1,3]},\"metadata\":{\"order\":\"o-1\"}}", call.body);
    }

    @Test void itemizedLinkCarriesItemBodies() {
        transport.enqueue(201, LINK);
        links.create(CreatePaymentLinkRequest.itemized(PaymentLinkValues.Usage.MULTI_USE, "TRY", "Tickets",
                List.of(PaymentLinkItem.create("Adult", "25000").withQuantityRange(0, 4).withCapacity(null))), KEY);
        assertEquals("{\"usage\":\"MULTI_USE\",\"amountMode\":\"ITEMIZED\",\"currency\":\"TRY\",\"title\":\"Tickets\","
                + "\"items\":[{\"name\":\"Adult\",\"unitPriceMinor\":\"25000\",\"minQuantity\":0,\"maxQuantity\":4,"
                + "\"capacity\":null}]}", transport.onlyApiCall().body);
    }

    @Test void createRulesSendNothing() {
        CreatePaymentLinkRequest request = CreatePaymentLinkRequest.fixed("SINGLE_USE", "100", "TRY", "t");
        assertThrows(ConfigurationException.class, () -> links.create(request, null), "missing key");
        assertThrows(ConfigurationException.class, () -> links.create(request, "short"), "key under 8 characters");
        assertThrows(ConfigurationException.class, () -> links.create(request, "has space key"));
        assertThrows(ConfigurationException.class, () -> links.create(Map.of("usage", "SINGLE_USE", "colour", "red"), KEY));
        assertThrows(ConfigurationException.class, () -> CreatePaymentLinkRequest.fixed("ONCE", "100", "TRY", "t"));
        assertThrows(ConfigurationException.class, () -> CreatePaymentLinkRequest.fixed("SINGLE_USE", "1.5", "TRY", "t"));
        assertThrows(ConfigurationException.class, () -> CreatePaymentLinkRequest.itemized("MULTI_USE", "TRY", "t", List.of()));
        assertThrows(ConfigurationException.class, () -> request.withBuyerFields(Map.of("name", "MAYBE")));
        assertThrows(ConfigurationException.class, () -> request.withRecipient(Map.of("fax", "1")));
        assertThrows(ConfigurationException.class, () -> request.withCheckout(false, List.of(), List.of(2)));
        assertThrows(ConfigurationException.class, () -> request.withImageAssetId("not-a-uuid"));
        assertNothingSent();
    }

    // ── Read, update, actions, send ───────────────────────────────────────────────────────────────────────────

    @Test void listGetAndPagingAreSigned() {
        transport.enqueue(200, "{\"status\":\"success\",\"data\":{\"items\":[{\"id\":\"pl_1\"}]},"
                + "\"pageInfo\":{\"hasMore\":true,\"nextCursor\":\"c2\",\"totalCount\":2}}");
        transport.enqueue(200, LINK);
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("state", List.of("ACTIVE", "PAUSED"));
        filters.put("limit", 10);
        filters.put("createdFrom", Instant.parse("2026-11-01T00:00:00Z"));
        PaymentLinkPage page = links.list(filters);
        links.get("pl 1");
        List<RecordingTransport.Call> calls = transport.apiCalls();
        assertEquals(BASE + "?limit=10&state=ACTIVE%2CPAUSED&createdFrom=2026-11-01T00%3A00%3A00Z", calls.get(0).url);
        assertSigned(calls.get(0));
        assertEquals(BASE + "/pl%201", calls.get(1).url);
        assertSigned(calls.get(1));
        assertEquals(1, page.items().size());
        assertTrue(page.hasMore());
        assertEquals("c2", page.nextCursor().orElseThrow());
        assertEquals(2L, page.totalCount().orElseThrow());
        assertThrows(ConfigurationException.class, () -> links.list(Map.of("colour", "red")));
        assertEquals(2, transport.apiCalls().size());
    }

    @Test void iterateFollowsTheCursor() {
        transport.enqueue(200, "{\"status\":\"success\",\"data\":{\"items\":[{\"id\":\"a\"}]},\"pageInfo\":{\"hasMore\":true,\"nextCursor\":\"c2\"}}");
        transport.enqueue(200, "{\"status\":\"success\",\"data\":{\"items\":[{\"id\":\"b\"}]},\"pageInfo\":{\"hasMore\":false}}");
        List<Object> ids = new ArrayList<>();
        for (Map<String, Object> row : links.iterateAttempts("pl_1", Map.of("limit", 1))) { ids.add(row.get("id")); }
        assertEquals(List.of("a", "b"), ids);
        assertEquals(BASE + "/pl_1/attempts?limit=1", transport.apiCalls().get(0).url);
        assertEquals(BASE + "/pl_1/attempts?limit=1&cursor=c2", transport.apiCalls().get(1).url);
    }

    @Test void updateSendsTheVersionFirstAndRefusesFixedFields() {
        transport.enqueue(200, LINK);
        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("title", "New title");
        changes.put("expiresAt", null);
        links.update("pl_1", 3, changes, KEY);
        RecordingTransport.Call call = transport.onlyApiCall();
        assertEquals("PATCH", call.method);
        assertEquals(BASE + "/pl_1", call.url);
        assertEquals("{\"expectedRowVersion\":3,\"title\":\"New title\",\"expiresAt\":null}", call.body);
        assertEquals(KEY, call.headers.get("Idempotency-Key"));
        assertSigned(call);

        assertThrows(ConfigurationException.class, () -> links.update("pl_1", 3, Map.of("currency", "USD")));
        assertThrows(ConfigurationException.class, () -> links.update("pl_1", 3, Map.of()));
        assertThrows(ConfigurationException.class, () -> links.update("pl_1", 3, Map.of("environment", "LIVE")));
        assertThrows(ConfigurationException.class, () -> links.update("pl_1", -1, Map.of("title", "x")));
        assertEquals(1, transport.apiCalls().size());
    }

    @Test void actionsAndSend() {
        transport.enqueue(200, LINK).enqueue(200, LINK).enqueue(200, "{\"status\":\"success\",\"data\":{\"delivery\":{},\"replayed\":false}}");
        links.pause("pl_1");
        links.deactivate("pl_1", 3L, "sold out", null);
        links.send("pl_1", PaymentLinkValues.DeliveryChannel.EMAIL, Map.of("recipientEmail", "buyer@example.com",
                "locale", "en"), KEY);
        List<RecordingTransport.Call> calls = transport.apiCalls();
        assertEquals(BASE + "/pl_1/pause", calls.get(0).url);
        assertEquals("{}", calls.get(0).body);
        assertNull(calls.get(0).headers.get("Idempotency-Key"));
        assertEquals(BASE + "/pl_1/deactivate", calls.get(1).url);
        assertEquals("{\"expectedRowVersion\":3,\"reason\":\"sold out\"}", calls.get(1).body);
        assertEquals(BASE + "/pl_1/send", calls.get(2).url);
        assertEquals("{\"channel\":\"EMAIL\",\"recipientEmail\":\"buyer@example.com\",\"locale\":\"en\"}", calls.get(2).body);
        assertEquals(KEY, calls.get(2).headers.get("Idempotency-Key"));
        calls.forEach(PaymentLinksClientTest::assertSigned);

        assertThrows(ConfigurationException.class, () -> links.send("pl_1", "EMAIL", null), "send needs a key");
        assertThrows(ConfigurationException.class, () -> links.send("pl_1", "FAX", KEY));
        assertThrows(ConfigurationException.class, () -> links.action("pl_1", "archive", null, null, null));
        assertThrows(ConfigurationException.class, () -> links.pause(" "));
        assertEquals(3, calls.size());
    }

    @Test void qrIsABinarySignedRead() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G'};
        transport.enqueueBinary(200, Map.of("content-type", "image/png"), png);
        PaymentLinkQrCode qr = links.qr("pl_1", PaymentLinkValues.QrFormat.PNG, 256);
        RecordingTransport.Call call = transport.onlyApiCall();
        assertTrue(call.binary);
        assertEquals(BASE + "/pl_1/qr?format=png&size=256", call.url);
        assertSigned(call);
        assertEquals("image/png", qr.contentType());
        assertArrayEquals(png, qr.bytes());
        assertThrows(ConfigurationException.class, () -> links.qr("pl_1", "gif", null));
        assertThrows(ConfigurationException.class, () -> links.qr("pl_1", "svg", 64));
        assertEquals(1, transport.apiCalls().size());
    }

    // ── Image upload ──────────────────────────────────────────────────────────────────────────────────────────

    static String uploadTarget() {
        return "{\"status\":\"success\",\"data\":{\"assetId\":\"" + ASSET + "\",\"uploadUrl\":"
                + "\"https://storage.invalid/bucket/object?signature=abc\",\"uploadMethod\":\"PUT\","
                + "\"uploadHeaders\":{\"Content-Type\":\"image/png\"},\"expiresAt\":\"2026-11-15T12:15:00Z\"}}";
    }

    @Test void uploadImagePutsOnlyTheUploadHeadersToStorage() {
        byte[] image = "png-bytes".getBytes(StandardCharsets.UTF_8);
        transport.enqueue(201, uploadTarget());
        transport.enqueue(200, Map.of(), "");
        transport.enqueue(200, "{\"status\":\"success\",\"data\":{\"assetId\":\"" + ASSET
                + "\",\"url\":\"https://img.invalid/" + ASSET + "\",\"state\":\"PUBLISHED\"}}");
        PaymentLinkImage published = links.uploadImage("logo.png", PaymentLinkValues.ImageContentType.PNG, image);
        assertEquals(ASSET, published.assetId());
        assertEquals(PaymentLinkImage.PUBLISHED, published.state());

        List<RecordingTransport.Call> calls = transport.apiCalls();
        assertEquals(3, calls.size());
        RecordingTransport.Call create = calls.get(0);
        assertEquals(BASE + "/images", create.url);
        assertEquals("{\"fileName\":\"logo.png\",\"contentType\":\"image/png\",\"sizeBytes\":9}", create.body);
        assertSigned(create);

        RecordingTransport.Call put = calls.get(1);
        assertEquals("PUT", put.method);
        assertEquals("https://storage.invalid/bucket/object?signature=abc", put.url);
        assertEquals(Map.of("Content-Type", "image/png"), put.headers, "only uploadHeaders reach the storage host");
        assertNull(put.headers.get("Authorization"));
        assertNull(put.headers.get(CanonicalRequest.HEADER_SIGNATURE));
        assertNull(put.headers.get(CanonicalRequest.HEADER_KEY_ID));
        assertNull(put.headers.get(CanonicalRequest.HEADER_DIGEST));
        assertArrayEquals(image, put.bytes);

        RecordingTransport.Call confirm = calls.get(2);
        assertEquals(BASE + "/images/" + ASSET + "/confirm", confirm.url);
        assertEquals("{}", confirm.body);
        assertSigned(confirm);
    }

    @Test void aFailedStoragePutStopsBeforeConfirmation() {
        transport.enqueue(201, uploadTarget());
        transport.enqueue(403, Map.of(), "<Error><Code>AccessDenied</Code></Error>");
        LunixiApiException error = assertThrows(LunixiApiException.class,
                () -> links.uploadImage("logo.png", "image/png", new byte[]{1, 2, 3}));
        assertEquals(403, error.statusCode());
        assertEquals("AccessDenied", error.code().orElseThrow());
        assertEquals(2, transport.apiCalls().size(), "no confirmation after a failed PUT");
    }

    @Test void anUnusableUploadTargetIsNotUsed() {
        transport.enqueue(201, "{\"status\":\"success\",\"data\":{\"assetId\":\"" + ASSET + "\",\"uploadUrl\":"
                + "\"ftp://storage.invalid/o\",\"uploadMethod\":\"PUT\",\"uploadHeaders\":{}}}");
        assertThrows(LunixiApiException.class, () -> links.uploadImage("logo.png", "image/png", new byte[]{1}));
        assertEquals(1, transport.apiCalls().size(), "nothing is PUT to an unusable target");
    }

    @Test void imageRulesSendNothing() {
        assertThrows(ConfigurationException.class, () -> links.uploadImage("logo.svg", "image/svg+xml", new byte[]{1}));
        assertThrows(ConfigurationException.class, () -> links.uploadImage("logo.png", "image/png", new byte[0]));
        assertThrows(ConfigurationException.class, () -> links.createImageUpload("", "image/png", 10));
        assertThrows(ConfigurationException.class, () -> links.createImageUpload("logo.png", "image/png", 0));
        assertThrows(ConfigurationException.class, () -> links.confirmImageUpload("asset-1"));
        assertNothingSent();
    }

    @Test void aTransportWithoutByteBodiesRefusesTheUpload() {
        HttpTransport textOnly = new HttpTransport() {
            @Override
            public HttpResult send(String method, String url, Map<String, String> headers, String body,
                                   Duration timeout) {
                return transport.send(method, url, headers, body, timeout);
            }
        };
        PaymentLinksClient textLinks = LunixiClient.create(LunixiConfig.builder().baseUrl(GATEWAY).keyId("test-key")
                .privateKey(Ed25519KeyPair.generate().privateKeyPem()).transport(textOnly).maxRetries(0).build())
                .paymentLinks();
        transport.enqueue(201, uploadTarget());
        assertThrows(ConfigurationException.class, () -> textLinks.uploadImage("logo.png", "image/png", new byte[]{1}));
        assertEquals(1, transport.apiCalls().size());
    }
}
