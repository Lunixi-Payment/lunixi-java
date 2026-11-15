package com.lunixi.sdk.bankconnect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.lunixi.sdk.LunixiClient;
import com.lunixi.sdk.LunixiConfig;
import com.lunixi.sdk.auth.CanonicalRequest;
import com.lunixi.sdk.auth.Ed25519KeyPair;
import com.lunixi.sdk.exception.ConfigurationException;
import com.lunixi.sdk.exception.WebhookVerificationException;
import com.lunixi.sdk.json.Json;
import com.lunixi.sdk.support.RecordingTransport;
import com.lunixi.sdk.support.SignedWebhooks;

/** Bank accounts: signed reads, the filter rules, transfers and exchange orders (keys, retries, bodies), webhooks. */
class BankConnectClientTest {

    static final String GATEWAY = "https://gateway.invalid";
    static final String BASE = GATEWAY + "/api/v1/bank-connect";
    static final String PAGE = "{\"status\":\"success\",\"data\":{\"items\":[{\"accountId\":\"a1\"}]},"
            + "\"pageInfo\":{\"hasMore\":true,\"nextCursor\":\"c2\",\"totalCount\":null}}";
    static final String TRANSFER = "{\"status\":\"success\",\"data\":{\"transfer\":{\"id\":\"t1\",\"amountMinor\":"
            + "\"150075\",\"status\":\"SUBMITTED\"},\"stepUp\":null,\"replayed\":false}}";

    RecordingTransport transport = new RecordingTransport("/api/v1/auth/token", "fixture-only");

    BankConnectClient client(int maxRetries) {
        return LunixiClient.create(LunixiConfig.builder().baseUrl(GATEWAY).keyId("test-key")
                .privateKey(Ed25519KeyPair.generate().privateKeyPem()).transport(transport).maxRetries(maxRetries)
                .build()).bankConnect();
    }

    static void assertSigned(RecordingTransport.Call call) {
        assertNotNull(call.headers.get(CanonicalRequest.HEADER_SIGNATURE), call.url);
        assertEquals("test-key", call.headers.get(CanonicalRequest.HEADER_KEY_ID));
    }

    static Map<String, Object> map(Object... pairs) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            out.put((String) pairs[i], pairs[i + 1]);
        }
        return out;
    }

    void assertNothingSent() {
        assertTrue(transport.apiCalls().isEmpty(), "no request may be sent");
    }

    // ── Reads ─────────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    void readsAreSignedAndListsReadTheTopLevelPageInfo() {
        BankConnectClient bank = client(0);
        transport.enqueue(200, PAGE);
        BankConnectPage page = bank.listAccounts(map("connectionId", "c1", "status",
                BankConnectValues.AccountStatus.ACTIVE, "currency", "try", "limit", 50));
        assertEquals(1, page.items().size());
        assertTrue(page.hasMore());
        assertEquals("c2", page.nextCursor().orElseThrow());
        RecordingTransport.Call call = transport.onlyApiCall();
        assertEquals("GET", call.method);
        assertEquals(BASE + "/accounts?limit=50&connectionId=c1&currency=TRY&status=ACTIVE", call.url);
        assertSigned(call);
    }

    @Test
    void transactionFiltersAreSentAsCommaLists() {
        BankConnectClient bank = client(0);
        transport.enqueue(200, PAGE);
        bank.listTransactions(map("accountId", List.of("a1", "a2"), "bookedFrom",
                Instant.parse("2026-10-01T00:00:00Z"), "bookedTo", "2026-11-01T00:00:00+03:00", "canonicalType",
                List.of(BankConnectValues.TransactionType.FEE, BankConnectValues.TransactionType.TAX),
                "minAmountMinor", "0", "maxAmountMinor", "500000", "direction", "DEBIT"));
        assertEquals(BASE + "/transactions?accountId=a1%2Ca2&bookedFrom=2026-10-01T00%3A00%3A00Z"
                + "&bookedTo=2026-11-01T00%3A00%3A00%2B03%3A00&direction=DEBIT&minAmountMinor=0"
                + "&maxAmountMinor=500000&canonicalType=FEE%2CTAX", transport.onlyApiCall().url);
    }

    @Test
    void filterRulesFailBeforeSending() {
        BankConnectClient bank = client(0);
        List<Map<String, Object>> bad = new ArrayList<>();
        bad.add(map("environment", "LIVE"));
        bad.add(map("limit", 0));
        bad.add(map("limit", 101));
        bad.add(map("limit", "10"));
        bad.add(map("direction", "IN"));
        bad.add(map("canonicalType", List.of("FEE", "BONUS")));
        bad.add(map("minAmountMinor", 100L));
        bad.add(map("maxAmountMinor", "1.5"));
        bad.add(map("minAmountMinor", "500", "maxAmountMinor", "100"));
        bad.add(map("bookedTo", "2026-11-01T00:00:00Z"));
        bad.add(map("bookedFrom", "2026-11-02T00:00:00Z", "bookedTo", "2026-11-01T00:00:00Z"));
        bad.add(map("bookedFrom", Instant.parse("2026-01-01T00:00:00Z"), "bookedTo",
                Instant.parse("2026-01-01T00:00:00Z").plus(93, ChronoUnit.DAYS)));
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            many.add("a" + i);
        }
        bad.add(map("accountId", many));
        bad.add(map("accountId", List.of("a,b")));
        for (Map<String, Object> filters : bad) {
            assertThrows(ConfigurationException.class, () -> bank.listTransactions(filters), filters.toString());
        }
        assertThrows(ConfigurationException.class, () -> bank.listConnections(map("status", "OPEN")));
        assertThrows(ConfigurationException.class, () -> bank.listAccounts(map("status", List.of("ACTIVE"))));
        assertThrows(ConfigurationException.class, () -> bank.listBalances(map("currency", "TL")));
        assertThrows(ConfigurationException.class, () -> bank.listBanks(map("includeTotal", true)));
        assertNothingSent();
    }

    @Test
    void ninetyTwoDaysIsTheWidestWindow() {
        BankConnectClient bank = client(0);
        transport.enqueue(200, PAGE);
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        bank.listTransactions(map("bookedFrom", from, "bookedTo", from.plus(92, ChronoUnit.DAYS)));
        assertEquals(1, transport.apiCalls().size());
    }

    @Test
    void connectionStatusTakesAListAndSyncIsABodylessSignedPost() {
        BankConnectClient bank = client(0);
        transport.enqueue(200, PAGE);
        transport.enqueue(202, "{\"status\":\"success\",\"data\":{\"accountId\":\"a 1\",\"syncStatus\":\"QUEUED\"}}");
        bank.listConnections(map("status", List.of("ACTIVE", "DEGRADED")));
        assertEquals("QUEUED", bank.requestSync("a 1").get("syncStatus"));
        List<RecordingTransport.Call> calls = transport.apiCalls();
        assertEquals(BASE + "/connections?status=ACTIVE%2CDEGRADED", calls.get(0).url);
        assertEquals("POST", calls.get(1).method);
        assertEquals(BASE + "/accounts/a%201/sync", calls.get(1).url);
        assertNull(calls.get(1).body);
        assertNull(calls.get(1).headers.get("Idempotency-Key"));
        assertSigned(calls.get(1));
        assertThrows(ConfigurationException.class, () -> bank.getAccount(" "));
    }

    @Test
    void iterateFollowsTheCursor() {
        BankConnectClient bank = client(0);
        transport.enqueue(200, PAGE);
        transport.enqueue(200, "{\"status\":\"success\",\"data\":{\"items\":[{\"accountId\":\"a2\"}]},"
                + "\"pageInfo\":{\"hasMore\":false,\"nextCursor\":null}}");
        List<Object> ids = new ArrayList<>();
        bank.iterateBalances(map("limit", 1)).forEach(row -> ids.add(row.get("accountId")));
        assertEquals(List.of("a1", "a2"), ids);
        assertEquals(BASE + "/balances?limit=1&cursor=c2", transport.apiCalls().get(1).url);
    }

    // ── Transfers ─────────────────────────────────────────────────────────────────────────────────────────────

    static Map<String, Object> transfer() {
        return map("accountId", "acc_1", "receiverIban", "TR330006100519786457841326", "receiverName", "Ayse Yilmaz",
                "amountMinor", "150075", "currency", "try", "paymentPurpose", "OTHER", "description", "Invoice 42");
    }

    @Test
    void createTransferGeneratesAUuidKeyAndSendsTheBody() {
        BankConnectClient bank = client(0);
        transport.enqueue(201, TRANSFER);
        Map<String, Object> created = bank.transfers().create(transfer());
        assertEquals(Boolean.FALSE, created.get("replayed"));
        RecordingTransport.Call call = transport.onlyApiCall();
        assertEquals("POST", call.method);
        assertEquals(BASE + "/transfers", call.url);
        assertSigned(call);
        assertTrue(call.headers.get("Idempotency-Key").matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"), "UUID v4");
        Map<String, Object> expected = transfer();
        expected.put("currency", "TRY");
        assertEquals(expected, Json.parseObject(call.body));
    }

    @Test
    void aGivenKeyIsCheckedAndKeptAcrossRetries() {
        BankConnectClient bank = client(1);
        assertThrows(ConfigurationException.class, () -> bank.transfers().create(transfer(), "short"));
        assertThrows(ConfigurationException.class, () -> bank.transfers().create(transfer(), "has space 123"));
        assertNothingSent();

        transport.enqueue(503, "{\"status\":\"failure\",\"code\":\"service.unavailable\",\"statusCode\":503}");
        transport.enqueue(201, TRANSFER);
        bank.transfers().create(transfer(), "payout-2026-0001");
        List<RecordingTransport.Call> calls = transport.apiCalls();
        assertEquals(2, calls.size(), "a 5xx is retried");
        assertEquals("payout-2026-0001", calls.get(0).headers.get("Idempotency-Key"));
        assertEquals("payout-2026-0001", calls.get(1).headers.get("Idempotency-Key"));
        assertEquals(calls.get(0).body, calls.get(1).body);
    }

    @Test
    void aGeneratedKeyIsReusedOnTheRetry() {
        BankConnectClient bank = client(1);
        transport.enqueue(502, "{\"status\":\"failure\",\"statusCode\":502}");
        transport.enqueue(201, "{\"status\":\"success\",\"data\":{\"order\":{\"id\":\"o1\"},\"replayed\":false}}");
        bank.fx().createOrder(map("quoteId", "q1"));
        List<RecordingTransport.Call> calls = transport.apiCalls();
        assertEquals(2, calls.size());
        assertNotNull(calls.get(0).headers.get("Idempotency-Key"));
        assertEquals(calls.get(0).headers.get("Idempotency-Key"), calls.get(1).headers.get("Idempotency-Key"));
    }

    @Test
    void transferBodiesAreCheckedBeforeSending() {
        BankConnectClient bank = client(0);
        List<Map<String, Object>> bad = new ArrayList<>();
        Map<String, Object> unknown = transfer();
        unknown.put("environment", "LIVE");
        bad.add(unknown);
        Map<String, Object> missing = transfer();
        missing.remove("receiverIban");
        bad.add(missing);
        for (Object amount : List.of(150075L, "0", "15.5", "-1")) {
            Map<String, Object> body = transfer();
            body.put("amountMinor", amount);
            bad.add(body);
        }
        Map<String, Object> shortName = transfer();
        shortName.put("receiverName", "A");
        bad.add(shortName);
        Map<String, Object> longIban = transfer();
        longIban.put("receiverIban", "TR" + "0".repeat(41));
        bad.add(longIban);
        Map<String, Object> purpose = transfer();
        purpose.put("paymentPurpose", "not valid");
        bad.add(purpose);
        Map<String, Object> description = transfer();
        description.put("description", "x".repeat(141));
        bad.add(description);
        for (Map<String, Object> body : bad) {
            assertThrows(ConfigurationException.class, () -> bank.transfers().create(body), body.toString());
        }
        assertNothingSent();
    }

    @Test
    void transferReadsAndStatusLists() {
        BankConnectClient bank = client(0);
        transport.enqueue(200, PAGE);
        transport.enqueue(200, "{\"status\":\"success\",\"data\":{\"id\":\"t1\"}}");
        transport.enqueue(200, "{\"status\":\"success\",\"data\":{\"items\":[{\"code\":\"RENT\"}]}}");
        bank.transfers().list(map("status", List.of("SUBMITTED", "COMPLETED"), "accountId", "acc_1",
                "createdFrom", Instant.parse("2026-11-01T00:00:00Z")));
        bank.transfers().get("t1");
        bank.transfers().listPurposes("conn_1");
        List<RecordingTransport.Call> calls = transport.apiCalls();
        assertEquals(BASE + "/transfers?accountId=acc_1&status=SUBMITTED%2CCOMPLETED"
                + "&createdFrom=2026-11-01T00%3A00%3A00Z", calls.get(0).url);
        assertEquals(BASE + "/transfers/t1", calls.get(1).url);
        assertEquals(BASE + "/transfer-purposes?connectionId=conn_1", calls.get(2).url);
        calls.forEach(BankConnectClientTest::assertSigned);
        assertThrows(ConfigurationException.class, () -> bank.transfers().list(map("status", "PENDING")));
        assertThrows(ConfigurationException.class, () -> bank.fx().listOrders(map("status", "RETURNED")));
    }

    // ── Exchange ──────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    void quotesTakeExactlyOneAmountAndTwoAccounts() {
        BankConnectClient bank = client(0);
        assertThrows(ConfigurationException.class, () -> bank.fx().createQuote(map("sourceAccountId", "a1",
                "targetAccountId", "a2")));
        assertThrows(ConfigurationException.class, () -> bank.fx().createQuote(map("sourceAccountId", "a1",
                "targetAccountId", "a2", "sourceAmountMinor", "100", "targetAmountMinor", "200")));
        assertThrows(ConfigurationException.class, () -> bank.fx().createQuote(map("sourceAccountId", "a1",
                "targetAccountId", "a1", "sourceAmountMinor", "100")));
        assertThrows(ConfigurationException.class, () -> bank.fx().createQuote(map("sourceAccountId", "a1",
                "targetAccountId", "a2", "sourceAmountMinor", 100L)));
        assertThrows(ConfigurationException.class, () -> bank.fx().createOrder(map("quoteId", "q1",
                "description", "x".repeat(141))));
        assertNothingSent();

        transport.enqueue(201, "{\"status\":\"success\",\"data\":{\"id\":\"q1\",\"sourceAmountMinor\":\"10000\"}}");
        bank.fx().createQuote(map("sourceAccountId", "a1", "targetAccountId", "a2", "targetAmountMinor", "10000"));
        RecordingTransport.Call call = transport.onlyApiCall();
        assertEquals(BASE + "/fx/quotes", call.url);
        assertNull(call.headers.get("Idempotency-Key"), "a quote takes no key");
        assertEquals(map("sourceAccountId", "a1", "targetAccountId", "a2", "targetAmountMinor", "10000"),
                Json.parseObject(call.body));
    }

    // ── Webhooks ──────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    void readsTheTransactionAndTransferWebhooks() {
        BankAccountTransactionReceived received = BankAccountTransactionReceived.fromEvent(SignedWebhooks.verified(
                "evt_tx", BankConnectEvents.TRANSACTION_RECEIVED, "{\"transactionId\":\"tx1\",\"accountId\":\"a1\","
                        + "\"direction\":\"CREDIT\",\"amountMinor\":\"2500\",\"currency\":\"TRY\",\"valueAt\":null,"
                        + "\"canonicalType\":\"TRANSFER_IN\",\"extra\":\"dropped\"}"));
        assertEquals("evt_tx", received.eventId());
        assertEquals("tx1", received.transactionId());
        assertEquals("2500", received.amountMinor());
        assertTrue(received.valueAt().isEmpty());
        assertFalse(received.data().containsKey("extra"), "only allow-listed fields are kept");

        BankTransferWebhookData failed = BankTransferWebhookData.fromEvent(SignedWebhooks.verified("evt_tr",
                BankTransferEvents.FAILED, "{\"transferId\":\"t1\",\"status\":\"FAILED\",\"amountMinor\":\"150075\","
                        + "\"failureCode\":\"bank_connect.transfer.insufficient_funds\",\"rail\":null}"));
        assertEquals(BankTransferEvents.FAILED, failed.eventType());
        assertEquals("t1", failed.transferId());
        assertEquals("bank_connect.transfer.insufficient_funds", failed.failureCode().orElseThrow());
        assertTrue(failed.rail().isEmpty());

        assertThrows(WebhookVerificationException.class, () -> BankTransferWebhookData.fromEvent(
                SignedWebhooks.verified("evt_p", "payment.bank_transfer.late_arrival", "{\"transferId\":\"t1\"}")));
        assertThrows(WebhookVerificationException.class, () -> BankAccountTransactionReceived.fromEvent(
                SignedWebhooks.verified("evt_n", BankConnectEvents.TRANSACTION_RECEIVED, "{\"amountMinor\":2500}")));
        assertEquals("bank_account.*", BankConnectEvents.PATTERN);
        assertEquals("bank_transfer.*", BankTransferEvents.PATTERN);
    }
}
