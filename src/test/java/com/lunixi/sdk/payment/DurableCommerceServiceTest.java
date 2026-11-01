package com.lunixi.sdk.payment;

import static org.junit.jupiter.api.Assertions.*;

import com.lunixi.sdk.LunixiClient;
import com.lunixi.sdk.LunixiConfig;
import com.lunixi.sdk.auth.Ed25519KeyPair;
import com.lunixi.sdk.fraud.FraudDecision;
import com.lunixi.sdk.internal.RequestOptions;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import com.lunixi.sdk.exception.LunixiApiException;
import com.lunixi.sdk.json.Json;
import com.lunixi.sdk.webhook.WebhookEvent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DurableCommerceServiceTest {
    @TempDir Path directory;
    DurableCommerceFixture.FileLedger store;
    DurableCommerceFixture.Gateway gateway;
    LunixiClient client;
    CommerceService service;

    @BeforeEach void setup() {
        store = new DurableCommerceFixture.FileLedger(directory.resolve("native-ledger.json"));
        gateway = new DurableCommerceFixture.Gateway(store, false);
        client = DurableCommerceFixture.client(gateway);
        service = new CommerceService(client, store, DurableCommerceFixture.box());
    }

    CheckoutSession start() {
        return service.start(DurableCommerceFixture.ORDER, DurableCommerceFixture.SNAPSHOT,
                "https://shop.invalid/return", true, false, "en");
    }

    void loseCreate() {
        gateway.loseCreate = true;
        assertThrows(LunixiApiException.class, this::start);
        assertEquals("", store.record().get("paymentId"));
    }

    record Signed(String body, Map<String, String> headers) { }
    Signed signed(String eventId, String type, String paymentId) throws Exception {
        String body = Json.stringify(Map.of("id", eventId, "type", type,
                "data", Map.of("intentId", paymentId, "orderId", DurableCommerceFixture.ORDER)));
        String timestamp = Instant.now().toString();
        Mac hmac = Mac.getInstance("HmacSHA256");
        hmac.init(new SecretKeySpec(DurableCommerceFixture.SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = HexFormat.of().formatHex(hmac.doFinal((eventId + "." + timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
        return new Signed(body, Map.of("x-lunixi-event-id", eventId, "x-lunixi-event-type", type,
                "x-lunixi-signature-timestamp", timestamp, "x-lunixi-signature", "v2=" + signature));
    }

    WebhookEvent verified(Signed signed) {
        WebhookEvent event = client.webhooks().verifyMerchant(signed.body(), signed.headers(), DurableCommerceFixture.SECRET);
        assertTrue(event.isMerchantEnvelope());
        assertEquals(OrderSnapshot.hash(signed.body()), event.rawBodyHash());
        return event;
    }

    @Test void reservationCommitsBeforeDispatchAndEncryptedTokenSurvivesNewClient() {
        CheckoutSession first = start();
        Map<String, Object> persisted = store.record();
        assertFalse(persisted.get("token").toString().contains(DurableCommerceFixture.TOKEN));
        assertFalse(persisted.get("paymentPageUrl").toString().contains("https://pay.lunixi.com"));
        String key = (String) persisted.get("key");
        CommerceService restarted = new CommerceService(DurableCommerceFixture.client(gateway),
                new DurableCommerceFixture.FileLedger(directory.resolve("native-ledger.json")), DurableCommerceFixture.box());
        CheckoutSession cached = restarted.start(DurableCommerceFixture.ORDER, DurableCommerceFixture.SNAPSHOT,
                "https://shop.invalid/return", true, false, "en");
        assertEquals(first.paymentId(), cached.paymentId());
        assertEquals(first.token(), cached.token());
        assertEquals("https://pay.lunixi.com/payment/hosted-fixture-session", cached.hostedUrl());
        assertEquals(first.hostedUrl(), cached.hostedUrl());
        assertEquals(key, store.record().get("key"));
        assertEquals(1, gateway.bodies.size(), "A cache reopen must not create another payment.");
    }

    @Test void legacyAttemptWithoutHostedUrlRetainsItsEmbeddedTokenKeyBodyAndFinancialHistory() {
        CheckoutSession first = start();
        String key = (String) store.record().get("key");
        store.transaction(DurableCommerceFixture.ORDER, access -> {
            Map<String, Object> legacy = access.load(); legacy.remove("paymentPageUrl"); access.save(legacy); return null;
        });
        CheckoutSession cached = start();
        assertEquals(first.paymentId(), cached.paymentId()); assertEquals(first.token(), cached.token());
        assertThrows(IllegalStateException.class, cached::hostedUrl);
        assertEquals(key, store.record().get("key")); assertEquals(1, gateway.bodies.size());
    }

    @Test void timeoutRetryReusesExactPersistedKeyAndBodyWithoutRotatingAnAttempt() {
        loseCreate();
        String key = (String) store.record().get("key");
        String body = gateway.bodies.get(0);
        gateway.loseCreate = false;
        start();
        assertEquals(2, gateway.bodies.size());
        assertEquals(body, gateway.bodies.get(1));
        assertEquals(key, gateway.keys.get(1));
        assertEquals(key, store.record().get("key"));
    }

    @Test void changedOrderOrCheckoutSettingsDoNotDispatchAnotherCreate() {
        loseCreate();
        OrderSnapshot changed = new OrderSnapshot("merchant_fixture", DurableCommerceFixture.ORDER, "2990", "TRY");
        assertThrows(IllegalStateException.class, () -> service.start(DurableCommerceFixture.ORDER,
                changed, "https://shop.invalid/return", true, false, "en"));
        assertThrows(IllegalStateException.class, () -> service.start(DurableCommerceFixture.ORDER,
                DurableCommerceFixture.SNAPSHOT, "https://shop.invalid/return", false, false, "en"));
        assertEquals(1, gateway.bodies.size());
    }

    @Test void expiredGatewayKeyWindowBlocksPostButAuthenticatedRecoveryStillWorks() throws Exception {
        loseCreate();
        store.edit(record -> record.put("createdAt", Instant.now().getEpochSecond() - 23 * 3600));
        String key = (String) store.record().get("key");
        assertThrows(IllegalStateException.class, this::start);
        assertEquals(1, gateway.bodies.size());
        Signed signed = signed("evt_expired_key", "payment.captured", DurableCommerceFixture.PAYMENT);
        PaymentDecision decision = service.reconcile(DurableCommerceFixture.ORDER, verified(signed), signed.body(), store::applyNative);
        assertTrue(decision.isPaid());
        assertEquals(key, store.record().get("key"));
        assertEquals(1, gateway.reads);
        assertEquals(1, store.applications());
    }

    @Test void cachedTokenExpiryDoesNotAuthorizeANewFinancialAttempt() {
        start();
        String key = (String) store.record().get("key");
        store.edit(record -> record.put("createdAt", Instant.now().getEpochSecond() - 840));
        assertThrows(IllegalStateException.class, this::start);
        assertEquals(1, gateway.bodies.size());
        assertEquals(key, store.record().get("key"));
    }

    @Test void onlyExactOpaqueRecoveryMetadataMayBindAnUnboundPayment() throws Exception {
        loseCreate();
        Signed signed = signed("evt_wrong_attempt", "payment.captured", DurableCommerceFixture.PAYMENT);
        gateway.wrongAttempt = true;
        assertThrows(IllegalStateException.class, () -> service.reconcile(DurableCommerceFixture.ORDER,
                verified(signed), signed.body(), store::applyNative));
        assertEquals("", store.record().get("paymentId"));
        assertEquals(0, store.receipts());
        assertEquals(0, store.applications());
        gateway.wrongAttempt = false;
        assertTrue(service.reconcile(DurableCommerceFixture.ORDER, verified(signed), signed.body(), store::applyNative).isPaid());
        assertEquals(DurableCommerceFixture.PAYMENT, store.record().get("paymentId"));
    }

    @Test void eventReceiptAndNativeWritesRollbackTogetherThenReplayAndCollisionAreSafe() throws Exception {
        start();
        Signed signed = signed("evt_native_transaction", "payment.captured", DurableCommerceFixture.PAYMENT);
        WebhookEvent event = verified(signed);
        assertThrows(IllegalStateException.class, () -> service.reconcile(DurableCommerceFixture.ORDER,
                event, signed.body(), decision -> { store.applyNative(decision); throw new IllegalStateException("Synthetic native failure"); }));
        assertEquals(0, store.receipts());
        assertEquals(0, store.applications());
        service.reconcile(DurableCommerceFixture.ORDER, event, signed.body(), store::applyNative);
        service.reconcile(DurableCommerceFixture.ORDER, event, signed.body(), store::applyNative);
        assertEquals(1, store.receipts());
        assertEquals(1, store.applications());
        Signed collision = signed("evt_native_transaction", "payment.failed", DurableCommerceFixture.PAYMENT);
        assertThrows(IllegalStateException.class, () -> service.reconcile(DurableCommerceFixture.ORDER,
                verified(collision), collision.body(), store::applyNative));
        assertEquals(1, store.applications());
    }

    @Test void wrongRawBytesLegacyProofAndNonpaymentProofCannotReadOrConsumeInbox() throws Exception {
        loseCreate();
        Signed signed = signed("evt_proof", "payment.captured", DurableCommerceFixture.PAYMENT);
        WebhookEvent event = verified(signed);
        assertThrows(RuntimeException.class, () -> service.reconcile(DurableCommerceFixture.ORDER,
                event, signed.body() + " ", store::applyNative));
        WebhookEvent legacy = client.webhooks().verify(signed.body(), signed.headers(), DurableCommerceFixture.SECRET);
        assertFalse(legacy.isMerchantEnvelope());
        assertThrows(RuntimeException.class, () -> service.reconcile(DurableCommerceFixture.ORDER,
                legacy, signed.body(), store::applyNative));
        Signed fraud = signed("evt_fraud", "fraud.alert.triggered", DurableCommerceFixture.PAYMENT);
        assertThrows(RuntimeException.class, () -> service.reconcile(DurableCommerceFixture.ORDER,
                verified(fraud), fraud.body(), store::applyNative));
        assertEquals(0, gateway.reads);
        assertEquals(0, store.receipts());
        assertEquals(0, store.applications());
    }

    @Test void aBoundPaymentCannotBeReboundFromADifferentSignedCandidate() throws Exception {
        start();
        Signed conflict = signed("evt_binding_conflict", "payment.captured", "pi_other");
        assertThrows(IllegalStateException.class, () -> service.reconcile(DurableCommerceFixture.ORDER,
                verified(conflict), conflict.body(), store::applyNative));
        assertEquals(0, gateway.reads);
        assertEquals(DurableCommerceFixture.PAYMENT, store.record().get("paymentId"));
    }

    @Test void changingGatewayOriginOrEnvironmentCannotReuseOrReadAnExistingReservation() throws Exception {
        loseCreate();
        String key = (String) store.record().get("key");
        Signed signed = signed("evt_context", "payment.captured", DurableCommerceFixture.PAYMENT);
        for (int profile = 0; profile < 2; profile++) {
            LunixiClient switched = LunixiClient.create(LunixiConfig.builder()
                    .baseUrl(profile == 0 ? "https://other-gateway.invalid" : "https://gateway.invalid")
                    .environment(profile == 0 ? LunixiConfig.Environment.LIVE : LunixiConfig.Environment.TEST)
                    .keyId("fixture-key").privateKey(Ed25519KeyPair.generate().privateKeyPem())
                    .transport(gateway).maxRetries(0).build());
            CommerceService changed = new CommerceService(switched, store, DurableCommerceFixture.box());
            assertThrows(IllegalStateException.class, () -> changed.start(DurableCommerceFixture.ORDER,
                    DurableCommerceFixture.SNAPSHOT, "https://shop.invalid/return", true, false, "en"));
            assertThrows(IllegalStateException.class, () -> changed.reconcile(DurableCommerceFixture.ORDER,
                    verified(signed), signed.body(), store::applyNative));
            assertThrows(IllegalStateException.class, () -> changed.reconcile(DurableCommerceFixture.ORDER,
                    null, null, store::applyNative));
        }
        assertEquals(1, gateway.bodies.size());
        assertEquals(0, gateway.reads);
        assertEquals(0, store.receipts());
        assertEquals(0, store.applications());
        assertEquals(key, store.record().get("key"));
    }

    @Test void requestOptionsAndDecodedNestedResponsesCannotBeMutatedThroughPublicViews() throws Exception {
        RequestOptions.Builder builder = RequestOptions.builder().query("cursor", "original");
        RequestOptions options = builder.build();
        builder.query("cursor", "changed");
        assertEquals("original", options.query().get("cursor"));
        assertThrows(UnsupportedOperationException.class, () -> options.query().put("cursor", "tampered"));
        Map<String, Object> nested = new LinkedHashMap<>(); nested.put("nullable", null);
        List<Object> rows = new ArrayList<>(); rows.add(nested);
        Map<String, Object> source = new LinkedHashMap<>(); source.put("rows", rows);
        FraudDecision decision = new FraudDecision(source);
        LunixiApiException error = new LunixiApiException("Synthetic", 400, "TEST", source, "request-fixture");
        nested.put("nullable", "changed"); rows.add("extra"); source.put("extra", "changed");
        for (Map<String, Object> copy : List.of(decision.raw(), error.response())) {
            assertFalse(copy.containsKey("extra"));
            assertThrows(UnsupportedOperationException.class, () -> copy.put("extra", true));
            List<?> immutableRows = (List<?>) copy.get("rows");
            assertEquals(1, immutableRows.size());
            assertThrows(UnsupportedOperationException.class, immutableRows::clear);
            Map<?, ?> immutableNested = (Map<?, ?>) immutableRows.get(0);
            assertTrue(immutableNested.containsKey("nullable"));
            assertNull(immutableNested.get("nullable"));
            assertThrows(UnsupportedOperationException.class, immutableNested::clear);
        }
        Signed signed = signed("evt_immutable", "payment.captured", DurableCommerceFixture.PAYMENT);
        WebhookEvent event = verified(signed);
        assertThrows(UnsupportedOperationException.class, () -> event.data().put("intentId", "pi_tampered"));
        assertThrows(UnsupportedOperationException.class, () -> ((Map<?, ?>) event.envelope().get("data")).clear());
        assertEquals(DurableCommerceFixture.PAYMENT, CommerceService.eventPaymentId(event));
    }

    @Test void separateJvmsReuseByteIdenticalCreateBodiesAndKeysFromOnlyDiskState() throws Exception {
        Path state = directory.resolve("cross-jvm-ledger.json");
        Map<String, Object> baseline = probe(state);
        for (int process = 0; process < 5; process++) {
            Map<String, Object> retry = probe(state);
            assertEquals(baseline.get("body"), retry.get("body"), "Body changed after a fresh JVM startup.");
            assertEquals(baseline.get("key"), retry.get("key"), "Financial key rotated after restart.");
        }
        Map<String, Object> body = Json.parseObject((String) baseline.get("body"));
        Map<String, Object> proof = PaymentEvidence.object(PaymentEvidence.object(body.get("metadata")).get("lunixiCommerce"));
        assertEquals(DurableCommerceFixture.SNAPSHOT.fingerprint(), proof.get("fingerprint"));
        assertEquals(OrderSnapshot.hash((String) baseline.get("key")), proof.get("attempt"));
    }

    private static Map<String, Object> probe(Path state) throws Exception {
        String separator = System.getProperty("path.separator");
        String classpath = Path.of(CommerceService.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                + separator + Path.of(DurableCommerceProbe.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        Process process = new ProcessBuilder(java.toString(), "-cp", classpath,
                DurableCommerceProbe.class.getName(), state.toString()).redirectErrorStream(true).start();
        if (!process.waitFor(20, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            fail("Fresh JVM probe timed out.");
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        assertEquals(0, process.exitValue(), output);
        return Json.parseObject(output);
    }
}
