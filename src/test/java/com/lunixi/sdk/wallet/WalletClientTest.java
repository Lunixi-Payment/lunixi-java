package com.lunixi.sdk.wallet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import com.lunixi.sdk.http.Route;
import com.lunixi.sdk.json.Json;
import com.lunixi.sdk.support.RecordingTransport;

/** Wallet: route table, signing, keys, body and query checks, two-step completes, pages and the QR image. */
class WalletClientTest {

    static final String GATEWAY = "https://gateway.invalid";
    static final String BASE = GATEWAY + "/api/v1/wallet";
    static final String OK = "{\"status\":\"success\",\"code\":\"SUCCESS\",\"data\":{\"id\":\"w_1\"}}";
    static final String KEY = "wallet-key-0001";

    RecordingTransport transport;
    WalletClient wallet;

    @BeforeEach
    void setUp() {
        transport = new RecordingTransport("/api/v1/auth/token", "fixture-only");
        wallet = LunixiClient.create(LunixiConfig.builder().baseUrl(GATEWAY).keyId("test-key")
                .privateKey(Ed25519KeyPair.generate().privateKeyPem()).transport(transport).maxRetries(0).build())
                .wallet();
    }

    static void assertSigned(RecordingTransport.Call call) {
        assertNotNull(call.headers.get(CanonicalRequest.HEADER_SIGNATURE), call.url);
        assertEquals("test-key", call.headers.get(CanonicalRequest.HEADER_KEY_ID));
    }

    static Map<String, Object> body(RecordingTransport.Call call) {
        return Json.parseObject(call.body);
    }

    void assertNothingSent() {
        assertTrue(transport.apiCalls().isEmpty(), "no request may be sent");
    }

    static Map<String, Object> map(Object... pairs) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            out.put((String) pairs[i], pairs[i + 1]);
        }
        return out;
    }

    @Test
    void routeTableHasTheSixtyRoutes() {
        assertEquals(60, WalletRoutes.ROUTES.size());
        Route complete = WalletRoutes.route("completeW2W");
        assertEquals("POST", complete.method());
        assertEquals("/api/v1/wallet/transfers/w2w/:operationId/complete", complete.path());
        assertTrue(complete.idempotencyKey());
        assertEquals(List.of("endUserId", "operationId", "integrityHash", "nonce"), complete.required());
        assertTrue(WalletRoutes.route("getQrImage").binary());
        assertEquals(List.of("format", "scale"), WalletRoutes.route("getQrImage").query());
        assertFalse(WalletRoutes.route("refundTopup").idempotencyKey());
        assertThrows(IllegalArgumentException.class, () -> WalletRoutes.route("nope"));
    }

    @Test
    void createEndUserSendsTheBodySignedWithTheKey() {
        transport.enqueue(201, OK);
        Map<String, Object> user = wallet.endUsers().create(map("programId", "p1", "accountType",
                WalletValues.AccountType.PERSONAL, "phone", "+905551112233", "displayName", null), KEY);
        assertEquals("w_1", user.get("id"));
        RecordingTransport.Call call = transport.onlyApiCall();
        assertEquals("POST", call.method);
        assertEquals(BASE + "/end-users", call.url);
        assertSigned(call);
        assertEquals(KEY, call.headers.get("Idempotency-Key"));
        assertEquals(map("programId", "p1", "accountType", "PERSONAL", "phone", "+905551112233"), body(call));
    }

    @Test
    void keyedRoutesRefuseAMissingOrMalformedKey() {
        Map<String, Object> request = map("programId", "p1", "accountType", "PERSONAL", "phone", "+90555");
        assertThrows(ConfigurationException.class, () -> wallet.endUsers().create(request, null));
        assertThrows(ConfigurationException.class, () -> wallet.endUsers().create(request, "   "));
        assertThrows(ConfigurationException.class, () -> wallet.endUsers().create(request, "x".repeat(129)));
        assertThrows(ConfigurationException.class, () -> wallet.endUsers().create(request, "bad\nkey"));
        assertNothingSent();
    }

    @Test
    void bodiesAreCheckedAgainstTheRoute() {
        assertThrows(ConfigurationException.class, () -> wallet.endUsers().create(map("programId", "p1",
                "accountType", "PERSONAL", "phone", "+90555", "businessAccountId", "b1"), KEY));
        assertThrows(ConfigurationException.class, () -> wallet.endUsers().create(map("programId", "p1",
                "accountType", "PERSONAL"), KEY));
        assertThrows(ConfigurationException.class, () -> wallet.endUsers().create(map("programId", "",
                "accountType", "PERSONAL", "phone", "+90555"), KEY));
        assertNothingSent();
    }

    @Test
    void amountsMustBeMinorUnitDigitStrings() {
        for (Object amount : List.of(12550L, 125.5, "125.50", "0", "-1", "0012", "1".repeat(19))) {
            assertThrows(ConfigurationException.class, () -> wallet.transfers().quoteW2W(map("endUserId", "u1",
                    "sourceWalletAccountId", "a1", "amount", amount, "currency", "TRY")), String.valueOf(amount));
        }
        assertNothingSent();
        transport.enqueue(200, OK);
        wallet.transfers().quoteW2W(map("endUserId", "u1", "sourceWalletAccountId", "a1", "amount", "12550",
                "currency", "TRY", "destinationPhone", "+90555"));
        RecordingTransport.Call call = transport.onlyApiCall();
        assertEquals(BASE + "/transfers/w2w/quote", call.url);
        assertNull(call.headers.get("Idempotency-Key"), "a quote takes no key");
        assertEquals("12550", body(call).get("amount"));
    }

    @Test
    void completePutsTheOperationIdIntoTheBody() {
        transport.enqueue(200, OK);
        wallet.transfers().completeW2Iban("op 1", map("endUserId", "u1", "integrityHash", "h", "nonce", "n",
                "otpCode", "123456"), KEY);
        RecordingTransport.Call call = transport.onlyApiCall();
        assertEquals(BASE + "/transfers/w2iban/op%201/complete", call.url);
        assertEquals("op 1", body(call).get("operationId"));
        assertEquals(KEY, call.headers.get("Idempotency-Key"));

        assertThrows(ConfigurationException.class, () -> wallet.withdrawals().complete("op-1", map("endUserId", "u1",
                "operationId", "op-2", "integrityHash", "h", "nonce", "n"), KEY));
        assertThrows(ConfigurationException.class, () -> wallet.transfers().completeW2W(" ", map("endUserId", "u1",
                "integrityHash", "h", "nonce", "n"), KEY));
        assertEquals(1, transport.apiCalls().size());
    }

    @Test
    void readsAreSignedGetsWithTheirQuery() {
        transport.enqueue(200, "{\"status\":\"success\",\"data\":{\"items\":[{\"id\":\"u1\"}],\"nextCursor\":\"c2\"}}");
        WalletPage page = wallet.endUsers().list(map("programId", "p1", "limit", 10, "status", null));
        assertEquals(1, page.items().size());
        assertEquals("c2", page.nextCursor().orElseThrow());
        assertTrue(page.hasMore());
        RecordingTransport.Call call = transport.onlyApiCall();
        assertEquals("GET", call.method);
        assertEquals(BASE + "/end-users?programId=p1&limit=10", call.url);
        assertNull(call.body);
        assertSigned(call);

        assertThrows(ConfigurationException.class, () -> wallet.endUsers().list(map("merchantId", "m1")));
        assertEquals(1, transport.apiCalls().size());
    }

    @Test
    void operationsPageReadsTheOperationsListAndIterates() {
        transport.enqueue(200, "{\"status\":\"success\",\"data\":{\"operations\":[{\"id\":\"o1\"},{\"id\":\"o2\"}],"
                + "\"nextCursor\":\"c2\"}}");
        transport.enqueue(200, "{\"status\":\"success\",\"data\":{\"operations\":[{\"id\":\"o3\"}],"
                + "\"nextCursor\":null}}");
        List<Object> ids = new ArrayList<>();
        wallet.operations().iterate(map("endUserId", "u1")).forEach(row -> ids.add(row.get("id")));
        assertEquals(List.of("o1", "o2", "o3"), ids);
        List<RecordingTransport.Call> calls = transport.apiCalls();
        assertEquals(BASE + "/operations?endUserId=u1", calls.get(0).url);
        assertEquals(BASE + "/operations?endUserId=u1&cursor=c2", calls.get(1).url);
    }

    @Test
    void bulkPayoutItemsAreCheckedOneByOne() {
        Map<String, Object> item = map("targetType", WalletValues.BulkTargetType.IBAN, "targetRef", "TR00",
                "amount", "1000");
        assertThrows(ConfigurationException.class, () -> wallet.bulkPayouts().create(map("programId", "p1",
                "sourceWalletAccountId", "a1", "currency", "TRY", "items", List.of()), KEY));
        assertThrows(ConfigurationException.class, () -> wallet.bulkPayouts().create(map("programId", "p1",
                "sourceWalletAccountId", "a1", "currency", "TRY", "items", List.of(map("targetType", "IBAN",
                        "amount", "1000"))), KEY));
        assertThrows(ConfigurationException.class, () -> wallet.bulkPayouts().create(map("programId", "p1",
                "sourceWalletAccountId", "a1", "currency", "TRY", "items", List.of(map("targetType", "IBAN",
                        "targetRef", "TR00", "amount", 1000))), KEY));
        assertThrows(ConfigurationException.class, () -> wallet.bulkPayouts().create(map("programId", "p1",
                "sourceWalletAccountId", "a1", "currency", "TRY", "items", List.of(map("targetType", "IBAN",
                        "targetRef", "TR00", "amount", "1000", "iban", "x"))), KEY));
        assertNothingSent();

        transport.enqueue(201, OK);
        wallet.bulkPayouts().create(map("programId", "p1", "sourceWalletAccountId", "a1", "currency", "TRY",
                "items", List.of(item)), KEY);
        assertEquals(List.of(item), body(transport.onlyApiCall()).get("items"));
    }

    @Test
    void bodylessPostsAndApprovalBodies() {
        transport.enqueue(200, OK);
        transport.enqueue(200, OK);
        wallet.qr().revoke("qr_1");
        wallet.bulkPayouts().approve("b_1", false, "duplicate");
        List<RecordingTransport.Call> calls = transport.apiCalls();
        assertEquals("POST", calls.get(0).method);
        assertEquals(BASE + "/qr/qr_1/revoke", calls.get(0).url);
        assertNull(calls.get(0).body);
        assertSigned(calls.get(0));
        assertEquals(map("approve", false, "reason", "duplicate"), body(calls.get(1)));
        assertNull(calls.get(1).headers.get("Idempotency-Key"));
    }

    @Test
    void qrImageReturnsTheBytes() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G'};
        transport.enqueueBinary(200, Map.of("content-type", "image/png"), png);
        WalletQrImage image = wallet.qr().image("qr_1", WalletValues.QrImageFormat.PNG, 4);
        assertEquals("image/png", image.contentType());
        assertArrayEquals(png, image.bytes());
        RecordingTransport.Call call = transport.onlyApiCall();
        assertTrue(call.binary);
        assertEquals(BASE + "/qr/qr_1/image?format=png&scale=4", call.url);
        assertSigned(call);

        assertThrows(ConfigurationException.class, () -> wallet.qr().image("qr_1", "jpg", null));
        assertThrows(ConfigurationException.class, () -> wallet.qr().image("qr_1", null, 21));
        assertThrows(ConfigurationException.class, () -> wallet.qr().image("qr_1", null, 0));
        assertEquals(1, transport.apiCalls().size());
    }

    @Test
    void pingAndProgramsAreSigned() {
        transport.enqueue(200, OK);
        transport.enqueue(200, "{\"status\":\"success\",\"data\":{\"items\":[{\"programId\":\"p1\"}]}}");
        wallet.ping();
        assertEquals(1, wallet.programs().listMine().items().size());
        List<RecordingTransport.Call> calls = transport.apiCalls();
        assertEquals(BASE + "/ping", calls.get(0).url);
        assertEquals(BASE + "/programs/me", calls.get(1).url);
        calls.forEach(WalletClientTest::assertSigned);
    }
}
