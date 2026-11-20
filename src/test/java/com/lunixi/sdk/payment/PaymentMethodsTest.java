package com.lunixi.sdk.payment;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.lunixi.sdk.LunixiClient;
import com.lunixi.sdk.LunixiConfig;
import com.lunixi.sdk.auth.CanonicalRequest;
import com.lunixi.sdk.auth.Ed25519KeyPair;
import com.lunixi.sdk.auth.Ed25519Signer;
import com.lunixi.sdk.exception.ConfigurationException;
import com.lunixi.sdk.exception.ServiceUnavailableException;
import com.lunixi.sdk.exception.ValidationException;
import com.lunixi.sdk.http.HttpTransport;
import com.lunixi.sdk.json.Json;
import com.lunixi.sdk.support.OrderFixture;
import com.lunixi.sdk.support.RecordingTransport;

/** Payment methods, direct payments, actions, promissory notes, bank transfer, split payments and reference data. */
class PaymentMethodsTest {
    static final String KEY = "persisted-operation-key-1";
    static final String BASE = "https://gateway.invalid/api/v1/payments";
    static final String OK = "{\"status\":\"success\",\"code\":\"SUCCESS\",\"data\":{}}";
    static final String EXCEPTION_ID = "3F2504E0-4F89-11D3-9A0C-0305E82C3301";
    RecordingTransport transport;
    LunixiClient client;
    Ed25519KeyPair keys;

    @BeforeEach void setup() {
        keys = Ed25519KeyPair.generate();
        transport = new RecordingTransport("/api/v1/auth/token", "fixture-only");
        client = clientWith(transport);
    }

    LunixiClient clientWith(HttpTransport http) {
        return LunixiClient.create(LunixiConfig.builder().baseUrl("https://gateway.invalid").keyId("test-key")
                .privateKey(keys.privateKeyPem()).transport(http).maxRetries(0).build());
    }

    static Map<String, Object> raw(String method, Object... extra) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("paymentMethod", method);
        body.put("amount", "10000");
        body.put("currency", "TRY");
        body.put("orderId", "order-1");
        body.putAll(OrderFixture.withOrderDetails(Map.of(), "10000"));
        for (int i = 0; i < extra.length; i += 2) { body.put((String) extra[i], extra[i + 1]); }
        return body;
    }

    /** Without the order details: for the builder rules that do not send. */
    static DirectMethodPaymentRequest bare() { return DirectMethodPaymentRequest.create("10000", "TRY", "order-1"); }

    static DirectMethodPaymentRequest request() {
        return bare().withBuyer(OrderFixture.BUYER).withBillingAddress(OrderFixture.ADDRESS)
                .withBasketItems(OrderFixture.basket("10000"));
    }

    static List<Map<String, Object>> schedule(String... amountDuePairs) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < amountDuePairs.length; i += 2) { rows.add(Map.of("amount", amountDuePairs[i], "dueDate", amountDuePairs[i + 1])); }
        return rows;
    }

    static void assertSigned(RecordingTransport.Call call) {
        assertNotNull(call.headers.get(CanonicalRequest.HEADER_SIGNATURE), call.url);
        assertEquals("test-key", call.headers.get(CanonicalRequest.HEADER_KEY_ID));
        assertNotNull(call.headers.get(CanonicalRequest.HEADER_NONCE));
    }

    void assertNothingSent() { assertTrue(transport.apiCalls().isEmpty(), "no request may be sent"); }

    // ── Constants ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test void constantValuesMatchTheGateway() {
        assertEquals(List.of("CARD", "BNPL", "SHOPPING_LOAN", "PROMISSORY_NOTE", "MEAL_CARD", "GIFT_CARD", "CASH",
                "CASH_ON_DELIVERY", "WALLET", "BANK_TRANSFER", "OPEN_BANKING", "MOBILE_BILLING", "QR_CODE", "CRYPTO",
                "OTHER"), PaymentMethod.ALL);
        assertEquals("QR_CODE", PaymentMethod.normalize(PaymentMethod.QR));
        assertEquals(List.of("mobileBilling", "wallet", "promissoryNote", "mealCard", "openBanking", "shoppingLoan",
                "bankTransfer"), PaymentMethod.METHOD_BLOCK_FIELDS);
        assertEquals(List.of("card", "installment", "installmentQuoteToken", "force3D"), PaymentMethod.CARD_ONLY_FIELDS);
        assertEquals(List.of("otp", "consentAccepted", "fields"), PaymentMethod.PAYMENT_ACTION_CONFIRM_FIELDS);
        assertEquals(27, PaymentMethod.DIRECT_METHOD_PAYMENT_FIELDS.size());
        assertTrue(PaymentMethod.DIRECT_METHOD_PAYMENT_FIELDS.containsAll(PaymentMethod.METHOD_BLOCK_FIELDS));
        assertEquals(100, PaymentMethod.SHOPPING_LOAN_MAX_BASKET_ITEMS);
        assertEquals("9999", PaymentMethod.BANK_TRANSFER_OTHER_BANKS_CODE);
        assertEquals(List.of("OTP", "REDIRECT", "DISPLAY", "APP_APPROVAL", "INPUT"), PaymentActionType.ALL);
        assertEquals(List.of("ROUTED", "OPTIONAL", "REQUIRED"), ProviderSelection.ALL);
        assertEquals(List.of("BUYER", "PLATFORM"), ProviderSelectionMode.ALL);
        assertEquals("bankTransfer", PaymentMethod.blockName(PaymentMethod.BANK_TRANSFER));
        assertEquals("mobileBilling", PaymentMethod.blockName(PaymentMethod.MOBILE_BILLING));
        assertEquals("qrCode", PaymentMethod.blockName(PaymentMethod.QR));
        assertEquals("card", PaymentMethod.blockName(PaymentMethod.CARD));
    }

    @Test void bankTransferPromissoryNoteAndSplitConstants() {
        assertEquals(200, PaymentsClient.BANK_TRANSFER_EXCEPTION_LIMIT_MAX);
        assertEquals(List.of("TEST", "LIVE"), PaymentsClient.BANK_TRANSFER_EXCEPTION_ENVIRONMENTS);
        assertEquals(List.of("LATE", "UNDERPAID", "OVERPAID"), PaymentBankTransferExceptionKind.ALL);
        assertEquals(List.of("PENDING_DECISION", "ACCEPTED", "REFUND_PENDING", "REFUNDED"), PaymentBankTransferExceptionStatus.ALL);
        assertEquals(List.of("payment.bank_transfer.partially_received", "payment.bank_transfer.underpaid",
                "payment.bank_transfer.overpaid", "payment.bank_transfer.late_arrival"), PaymentBankTransferEvents.ALL);
        assertEquals(List.of("payment.promissory_note.paid", "payment.promissory_note.protested",
                "payment.promissory_note.cancelled"), PromissoryNoteEvents.ALL);
        assertEquals(List.of("ISSUED", "PAID", "PROTESTED", "CANCELLED"), PromissoryNoteValues.Status.ALL);
        assertEquals(List.of("PROMISSORY_NOTE", "PAYMENT_ORDER"), PromissoryNoteValues.DocumentType.ALL);
        assertEquals(List.of("CANCELLED", "ALREADY_CLOSED", "NOT_CANCELLABLE", "FAILED"), PromissoryNoteValues.CancelResult.ALL);
        assertEquals(List.of("payment.promissory_note.not_cancellable", "payment.promissory_note.provider_unavailable",
                "payment.promissory_note.cancel_rejected"), PromissoryNoteValues.CancelErrorCode.ALL);
        assertEquals(List.of("tr-TR", "en-GB"), PromissoryNoteValues.DocumentLocale.ALL);
        assertEquals(24, PromissoryNoteValues.MAX_SCHEDULE_ITEMS);
        assertEquals(List.of("payment.split.completed", "payment.split.voided", "payment.split.compensating",
                "payment.split.outcome_pending", "payment.split.reconciliation_required",
                "payment.split.leg_refund_pending"), SplitPaymentEvents.ALL);
        assertEquals("payment.split.leg_events", SplitPaymentEvents.LEG_EVENTS_SUBSCRIPTION);
        assertEquals(List.of("mobileBilling", "wallet", "promissoryNote"), SplitPaymentValues.SPLIT_LEG_BLOCK_FIELDS);
        assertEquals(List.of("status", "currency", "createdFrom", "createdTo", "orderId", "ownerRef", "limit", "after"),
                SplitPaymentValues.LIST_SPLIT_PAYMENTS_FILTERS);
    }

    // ── Payment method list ───────────────────────────────────────────────────────────────────────────────────

    @Test void listPaymentMethodsIsSignedAndSendsTheCanonicalCode() throws Exception {
        transport.enqueue(200, "{\"status\":\"success\",\"data\":{\"methods\":[]}}");
        client.payments().listPaymentMethods(PaymentMethod.QR);
        RecordingTransport.Call call = transport.onlyApiCall();
        String path = "/api/v1/payments/methods?paymentMethod=QR_CODE";
        assertEquals("GET", call.method);
        assertEquals("https://gateway.invalid" + path, call.url);
        String canonical = CanonicalRequest.build("GET", path, call.headers.get(CanonicalRequest.HEADER_DATE),
                call.headers.get(CanonicalRequest.HEADER_NONCE), call.headers.get(CanonicalRequest.HEADER_DIGEST));
        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(Ed25519Signer.publicKeyFrom(keys.publicKeyPem()));
        verifier.update(canonical.getBytes(StandardCharsets.UTF_8));
        assertTrue(verifier.verify(Base64.getDecoder().decode(call.headers.get(CanonicalRequest.HEADER_SIGNATURE))));

        transport.enqueue(200, "{\"status\":\"success\",\"data\":{\"methods\":[]}}");
        client.payments().listPaymentMethods();
        assertEquals(BASE + "/methods", transport.apiCalls().get(1).url);
    }

    @Test void listPaymentMethodsRefusesAnInvalidCode() {
        assertThrows(ConfigurationException.class, () -> client.payments().listPaymentMethods("qr_code"));
        assertThrows(ConfigurationException.class, () -> client.payments().listPaymentMethods(""));
        assertNothingSent();
    }

    // ── direct/payment: Para Transferi ────────────────────────────────────────────────────────────────────────

    @Test void bankTransferSendsTheBlockWithoutProviderCodeAndReadsTheDisplayInstruction() {
        transport.enqueue(202, "{\"status\":\"partial\",\"code\":\"PAYMENT_PENDING\",\"data\":{\"paymentId\":\"pay_1\","
                + "\"status\":\"PROCESSING\",\"paymentMethod\":\"BANK_TRANSFER\",\"action\":{\"id\":\"act_1\",\"type\":\"DISPLAY\","
                + "\"expiresAt\":\"2026-11-16T20:59:59Z\",\"bankTransfer\":{\"bankCode\":\"0062\",\"bankName\":\"Example Bank\","
                + "\"holderName\":\"Example Shop Ltd\",\"iban\":\"TR000000000000000000000000\",\"amountMinor\":\"10000\","
                + "\"currency\":\"TRY\",\"paymentCode\":\"LNX123456\",\"expiresAt\":\"2026-11-16T20:59:59Z\",\"otherBank\":false}}}}");
        Map<String, Object> response = client.payments().directMethodPayment(
                request().withMethodFields(PaymentMethod.BANK_TRANSFER, null, Map.of("bankCode", "0062")), KEY);
        RecordingTransport.Call call = transport.onlyApiCall();
        assertEquals("POST", call.method);
        assertEquals(BASE + "/direct/payment", call.url);
        assertEquals(KEY, call.headers.get("Idempotency-Key"));
        assertSigned(call);
        assertEquals(CanonicalRequest.digestForBody(call.body), call.headers.get(CanonicalRequest.HEADER_DIGEST));
        assertEquals("{\"paymentMethod\":\"BANK_TRANSFER\",\"amount\":\"10000\",\"currency\":\"TRY\",\"orderId\":\"order-1\","
                + OrderFixture.json("10000") + ",\"bankTransfer\":{\"bankCode\":\"0062\"}}", call.body);

        PaymentBankTransferInstruction instruction = PaymentBankTransferInstruction.fromResponse(response).orElseThrow();
        assertEquals("0062", instruction.bankCode());
        assertEquals("TR000000000000000000000000", instruction.iban());
        assertEquals("10000", instruction.amountMinor());
        assertEquals("LNX123456", instruction.paymentCode());
        assertEquals("Example Shop Ltd", instruction.holderName());
        assertFalse(instruction.otherBank());
        assertTrue(PaymentBankTransferInstruction.fromAction(Map.of("type", "OTP", "bankTransfer", Map.of())).isEmpty());
        assertTrue(PaymentBankTransferInstruction.fromAction(null).isEmpty());
        assertTrue(PaymentBankTransferInstruction.fromResponse(Map.of("data", Map.of("status", "CAPTURED"))).isEmpty());
    }

    @Test void bankTransferOtherBanksCodeAndProviderCodeIsRefused() {
        Map<String, Object> body = request().withMethodFields(PaymentMethod.BANK_TRANSFER, null,
                Map.of("bankCode", PaymentMethod.BANK_TRANSFER_OTHER_BANKS_CODE)).toBody();
        assertEquals(Map.of("bankCode", "9999"), body.get("bankTransfer"));
        assertFalse(body.containsKey("providerCode"));
        assertThrows(ConfigurationException.class, () -> request().withMethodFields(PaymentMethod.BANK_TRANSFER,
                "SOME_PROVIDER", Map.of("bankCode", "0062")));
        assertThrows(ConfigurationException.class, () -> request().withMethodFields(PaymentMethod.BANK_TRANSFER, null,
                Map.of("providerCode", "SOME_PROVIDER", "bankCode", "0062")));
        assertThrows(ConfigurationException.class, () -> client.payments().directMethodPayment(raw("BANK_TRANSFER",
                "bankTransfer", Map.of("providerCode", "SOME_PROVIDER", "bankCode", "0062")), KEY));
        assertNothingSent();
    }

    // ── direct/payment: body rules ────────────────────────────────────────────────────────────────────────────

    @Test void rawBodiesBreakingTheBlockRulesAreRefusedBeforeSending() {
        Map<String, Object> gsm = Map.of("gsmNumber", "+905321234567");
        var payments = client.payments();
        assertThrows(ConfigurationException.class, () -> payments.directMethodPayment(raw("MOBILE_BILLING",
                "mobileBilling", gsm, "wallet", Map.of("providerCode", "WALLET_A")), KEY), "another method's block");
        assertThrows(ConfigurationException.class, () -> payments.directMethodPayment(raw("WALLET",
                "wallet", Map.of("providerCode", "WALLET_A"), "installment", 2), KEY), "card field on a non-card method");
        assertThrows(ConfigurationException.class, () -> payments.directMethodPayment(raw("MOBILE_BILLING",
                "mobileBilling", gsm, "unknownField", "x"), KEY), "unknown field");
        assertThrows(ConfigurationException.class, () -> payments.directMethodPayment(raw("MOBILE_BILLING"), KEY), "missing block");
        assertThrows(ConfigurationException.class, () -> payments.directMethodPayment(raw("MOBILE_BILLING",
                "mobileBilling", gsm, "price", "10000"), KEY), "price");
        assertThrows(ConfigurationException.class, () -> payments.directMethodPayment(raw("GIFT_CARD"), KEY), "method without a block");
        assertThrows(ConfigurationException.class, () -> payments.directMethodPayment(raw("CARD"), KEY), "card without card");
        assertThrows(ConfigurationException.class, () -> payments.directMethodPayment(raw("mobile_billing",
                "mobileBilling", gsm), KEY), "lower-case code");
        assertThrows(ConfigurationException.class, () -> payments.directMethodPayment(raw("MOBILE_BILLING",
                "mobileBilling", gsm, "preauth", "false"), KEY), "preauth must be a boolean");
        Map<String, Object> noAmount = raw("MOBILE_BILLING", "mobileBilling", gsm);
        noAmount.remove("amount");
        assertThrows(ConfigurationException.class, () -> payments.directMethodPayment(noAmount, KEY), "amount required");
        assertNothingSent();
    }

    @Test void aDirectPaymentWithoutTheOrderDetailsIsNotSent() {
        ConfigurationException refused = assertThrows(ConfigurationException.class, () -> client.payments()
                .directMethodPayment(bare().withMethodFields(PaymentMethod.BANK_TRANSFER, null, Map.of("bankCode", "0062")), KEY));
        assertEquals("A direct method payment requires the order details: buyer, billingAddress, basketItems.",
                refused.getMessage());
        Map<String, Object> noIp = new LinkedHashMap<>(OrderFixture.BUYER);
        noIp.remove("ip");
        Map<String, Object> physical = raw("BANK_TRANSFER", "bankTransfer", Map.of("bankCode", "0062"), "buyer", noIp,
                "basketItems", List.of(Map.of("id", "SKU-1", "name", "Lamp", "category1", "Home", "itemType", "PHYSICAL",
                        "price", "10000")));
        refused = assertThrows(ConfigurationException.class, () -> client.payments().directMethodPayment(physical, KEY));
        assertEquals("A direct method payment requires the order details: buyer.ip, shippingAddress.", refused.getMessage());
        assertNothingSent();
    }

    @Test void builderRulesAndTheIdempotencyKeyAreCheckedBeforeSending() {
        var payments = client.payments();
        assertThrows(ConfigurationException.class, () -> request().withMethodFields(PaymentMethod.CARD, null));
        assertThrows(ConfigurationException.class, () -> request().withMethodFields(PaymentMethod.GIFT_CARD, null));
        assertThrows(ConfigurationException.class, () -> request().withMethodFields(PaymentMethod.WALLET, "WALLET_A")
                .withForce3D(true).toBody());
        assertThrows(ConfigurationException.class, () -> request().toBody(), "no method chosen");
        assertThrows(ConfigurationException.class, () -> DirectMethodPaymentRequest.create("100.50", "TRY", "order-1"));
        assertThrows(ConfigurationException.class, () -> DirectMethodPaymentRequest.create("0", "TRY", "order-1"));
        DirectMethodPaymentRequest valid = request().withMethodFields(PaymentMethod.MOBILE_BILLING, null,
                Map.of("gsmNumber", "+905321234567"));
        assertThrows(ConfigurationException.class, () -> payments.directMethodPayment(valid, null));
        assertThrows(ConfigurationException.class, () -> payments.directMethodPayment(valid, ""));
        assertThrows(ConfigurationException.class, () -> payments.directMethodPayment(valid, "short"));
        assertNothingSent();
    }

    @Test void cardPaymentCarriesTheCardFieldsOnlyWithCard() {
        Map<String, Object> body = request().withCard(Map.of("cardUserKey", "cuk_1", "cardToken", "ctk_1"))
                .withForce3D(true).withInstallment(3, "quote-token").withCallbackUrl("https://shop.invalid/return").toBody();
        assertEquals("CARD", body.get("paymentMethod"));
        assertEquals(true, body.get("force3D"));
        assertEquals(3, body.get("installment"));
        assertEquals(Map.of("cardUserKey", "cuk_1", "cardToken", "ctk_1"), body.get("card"));
    }

    @Test void shoppingLoanNeedsOneToOneHundredBasketItems() {
        Map<String, Object> item = Map.of("id", "SKU-1", "name", "Phone", "category1", "Phones", "itemType", "VIRTUAL",
                "price", "100", "productCategory", ProductCategory.MOBILE_PHONE);
        DirectMethodPaymentRequest loan = bare().withMethodFields(PaymentMethod.SHOPPING_LOAN, null, Map.of("bankCode", "0062"));
        assertThrows(ConfigurationException.class, loan::toBody, "no basket");
        assertThrows(ConfigurationException.class, () -> loan.withBasketItems(List.of()).toBody(), "0 items");
        List<Map<String, Object>> tooMany = new ArrayList<>();
        for (int i = 0; i < 101; i++) { tooMany.add(item); }
        assertThrows(ConfigurationException.class, () -> loan.withBasketItems(tooMany).toBody(), "101 items");
        assertThrows(ConfigurationException.class, () -> client.payments().directMethodPayment(raw("SHOPPING_LOAN",
                "shoppingLoan", Map.of("bankCode", "0062"), "basketItems", tooMany), KEY));
        assertEquals(100, loan.withBasketItems(tooMany.subList(0, 100)).toBody().get("basketItems") instanceof List<?> list ? list.size() : -1);
        assertNothingSent();
    }

    // ── Promissory note plan ──────────────────────────────────────────────────────────────────────────────────

    @Test void promissoryNotePlanRules() {
        DirectMethodPaymentRequest notes = request().withMethodFields(PaymentMethod.PROMISSORY_NOTE, "NOTE_PROVIDER",
                Map.of("noteCount", "3"));
        assertThrows(ConfigurationException.class, () -> notes.withPromissoryNoteSchedule(
                schedule("5000", "2027-01-15", "5000", "2027-02-15")).toBody(), "schedule and noteCount");
        DirectMethodPaymentRequest planned = request().withMethodFields(PaymentMethod.PROMISSORY_NOTE, "NOTE_PROVIDER");
        assertThrows(ConfigurationException.class, () -> planned.withPromissoryNoteSchedule(
                schedule("5000", "2027-01-15", "4000", "2027-02-15")).toBody(), "total mismatch");
        assertThrows(ConfigurationException.class, () -> planned.withPromissoryNoteSchedule(
                schedule("5000", "2027-01-15", "5000", "2027-02-30")), "not a calendar day");
        assertThrows(ConfigurationException.class, () -> planned.withPromissoryNoteSchedule(
                schedule("5000", "2027-02-15", "5000", "2027-01-15")), "descending");
        assertThrows(ConfigurationException.class, () -> planned.withPromissoryNoteSchedule(
                schedule("5000", "2027-01-15", "5000", "2027-1-15")), "date format");
        assertThrows(ConfigurationException.class, () -> planned.withPromissoryNoteSchedule(
                List.of(Map.of("amount", "10000", "dueDate", "2027-01-15", "note", "x"))), "unknown row field");
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < 25; i++) { rows.add(Map.of("amount", "400", "dueDate", "2027-01-15")); }
        assertThrows(ConfigurationException.class, () -> planned.withPromissoryNoteSchedule(rows), "25 rows");
        assertThrows(ConfigurationException.class, () -> planned.withPromissoryNoteCount(25));
        assertThrows(ConfigurationException.class, () -> planned.withPromissoryNoteCount(0));
        assertThrows(ConfigurationException.class, () -> request().withMethodFields(PaymentMethod.PROMISSORY_NOTE,
                "NOTE_PROVIDER").toBody(), "no plan");
        assertThrows(ConfigurationException.class, () -> request().withMethodFields(PaymentMethod.PROMISSORY_NOTE,
                "NOTE_PROVIDER", Map.of("schedule", List.of())), "schedule in the fields");
        assertThrows(ConfigurationException.class, () -> request().withMethodFields(PaymentMethod.WALLET, "WALLET_A")
                .withPromissoryNoteCount(2).toBody(), "plan on another method");
        Map<String, Object> both = new LinkedHashMap<>();
        both.put("noteCount", "2");
        both.put("schedule", schedule("10000", "2027-01-15"));
        assertThrows(ConfigurationException.class, () -> client.payments().directMethodPayment(raw("PROMISSORY_NOTE",
                "promissoryNote", both), KEY), "raw schedule and noteCount");
        assertEquals("3", request().withMethodFields(PaymentMethod.PROMISSORY_NOTE, "NOTE_PROVIDER")
                .withPromissoryNoteCount(3).toBody().get("promissoryNote") instanceof Map<?, ?> block ? block.get("noteCount") : null);
        assertEquals("4", request().withMethodFields(PaymentMethod.PROMISSORY_NOTE, "NOTE_PROVIDER",
                Map.of("noteCount", 4)).toBody().get("promissoryNote") instanceof Map<?, ?> block ? block.get("noteCount") : null);
        assertNothingSent();
    }

    @Test void validPromissoryNoteScheduleIsSentAsMinorUnitStrings() {
        transport.enqueue(200, "{\"status\":\"success\",\"data\":{\"paymentId\":\"pay_2\",\"status\":\"CAPTURED\"}}");
        client.payments().directMethodPayment(request()
                .withMethodFields(PaymentMethod.PROMISSORY_NOTE, "NOTE_PROVIDER", Map.of("debtorType", "INDIVIDUAL"))
                .withPromissoryNoteSchedule(schedule("5000", "2027-01-15", "5000", "2027-01-15")), KEY);
        RecordingTransport.Call call = transport.onlyApiCall();
        assertSigned(call);
        assertEquals("{\"paymentMethod\":\"PROMISSORY_NOTE\",\"amount\":\"10000\",\"currency\":\"TRY\",\"orderId\":\"order-1\","
                + OrderFixture.json("10000") + ",\"promissoryNote\":{\"providerCode\":\"NOTE_PROVIDER\",\"debtorType\":\"INDIVIDUAL\",\"schedule\":["
                + "{\"amount\":\"5000\",\"dueDate\":\"2027-01-15\"},{\"amount\":\"5000\",\"dueDate\":\"2027-01-15\"}]}}", call.body);
    }

    // ── Method block structure ────────────────────────────────────────────────────────────────────────────────

    @Test void methodBlockStructureIsChecked() {
        Map<String, Object> wide = new LinkedHashMap<>();
        for (int i = 1; i <= 33; i++) { wide.put("field" + i, "x"); }
        assertThrows(ConfigurationException.class, () -> mobile(wide), "33 keys");
        wide.remove("field33");
        mobile(wide);
        assertThrows(ConfigurationException.class, () -> mobile(Map.of("gsmNumber", "x".repeat(1025))), "1025 characters");
        mobile(Map.of("gsmNumber", "x".repeat(1024)));
        List<Map<String, Object>> group = new ArrayList<>();
        for (int i = 0; i < 11; i++) { group.add(Map.of("name", "Guarantor")); }
        assertThrows(ConfigurationException.class, () -> mobile(Map.of("guarantors", group)), "GROUP of 11");
        mobile(Map.of("guarantors", group.subList(0, 10)));
        assertThrows(ConfigurationException.class, () -> mobile(Map.of("__proto__", "x")), "__proto__");
        assertThrows(ConfigurationException.class, () -> mobile(Map.of("constructor", "x")), "constructor");
        assertThrows(ConfigurationException.class, () -> mobile(Map.of("gsm-number", "x")), "invalid key");
        assertThrows(ConfigurationException.class, () -> mobile(Map.of("amount", Double.NaN)), "non-finite number");
        Map<String, Object> address = new LinkedHashMap<>();
        for (int i = 1; i <= 17; i++) { address.put("part" + i, "x"); }
        assertThrows(ConfigurationException.class, () -> mobile(Map.of("address", address)), "ADDRESS of 17 keys");
        assertThrows(ConfigurationException.class, () -> mobile(Map.of("address", Map.of("line", List.of()))), "nested list");
        assertThrows(ConfigurationException.class, () -> request().withMethodFields(PaymentMethod.WALLET, "bad code!"), "providerCode pattern");
        assertThrows(ConfigurationException.class, () -> client.payments().directMethodPayment(raw("MOBILE_BILLING",
                "mobileBilling", Map.of("__proto__", "x")), KEY), "raw block structure");
        assertNothingSent();
    }

    static Map<String, Object> mobile(Map<String, ?> fields) {
        return request().withMethodFields(PaymentMethod.MOBILE_BILLING, null, fields).toBody();
    }

    // ── Payment actions ───────────────────────────────────────────────────────────────────────────────────────

    @Test void confirmSendsOnlyTheMerchantFieldsOnTheEncodedPath() {
        transport.enqueue(200, OK);
        client.payments().confirmPaymentAction("pay/1", "act_1", Map.of("otp", "123456"));
        RecordingTransport.Call call = transport.onlyApiCall();
        assertEquals("POST", call.method);
        assertEquals(BASE + "/pay%2F1/actions/act_1/confirm", call.url);
        assertEquals("{\"otp\":\"123456\"}", call.body);
        assertSigned(call);
        assertNull(call.headers.get("Idempotency-Key"));

        transport.enqueue(200, OK);
        client.payments().resendPaymentAction("pay_1", "act_1");
        RecordingTransport.Call resend = transport.apiCalls().get(1);
        assertEquals(BASE + "/pay_1/actions/act_1/resend", resend.url);
        assertNull(resend.body);
        assertSigned(resend);
    }

    @Test void confirmRefusesUnknownFieldsTokensAndNonDigitCodes() {
        var payments = client.payments();
        assertThrows(ConfigurationException.class, () -> payments.confirmPaymentAction("pay_1", "act_1", Map.of("unknown", "1")));
        assertThrows(ConfigurationException.class, () -> payments.confirmPaymentAction("pay_1", "act_1", Map.of("token", "checkout-token")));
        assertThrows(ConfigurationException.class, () -> payments.confirmPaymentAction("pay_1", "act_1",
                Map.of("otp", "1234", "checkoutSessionProof", "proof")));
        assertThrows(ConfigurationException.class, () -> payments.confirmPaymentAction("pay_1", "act_1", Map.of("otp", "12a4")));
        assertThrows(ConfigurationException.class, () -> payments.confirmPaymentAction("pay_1", "act_1", Map.of("otp", "1".repeat(17))));
        assertThrows(ConfigurationException.class, () -> payments.confirmPaymentAction("pay_1", "act_1", Map.of("consentAccepted", "true")));
        assertThrows(ConfigurationException.class, () -> payments.confirmPaymentAction("pay_1", "act_1", Map.of()));
        assertThrows(ConfigurationException.class, () -> payments.confirmPaymentAction("pay_1", "act_1",
                Map.of("fields", Map.of("providerCode", "X"))));
        assertThrows(ConfigurationException.class, () -> payments.confirmPaymentAction(" ", "act_1", Map.of("otp", "1234")));
        assertThrows(ConfigurationException.class, () -> payments.resendPaymentAction("pay_1", ""));
        assertNothingSent();
    }

    // ── Bank transfer exceptions ──────────────────────────────────────────────────────────────────────────────

    @Test void bankTransferExceptionFiltersAreCheckedAndSentAsQuery() {
        var payments = client.payments();
        assertThrows(ConfigurationException.class, () -> payments.listBankTransferExceptions("PROD", null, null));
        assertThrows(ConfigurationException.class, () -> payments.listBankTransferExceptions(null, null, 0));
        assertThrows(ConfigurationException.class, () -> payments.listBankTransferExceptions(null, null, 201));
        assertThrows(ConfigurationException.class, () -> payments.listBankTransferExceptions(null, "OPEN", null));
        assertNothingSent();

        transport.enqueue(200, "{\"status\":\"success\",\"data\":{\"items\":[{\"id\":\"" + EXCEPTION_ID + "\",\"kind\":\"UNDERPAID\","
                + "\"status\":\"PENDING_DECISION\",\"amountMinor\":\"2500\",\"currency\":\"TRY\"}]}}");
        payments.listBankTransferExceptions("LIVE", PaymentBankTransferExceptionStatus.PENDING_DECISION, 50);
        RecordingTransport.Call call = transport.onlyApiCall();
        assertEquals(BASE + "/bank-transfer/exceptions?environment=LIVE&status=PENDING_DECISION&limit=50", call.url);
        assertSigned(call);

        transport.enqueue(200, "{\"status\":\"success\",\"data\":{\"items\":[]}}");
        payments.listBankTransferExceptions();
        assertEquals(BASE + "/bank-transfer/exceptions", transport.apiCalls().get(1).url);
    }

    @Test void bankTransferExceptionDecisionsNeedAUuid() {
        var payments = client.payments();
        assertThrows(ConfigurationException.class, () -> payments.acceptBankTransferException("exc_1"));
        assertThrows(ConfigurationException.class, () -> payments.markBankTransferExceptionRefunded("../" + EXCEPTION_ID));
        assertThrows(ConfigurationException.class, () -> payments.acceptBankTransferException(null));
        assertNothingSent();

        transport.enqueue(200, "{\"status\":\"success\",\"data\":null}");
        payments.acceptBankTransferException(EXCEPTION_ID);
        transport.enqueue(200, "{\"status\":\"success\",\"data\":null}");
        payments.markBankTransferExceptionRefunded(EXCEPTION_ID.toLowerCase(java.util.Locale.ROOT));
        List<RecordingTransport.Call> calls = transport.apiCalls();
        assertEquals(BASE + "/bank-transfer/exceptions/" + EXCEPTION_ID + "/accept", calls.get(0).url);
        assertEquals(BASE + "/bank-transfer/exceptions/" + EXCEPTION_ID.toLowerCase(java.util.Locale.ROOT) + "/refunded", calls.get(1).url);
        for (RecordingTransport.Call call : calls) {
            assertEquals("POST", call.method);
            assertNull(call.body);
            assertSigned(call);
        }
    }

    // ── Promissory note endpoints ─────────────────────────────────────────────────────────────────────────────

    @Test void promissoryNoteDocumentReturnsThePdfBytes() {
        byte[] pdf = {0x25, 0x50, 0x44, 0x46, 0x2d, (byte) 0xe2, (byte) 0xe3, (byte) 0xff, 0x00, 0x0a};
        transport.enqueueBinary(200, Map.of("content-type", "application/pdf"), pdf);
        PromissoryNoteDocument document = client.payments().downloadPromissoryNoteDocument("pay_1",
                PromissoryNoteValues.DocumentLocale.EN);
        RecordingTransport.Call call = transport.onlyApiCall();
        assertTrue(call.binary);
        assertEquals("GET", call.method);
        assertEquals(BASE + "/pay_1/promissory-notes/document?locale=en-GB", call.url);
        assertEquals("application/pdf, application/json", call.headers.get("Accept"));
        assertSigned(call);
        assertEquals("application/pdf", document.contentType());
        assertArrayEquals(pdf, document.bytes());
        assertEquals(pdf.length, document.size());
    }

    @Test void promissoryNoteDocumentErrorsAreReadAsTheEnvelope() {
        assertThrows(ConfigurationException.class, () -> client.payments().downloadPromissoryNoteDocument("pay_1", "de-DE"));
        assertThrows(ConfigurationException.class, () -> client.payments().downloadPromissoryNoteDocument(""));
        assertNothingSent();
        transport.enqueue(503, Map.of("content-type", "application/json"), "{\"status\":\"failure\","
                + "\"code\":\"payment.promissory_note.document_unavailable\",\"message\":\"Try again later.\",\"data\":null}");
        ServiceUnavailableException error = assertThrows(ServiceUnavailableException.class,
                () -> client.payments().downloadPromissoryNoteDocument("pay_1"));
        assertEquals("payment.promissory_note.document_unavailable", error.code().orElse(null));
        assertEquals(BASE + "/pay_1/promissory-notes/document", transport.onlyApiCall().url);
    }

    @Test void aTextOnlyTransportRefusesBinaryDownloads() {
        HttpTransport textOnly = transport::send;
        LunixiClient plain = clientWith(textOnly);
        assertThrows(ConfigurationException.class, () -> plain.payments().downloadPromissoryNoteDocument("pay_1"));
        assertNothingSent();
    }

    @Test void promissoryNoteListAndCancel() {
        var payments = client.payments();
        assertThrows(ConfigurationException.class, () -> payments.cancelPromissoryNotes("pay_1", List.of("SN1")));
        assertThrows(ConfigurationException.class, () -> payments.cancelPromissoryNotes("pay_1", List.of()));
        assertThrows(ConfigurationException.class, () -> payments.cancelPromissoryNotes("pay_1", List.of("SN 0001")));
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 25; i++) { many.add("SN-" + (1000 + i)); }
        assertThrows(ConfigurationException.class, () -> payments.cancelPromissoryNotes("pay_1", many));
        assertThrows(ConfigurationException.class, () -> payments.getPromissoryNotes(null));
        assertNothingSent();

        transport.enqueue(200, "{\"status\":\"success\",\"data\":{\"notes\":[{\"serialNumber\":\"SN-0001\",\"amount\":\"5000\"}],"
                + "\"totalAmount\":\"5000\"}}");
        payments.getPromissoryNotes("pay_1");
        transport.enqueue(200, OK);
        payments.cancelPromissoryNotes("pay_1", List.of("SN-0001", "SN-0002"));
        transport.enqueue(200, OK);
        payments.cancelPromissoryNotes("pay_1");
        List<RecordingTransport.Call> calls = transport.apiCalls();
        assertEquals("GET", calls.get(0).method);
        assertEquals(BASE + "/pay_1/promissory-notes", calls.get(0).url);
        assertEquals(BASE + "/pay_1/promissory-notes/cancel", calls.get(1).url);
        assertEquals("{\"serialNumbers\":[\"SN-0001\",\"SN-0002\"]}", calls.get(1).body);
        assertNull(calls.get(2).body);
        calls.forEach(PaymentMethodsTest::assertSigned);
    }

    // ── Split payments ────────────────────────────────────────────────────────────────────────────────────────

    static Map<String, Object> split(Map<String, Object> methodLeg) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("cardToken", "ctok_1");
        card.put("amount", "6000");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("totalAmount", "10000");
        body.put("currency", "TRY");
        body.put("ownerRef", "customer-1");
        body.put("legs", List.of(card, methodLeg));
        return OrderFixture.withOrderDetails(body, "10000");
    }

    @Test void aSplitPaymentCarriesTheOrderDetailsAndIsNotSentWithoutThem() {
        Map<String, Object> body = split(Map.of("paymentMethod", "MOBILE_BILLING", "amount", "4000",
                "mobileBilling", Map.of("providerCode", "PROVIDER_A", "gsmNumber", "+905321234567")));
        body.put("shippingAddress", OrderFixture.ADDRESS);
        assertTrue(SplitPaymentValues.SPLIT_PAYMENT_FIELDS.containsAll(List.of("buyer", "billingAddress",
                "shippingAddress", "basketItems")));
        transport.enqueue(200, "{\"status\":\"success\",\"data\":{\"splitPaymentId\":\"sp_1\",\"status\":\"COMPLETED\"}}");
        client.payments().createSplitPayment(body, KEY);
        assertTrue(transport.onlyApiCall().body.contains("\"shippingAddress\":"));
        body.remove("buyer");
        body.remove("billingAddress");
        ConfigurationException refused = assertThrows(ConfigurationException.class,
                () -> client.payments().createSplitPayment(body, KEY));
        assertEquals("A split payment requires the order details: buyer, billingAddress.", refused.getMessage());
        assertEquals(1, transport.apiCalls().size());
    }

    @Test void bankTransferOpenBankingAndOtherNonLegMethodsCannotBeSplitLegs() {
        var payments = client.payments();
        for (String method : List.of("BANK_TRANSFER", "OPEN_BANKING", "MEAL_CARD", "SHOPPING_LOAN")) {
            String block = PaymentMethod.blockName(method);
            assertThrows(ConfigurationException.class, () -> payments.createSplitPayment(split(Map.of("paymentMethod", method,
                    "amount", "4000", block, Map.of("providerCode", "PROVIDER_A", "bankCode", "0062"))), KEY), method + " with block");
            assertThrows(ConfigurationException.class, () -> payments.createSplitPayment(split(Map.of("paymentMethod", method,
                    "amount", "4000")), KEY), method + " without block");
        }
        assertThrows(ConfigurationException.class, () -> payments.createSplitPayment(split(Map.of("paymentMethod", "MOBILE_BILLING",
                "amount", "4000", "mobileBilling", Map.of("gsmNumber", "+905321234567"))), KEY), "leg without providerCode");
        assertThrows(ConfigurationException.class, () -> payments.createSplitPayment(split(Map.of("paymentMethod", "MOBILE_BILLING",
                "amount", "3000", "mobileBilling", Map.of("providerCode", "PROVIDER_A"))), KEY), "legs do not add up");
        assertThrows(ConfigurationException.class, () -> payments.createSplitPayment(split(Map.of("paymentMethod", "MOBILE_BILLING",
                "amount", "4000", "mobileBilling", Map.of("providerCode", "PROVIDER_A"))), null), "no idempotency key");
        assertNothingSent();
    }

    @Test void runningSplitPaymentAnswerIsReturnedNotThrown() {
        transport.enqueue(400, "{\"status\":\"failure\",\"code\":\"AWAITING_ACTION\",\"statusCode\":400,"
                + "\"data\":{\"splitPaymentId\":\"sp_1\",\"status\":\"AWAITING_ACTION\"}}");
        Map<String, Object> response = client.payments().createSplitPayment(split(Map.of("paymentMethod", "MOBILE_BILLING",
                "amount", "4000", "mobileBilling", Map.of("providerCode", "PROVIDER_A", "gsmNumber", "+905321234567"))), KEY);
        assertEquals("sp_1", ((Map<?, ?>) response.get("data")).get("splitPaymentId"));
        RecordingTransport.Call call = transport.onlyApiCall();
        assertEquals(BASE + "/split", call.url);
        assertEquals(KEY, call.headers.get("Idempotency-Key"));
        assertSigned(call);

        transport.enqueue(400, "{\"status\":\"failure\",\"code\":\"VALIDATION_ERROR\",\"data\":null}");
        assertThrows(ValidationException.class, () -> client.payments().createSplitPayment(split(Map.of("paymentMethod",
                "WALLET", "amount", "4000", "wallet", Map.of("providerCode", "WALLET_A"))), KEY));
    }

    @Test void splitPaymentListSendsRepeatedFilters() {
        assertThrows(ConfigurationException.class, () -> client.payments().listSplitPayments(Map.of("page", 2)));
        assertThrows(ConfigurationException.class, () -> client.payments().listSplitPayments(Map.of("limit", 101)));
        assertThrows(ConfigurationException.class, () -> client.payments().listSplitPayments(Map.of("status", "DONE")));
        assertNothingSent();
        transport.enqueue(200, "{\"status\":\"success\",\"data\":[]}");
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("limit", 25);
        filters.put("status", List.of("COMPLETED", "VOIDED"));
        client.payments().listSplitPayments(filters);
        assertEquals(BASE + "/split?status=COMPLETED&status=VOIDED&limit=25", transport.onlyApiCall().url);
    }

    // ── Reference data ────────────────────────────────────────────────────────────────────────────────────────

    @Test void addressDictionaryChecksTheCodes() {
        assertThrows(ConfigurationException.class, () -> client.reference().listDistricts("82"));
        assertThrows(ConfigurationException.class, () -> client.reference().listDistricts("6"));
        assertThrows(ConfigurationException.class, () -> client.reference().listNeighborhoods("12a"));
        assertNothingSent();
        String items = "{\"status\":\"success\",\"data\":{\"items\":[]}}";
        transport.enqueue(200, items).enqueue(200, items).enqueue(200, items);
        client.reference().listProvinces();
        client.reference().listDistricts("06");
        client.reference().listNeighborhoods("1234");
        List<RecordingTransport.Call> calls = transport.apiCalls();
        String base = "https://gateway.invalid/api/v1/reference/tr-address";
        assertEquals(base + "/provinces", calls.get(0).url);
        assertEquals(base + "/districts?provinceCode=06", calls.get(1).url);
        assertEquals(base + "/neighborhoods?districtCode=1234", calls.get(2).url);
    }
}
