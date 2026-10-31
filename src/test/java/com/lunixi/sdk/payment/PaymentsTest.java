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
    Map<String, Object> request() { return Map.of("amount", 1990L, "currency", "TRY", "orderId", "order-1", "callbackUrl", "https://shop.invalid/return"); }
    @Test void signsTheExactIntentBodyAndReadsActualPaymentIdToken() throws Exception {
        transport.enqueue(201, "{\"data\":{\"paymentId\":\"pi-1\",\"token\":\"browser/token?\"}}");
        CheckoutSession result = client.payments().createIntent(request(), KEY);
        var call = transport.onlyApiCall(); String path = "/api/v1/payments/intents";
        assertEquals("https://gateway.invalid" + path, call.url); assertEquals(KEY, call.headers.get("Idempotency-Key"));
        assertEquals(CanonicalRequest.digestForBody(call.body), call.headers.get(CanonicalRequest.HEADER_DIGEST));
        String canonical = CanonicalRequest.build("POST", path, call.headers.get(CanonicalRequest.HEADER_DATE), call.headers.get(CanonicalRequest.HEADER_NONCE), call.headers.get(CanonicalRequest.HEADER_DIGEST));
        Signature verifier = Signature.getInstance("Ed25519"); verifier.initVerify(Ed25519Signer.publicKeyFrom(keys.publicKeyPem())); verifier.update(canonical.getBytes(StandardCharsets.UTF_8));
        assertTrue(verifier.verify(Base64.getDecoder().decode(call.headers.get(CanonicalRequest.HEADER_SIGNATURE))));
        assertEquals("pi-1", result.paymentId()); assertEquals("https://pay.lunixi.com/payment/browser%2Ftoken%3F", result.hostedUrl()); assertFalse(result.toString().contains("browser/token"));
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
        transport.enqueue(202, "{\"data\":{\"status\":\"PROCESSING\"}}"); client.payments().refund("pi/1", 990, "Customer refund", KEY);
        var call = transport.onlyApiCall(); assertTrue(call.url.endsWith("/pi%2F1/refund")); assertEquals(990L, Json.parseObject(call.body).get("amount")); assertEquals("pi/1", Json.parseObject(call.body).get("paymentIntentId"));
        assertNotNull(call.headers.get(CanonicalRequest.HEADER_SIGNATURE));
        assertThrows(IllegalArgumentException.class, () -> client.payments().capture("pi-1", -1, KEY));
        assertThrows(IllegalArgumentException.class, () -> client.payments().voidPayment("pi-1", "header\r\ninjection"));
    }
    @Test void exactMoneyNeverRoundsFractionalCentsOrAcceptsFloatingPoint() {
        assertEquals(1990, Money.minor("19.900", 2)); assertEquals(Money.MAX, Money.integer("9007199254740991"));
        assertEquals(19, Money.minor("19", 0)); assertEquals(1234, Money.minor("1.234", 3));
        assertThrows(ArithmeticException.class, () -> Money.minor("19.901", 2)); assertThrows(IllegalArgumentException.class, () -> Money.integer(1.0));
        assertThrows(IllegalArgumentException.class, () -> Money.integer("9007199254740992")); assertThrows(IllegalArgumentException.class, () -> Money.minor("1e2", 2));
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
