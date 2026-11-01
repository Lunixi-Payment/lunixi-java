package com.lunixi.sdk.payment;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.lunixi.sdk.LunixiClient;
import com.lunixi.sdk.LunixiConfig;
import com.lunixi.sdk.auth.CanonicalRequest;
import com.lunixi.sdk.auth.Ed25519KeyPair;
import com.lunixi.sdk.auth.Ed25519Signer;
import com.lunixi.sdk.support.RecordingTransport;
import com.lunixi.sdk.json.Json;

class PaymentsTest {
    RecordingTransport transport;
    LunixiClient client;
    Ed25519KeyPair keys;
    static final String KEY = "persisted-operation-key-1";
    @BeforeEach void setup() {
        keys = Ed25519KeyPair.generate(); transport = new RecordingTransport("/api/v1/auth/token", "fixture-only");
        client = LunixiClient.create(LunixiConfig.builder().baseUrl("https://gateway.invalid").keyId("test-key").privateKey(keys.privateKeyPem()).transport(transport).maxRetries(0).build());
    }
    Map<String, Object> request() { return Map.of("amount", "1990", "currency", "TRY", "orderId", "order-1", "callbackUrl", "https://shop.invalid/return"); }
    @Test void signsTheExactIntentBodyAndReadsActualPaymentIdToken() throws Exception {
        transport.enqueue(201, "{\"data\":{\"paymentId\":\"pi-1\",\"token\":\"embedded-only-token\",\"paymentPageUrl\":\"https://pay.lunixi.com/payment/hosted-origin-token-012345\"}}");
        CheckoutSession result = client.payments().createIntent(request(), KEY);
        var call = transport.onlyApiCall(); String path = "/api/v1/payments/intents";
        assertEquals("https://gateway.invalid" + path, call.url); assertEquals(KEY, call.headers.get("Idempotency-Key"));
        assertEquals(CanonicalRequest.digestForBody(call.body), call.headers.get(CanonicalRequest.HEADER_DIGEST));
        String canonical = CanonicalRequest.build("POST", path, call.headers.get(CanonicalRequest.HEADER_DATE), call.headers.get(CanonicalRequest.HEADER_NONCE), call.headers.get(CanonicalRequest.HEADER_DIGEST));
        Signature verifier = Signature.getInstance("Ed25519"); verifier.initVerify(Ed25519Signer.publicKeyFrom(keys.publicKeyPem())); verifier.update(canonical.getBytes(StandardCharsets.UTF_8));
        assertTrue(verifier.verify(Base64.getDecoder().decode(call.headers.get(CanonicalRequest.HEADER_SIGNATURE))));
        assertEquals("pi-1", result.paymentId()); assertEquals("embedded-only-token", result.token());
        assertEquals("https://pay.lunixi.com/payment/hosted-origin-token-012345", result.hostedUrl());
        assertFalse(result.toString().contains("embedded-only-token")); assertFalse(result.toString().contains("hosted-origin-token"));
    }
    @Test void oldResponsesAndTwoArgumentConstructionStillEmbedButNeverGuessAHostedUrl() {
        transport.enqueue(201, "{\"data\":{\"paymentId\":\"pi-1\",\"token\":\"merchant-token\"}}");
        CheckoutSession legacy = client.payments().createIntent(request(), KEY);
        assertEquals("merchant-token", legacy.token()); assertNull(legacy.paymentPageUrl());
        assertThrows(IllegalStateException.class, legacy::hostedUrl);
        assertThrows(IllegalStateException.class, new CheckoutSession("pi-1", "legacy-token")::hostedUrl);
    }
    @Test void rejectsUntrustedMalformedOrNonOpaqueHostedUrls() {
        for (String invalid : new String[]{
                "http://pay.lunixi.com/payment/t", "https://pay.lunixi.com.evil.example/payment/t",
                "https://pay.lunixi.com@evil.example/payment/t", "https://user@pay.lunixi.com/payment/t",
                "https://pay.lunixi.com:444/payment/t", "https://pay.lunixi.com/payment/t?next=evil",
                "https://pay.lunixi.com/payment/t#fragment", "https://pay.lunixi.com/payment/one/two",
                "https://pay.lunixi.com/payment/", "https://pay.lunixi.com/payment/%xx",
                "https://pay.lunixi.com/payment/..", "https://pay.lunixi.com/payment/%2e%2E",
                "https://pay.lunixi.com/payment/t\r\nLocation:evil",
                "https://pay.lunixi.com/payment/0123456789abcdef\n",
                "https://pay.lunixi.com/payment/0123456789abcdef%2Fanother",
                "https://pay.lunixi.com/payment/0123456789abcdef%0A",
                "https://pay.lunixi.com/payment/" + "t".repeat(4097)}) {
            assertThrows(IllegalArgumentException.class, () -> new CheckoutSession("pi-1", "embedded", invalid), invalid);
        }
        transport.enqueue(201, "{\"data\":{\"paymentId\":\"pi-1\",\"token\":\"merchant-token\",\"paymentPageUrl\":123}}");
        assertThrows(IllegalStateException.class, () -> client.payments().createIntent(request(), KEY));
    }
    @Test void refusesInvalidMoneyKeysCallbacksAndConflictingCreateIds() {
        assertThrows(IllegalArgumentException.class, () -> client.payments().createIntent(request(), "short"));
        assertThrows(IllegalArgumentException.class, () -> client.payments().createIntent(Map.of("amount", 19.90, "currency", "TRY", "orderId", "order"), KEY));
        var wrong = new java.util.LinkedHashMap<>(request()); wrong.put("callbackUrl", "http://shop.invalid/return");
        assertThrows(IllegalArgumentException.class, () -> client.payments().createIntent(wrong, KEY)); assertTrue(transport.calls().isEmpty());
        var legacy = new java.util.LinkedHashMap<>(request()); legacy.put("paidPrice", 19.90);
        assertThrows(IllegalArgumentException.class, () -> client.payments().createIntent(legacy, KEY)); assertTrue(transport.calls().isEmpty());
        transport.enqueue(201, "{\"data\":{\"paymentId\":\"pi-1\",\"intentId\":\"other\",\"token\":\"browser\"}}");
        assertThrows(IllegalStateException.class, () -> client.payments().createIntent(request(), KEY));
    }
    @Test void keepsSettlementDetailsAndEncodesEachResourceIdAsOnePathSegment() {
        transport.enqueue(200, "{\"data\":{\"id\":\"a/b ?\"},\"details\":{\"capturedAmount\":\"1990\",\"refundedAmount\":\"0\"}}");
        var response = client.payments().get("a/b ?");
        assertTrue(transport.onlyApiCall().url.endsWith("/a%2Fb%20%3F")); assertTrue(response.containsKey("details"));
        assertThrows(IllegalArgumentException.class, () -> client.payments().get(".."));
    }
    @Test void financialWritesRequireStableKeysAndExplicitMinorUnits() {
        transport.enqueue(202, "{\"data\":{\"status\":\"PROCESSING\"}}"); client.payments().refund("pi/1", "990", "Customer refund", KEY);
        var call = transport.onlyApiCall(); assertTrue(call.url.endsWith("/pi%2F1/refund")); assertEquals("990", Json.parseObject(call.body).get("amount")); assertEquals("pi/1", Json.parseObject(call.body).get("paymentIntentId"));
        assertNotNull(call.headers.get(CanonicalRequest.HEADER_SIGNATURE));
        assertThrows(IllegalArgumentException.class, () -> client.payments().capture("pi-1", "-1", KEY));
        assertThrows(IllegalArgumentException.class, () -> client.payments().voidPayment("pi-1", "header\r\ninjection"));
    }
    @Test void explicitNullNeverBecomesRemainingCaptureOrRefund() {
        assertThrows(IllegalArgumentException.class, () -> client.payments().capture("pi-1", null, KEY));
        assertThrows(IllegalArgumentException.class, () -> client.payments().refund("pi-1", null, null, KEY));
        var api = new com.lunixi.sdk.internal.ApiClient(client.config(), new Ed25519Signer(client.config().privateKey()), client.tokens());
        var body = new java.util.LinkedHashMap<String, Object>(); body.put("amount", null);
        for (String operation : new String[]{"capture", "refund"}) {
            assertThrows(IllegalArgumentException.class, () -> api.request("POST", "/api/v1/payments/pi-1/" + operation, body,
                com.lunixi.sdk.internal.RequestOptions.builder().stepUp(true).idempotencyKey(KEY).build()));
        }
        assertTrue(transport.calls().isEmpty());
        transport.enqueue(200, "{\"data\":{\"status\":\"PROCESSING\"}}");
        client.payments().captureRemaining("pi-1", KEY);
        transport.enqueue(200, "{\"data\":{\"status\":\"PROCESSING\"}}");
        client.payments().refundRemaining("pi-1", "Customer request", KEY);
        for (var call : transport.calls()) {
            if (!call.url.endsWith("/auth/token")) assertFalse(Json.parseObject(call.body).containsKey("amount"));
        }
    }
    @Test void exactMoneyNeverRoundsFractionalCentsOrAcceptsFloatingPoint() {
        assertEquals("1990", Money.minor("19.900", 2)); assertEquals(Money.MAX, Money.integer("9223372036854775807"));
        assertEquals("19", Money.minor("19", 0)); assertEquals("1234", Money.minor("1.234", 3));
        assertThrows(ArithmeticException.class, () -> Money.minor("19.901", 2)); assertThrows(IllegalArgumentException.class, () -> Money.integer(1.0));
        assertThrows(IllegalArgumentException.class, () -> Money.integer("9223372036854775808")); assertThrows(IllegalArgumentException.class, () -> Money.minor("1e2", 2));
        for (String currency : new String[]{"TRY", "USD", "EUR", "GBP", "RUB", "AED", "SAR", "QAR", "CHF", "CAD", "AUD", "CNY", "AZN"}) { assertEquals(2, Money.exponent(currency)); }
        assertEquals(0, Money.exponent("JPY")); assertEquals(3, Money.exponent("KWD")); assertThrows(IllegalArgumentException.class, () -> Money.exponent("NOK"));
    }
    @Test void jsonRejectsDuplicateFieldsAndExcessiveNesting() {
        assertThrows(RuntimeException.class, () -> Json.parseObject("{\"id\":\"one\",\"id\":\"two\"}"));
        assertThrows(RuntimeException.class, () -> Json.parse("[".repeat(100) + "0" + "]".repeat(100)));
    }
    @Test void credentialsCannotBeSentToHttpUserinfoOrRedirectFollowingTransports() {
        for (String origin : new String[]{"http://gateway.invalid", "https://user:secret@gateway.invalid", "https://gateway.invalid/api", "https://gateway.invalid?q=x", "https://gateway.invalid#fragment"}) {
            assertThrows(RuntimeException.class, () -> LunixiConfig.builder().baseUrl(origin).keyId("test").privateKey(keys.privateKeyPem()).build());
        }
        assertThrows(IllegalArgumentException.class, () -> new com.lunixi.sdk.http.JdkHttpTransport(java.net.http.HttpClient.newBuilder().followRedirects(java.net.http.HttpClient.Redirect.ALWAYS).build()));
    }
}
