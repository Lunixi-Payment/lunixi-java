package com.lunixi.sdk.payment;

import com.lunixi.sdk.LunixiClient;
import com.lunixi.sdk.LunixiConfig;
import com.lunixi.sdk.auth.Ed25519KeyPair;
import com.lunixi.sdk.exception.LunixiApiException;
import com.lunixi.sdk.http.HttpResult;
import com.lunixi.sdk.http.HttpTransport;
import com.lunixi.sdk.json.Json;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/** Test-only, actual disk/OS-lock ledger. No process-local cache stands in for durability. */
final class DurableCommerceFixture {
    static final String ORDER = "native-order-123";
    static final String PAYMENT = "pi_durable_fixture";
    static final String SECRET = "synthetic_webhook_secret_0123456789";
    static final String TOKEN = "synthetic_browser_token_DO_NOT_LOG";
    static final OrderSnapshot SNAPSHOT = new OrderSnapshot("merchant_fixture", ORDER, "1990", "TRY");

    private DurableCommerceFixture() { }

    static SecretBox box() {
        byte[] key = new byte[32];
        Arrays.fill(key, (byte) 0x42); // Synthetic test storage key, no production access.
        return new SecretBox(key);
    }

    static LunixiClient client(HttpTransport transport) {
        return LunixiClient.create(LunixiConfig.builder()
                .baseUrl("https://gateway.invalid")
                .keyId("fixture-key")
                .privateKey(Ed25519KeyPair.generate().privateKeyPem())
                .transport(transport).maxRetries(0).build());
    }

    static final class FileLedger implements LedgerStore {
        private final Path path;
        private Map<String, Object> active;

        FileLedger(Path path) {
            this.path = path;
        }

        @Override
        public <T> T transaction(String orderKey, Function<RecordAccess, T> operation) {
            if (active != null) {
                throw new IllegalStateException("Nested fixture transaction/network under lock.");
            }
            try {
                Files.createDirectories(path.getParent());
                try (FileChannel channel = FileChannel.open(path, StandardOpenOption.CREATE,
                        StandardOpenOption.READ, StandardOpenOption.WRITE);
                     FileLock lock = channel.lock()) {
                    if (!lock.isValid()) { throw new IllegalStateException("Exclusive lock unavailable."); }
                    ByteBuffer input = ByteBuffer.allocate(Math.toIntExact(channel.size()));
                    while (input.hasRemaining() && channel.read(input) != -1) { /* Read entire small fixture. */ }
                    Map<String, Object> working = input.position() == 0
                            ? new LinkedHashMap<>()
                            : Json.parseObject(new String(input.array(), 0, input.position(), StandardCharsets.UTF_8));
                    working.computeIfAbsent("records", ignored -> new LinkedHashMap<String, Object>());
                    working.computeIfAbsent("receipts", ignored -> new LinkedHashMap<String, Object>());
                    active = working;
                    try {
                        T result = operation.apply(new RecordAccess() {
                            @Override public Map<String, Object> load() {
                                Object record = object(working.get("records")).get(orderKey);
                                return record == null ? null : Json.parseObject(Json.stringify(record));
                            }
                            @Override public void save(Map<String, Object> record) {
                                object(working.get("records")).put(orderKey, Json.parseObject(Json.stringify(record)));
                            }
                            @Override public boolean claimEvent(String merchant, String id, String hash) {
                                Map<String, Object> receipts = object(working.get("receipts"));
                                String receiptKey = OrderSnapshot.hash(merchant + "\0" + id);
                                if (receipts.containsKey(receiptKey)) {
                                    if (!hash.equals(receipts.get(receiptKey))) {
                                        throw new IllegalStateException("Verified event content collision.");
                                    }
                                    return false;
                                }
                                receipts.put(receiptKey, hash);
                                return true;
                            }
                        });
                        // A failed callback never reaches the write: receipt/native mutation roll back together.
                        byte[] bytes = Json.stringify(working).getBytes(StandardCharsets.UTF_8);
                        channel.position(0);
                        channel.truncate(0);
                        ByteBuffer output = ByteBuffer.wrap(bytes);
                        while (output.hasRemaining()) { channel.write(output); }
                        channel.force(true);
                        return result;
                    } finally {
                        active = null;
                    }
                }
            } catch (IOException failure) {
                throw new IllegalStateException("Durable test ledger I/O failed.", failure);
            }
        }

        Map<String, Object> record() {
            return transaction(ORDER, RecordAccess::load);
        }

        void edit(Consumer<Map<String, Object>> edit) {
            transaction(ORDER, access -> {
                Map<String, Object> record = access.load();
                edit.accept(record);
                access.save(record);
                return null;
            });
        }

        void applyNative(PaymentDecision decision) {
            if (active == null) { throw new IllegalStateException("Native write escaped its transaction."); }
            long count = ((Number) active.getOrDefault("nativeApplications", 0L)).longValue();
            active.put("nativeApplications", count + 1);
            active.put("nativeState", decision.state());
        }

        long applications() {
            return transaction(ORDER, access -> ((Number) active.getOrDefault("nativeApplications", 0L)).longValue());
        }

        int receipts() {
            return transaction(ORDER, access -> object(active.get("receipts")).size());
        }

        @SuppressWarnings("unchecked")
        private static Map<String, Object> object(Object value) {
            return (Map<String, Object>) value;
        }
    }

    static final class Gateway implements HttpTransport {
        final FileLedger store;
        final List<String> bodies = new ArrayList<>();
        final List<String> keys = new ArrayList<>();
        int reads;
        boolean loseCreate;
        boolean wrongAttempt;
        String responsePayment = PAYMENT;
        Map<String, Object> request;

        Gateway(FileLedger store, boolean loseCreate) {
            this.store = store;
            this.loseCreate = loseCreate;
        }

        @Override
        public HttpResult send(String method, String url, Map<String, String> headers,
                               String body, Duration timeout) {
            if (url.endsWith("/api/v1/auth/token")) {
                return new HttpResult(200, Map.of(), "{\"access_token\":\"at_SYNTHETIC_ONLY\",\"expires_in\":1800}");
            }
            if (method.equals("POST") && url.endsWith("/api/v1/payments/intents")) {
                // The persisted reservation must be visible through a NEW ledger BEFORE API I/O.
                Map<String, Object> persisted = new FileLedger(store.path).record();
                if (persisted == null || !persisted.get("key").equals(headers.get("Idempotency-Key"))) {
                    throw new AssertionError("Create escaped before a durable stable-key reservation.");
                }
                bodies.add(body);
                keys.add(headers.get("Idempotency-Key"));
                request = Json.parseObject(body);
                if (loseCreate) {
                    throw new LunixiApiException("Synthetic lost create response", new IOException("fixture-only"));
                }
                return new HttpResult(201, Map.of(), Json.stringify(Map.of("data", Map.of("paymentId", PAYMENT, "token", TOKEN,
                        "paymentPageUrl", "https://pay.lunixi.com/payment/hosted-fixture-session"))));
            }
            if (method.equals("GET") && url.endsWith("/api/v1/payments/" + responsePayment)) {
                reads++;
                Map<String, Object> metadata = request == null
                        ? PaymentEvidence.object(Json.parseObject(bodies.get(0)).get("metadata"))
                        : Json.parseObject(Json.stringify(request.get("metadata")));
                if (wrongAttempt) {
                    PaymentEvidence.object(metadata.get("lunixiCommerce")).put("attempt", "not-the-reserved-attempt");
                }
                Map<String, Object> payment = new LinkedHashMap<>();
                payment.put("id", responsePayment);
                payment.put("merchantId", SNAPSHOT.merchantId());
                payment.put("orderId", SNAPSHOT.orderId());
                payment.put("currency", SNAPSHOT.currency());
                payment.put("amount", SNAPSHOT.amount());
                payment.put("status", "CAPTURED");
                payment.put("metadata", metadata);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("data", payment);
                result.put("details", Map.of("capturedAmount", "1990", "refundedAmount", "0"));
                return new HttpResult(200, Map.of(), Json.stringify(result));
            }
            throw new AssertionError("Unexpected external gateway route in contract fixture.");
        }
    }
}
