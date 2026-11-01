package com.lunixi.sdk.physicalpos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.lunixi.sdk.LunixiClient;
import com.lunixi.sdk.LunixiConfig;
import com.lunixi.sdk.auth.Ed25519KeyPair;
import com.lunixi.sdk.exception.ConfigurationException;
import com.lunixi.sdk.exception.LunixiException;
import com.lunixi.sdk.json.Json;
import com.lunixi.sdk.physicalpos.PhysicalPosSupport.Shape;
import com.lunixi.sdk.support.RecordingTransport;

/**
 * Cross-SDK parity: the shared fixture {@code physical-pos-parity.json} (byte-identical copies in the Node, PHP,
 * Java and Python SDKs) carries the gateway's card-data rules, PAN value cases, key rules, the closed field and
 * filter lists and the "nothing started" codes. Expected values were computed from the gateway sources, so a rule
 * change there turns every SDK red until it is ported.
 */
class PhysicalPosParityTest {

    static Map<String, Object> fixture;

    @BeforeAll
    static void load() throws IOException {
        String text;
        try (InputStream in = PhysicalPosParityTest.class.getResourceAsStream("/physical-pos-parity.json")) {
            text = in != null ? new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    : Files.readString(Path.of("src/test/resources/physical-pos-parity.json"), StandardCharsets.UTF_8);
        }
        fixture = Json.parseObject(text);
        assertFalse(fixture.isEmpty(), "physical-pos-parity.json is empty");
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> rows(String name) {
        List<Map<String, Object>> rows = (List<Map<String, Object>>) fixture.get(name);
        assertFalse(rows.isEmpty(), name);
        return rows;
    }

    @SuppressWarnings("unchecked")
    static Map<String, List<String>> lists(String name) {
        return (Map<String, List<String>>) fixture.get(name);
    }

    @Test
    void cardDataKeyCasesMatchTheGatewayTokenRule() {
        for (Map<String, Object> row : rows("cardDataKeyCases")) {
            String key = (String) row.get("key");
            assertEquals(row.get("cardData"), PhysicalPosSupport.isCardDataKey(key), key);
        }
    }

    @Test
    void cardDataPathCasesMatchTheGatewayGuard() {
        for (Map<String, Object> row : rows("cardDataPathCases")) {
            assertEquals(row.get("field"), PhysicalPosSupport.findCardDataPath(row.get("body"), "(body)"),
                    String.valueOf(row.get("body").hashCode()));
        }
    }

    @Test
    void panValueCasesMatchTheServiceScan() {
        for (Map<String, Object> row : rows("panValueCases")) {
            boolean itemText = "ITEM_TEXT".equals(row.get("mode"));
            // The message names the row by index-free mode only: values are synthetic but stay out of reports.
            assertEquals(row.get("pan"), PhysicalPosSupport.containsPanLikeValue((String) row.get("value"), itemText),
                    "mode " + row.get("mode") + ", length " + ((String) row.get("value")).length());
        }
    }

    @Test
    void idempotencyKeyCasesAreCheckedAsGivenWithoutTrimming() {
        for (Map<String, Object> row : rows("idempotencyKeyCases")) {
            String key = (String) row.get("key");
            if (Boolean.TRUE.equals(row.get("valid"))) {
                assertEquals(key, PhysicalPosSupport.idempotencyKey(key));
                assertEquals(key, PhysicalPosSupport.requiredIdempotencyKey(key, "createPayment"));
            } else {
                assertThrows(ConfigurationException.class, () -> PhysicalPosSupport.idempotencyKey(key),
                        "length " + key.length());
                assertThrows(ConfigurationException.class,
                        () -> PhysicalPosSupport.requiredIdempotencyKey(key, "createPayment"));
            }
        }
        assertThrows(ConfigurationException.class, () -> PhysicalPosSupport.requiredIdempotencyKey(null, "x"));
    }

    @Test
    void pathIdCases() {
        for (Map<String, Object> row : rows("pathIdCases")) {
            String id = (String) row.get("id");
            if (Boolean.TRUE.equals(row.get("valid"))) {
                Object encoded = row.get("encoded");
                assertEquals(encoded == null ? id : encoded, PhysicalPosSupport.segment(id, "id"));
            } else {
                assertThrows(ConfigurationException.class, () -> PhysicalPosSupport.segment(id, "id"), id);
            }
        }
    }

    @Test
    void forbiddenFieldsAreRefusedBeforeSending() {
        RecordingTransport transport = new RecordingTransport("/api/v1/auth/token", "fixture-only");
        LunixiClient client = LunixiClient.create(LunixiConfig.builder().baseUrl("https://gateway.invalid")
                .keyId("test-key").privateKey(Ed25519KeyPair.generate().privateKeyPem()).transport(transport)
                .maxRetries(0).build());
        @SuppressWarnings("unchecked")
        List<String> forbidden = (List<String>) fixture.get("forbiddenFields");
        for (String field : forbidden) {
            Map<String, Object> request = new LinkedHashMap<>(PhysicalPosClientTest.sale());
            request.put(field, "x");
            assertThrows(LunixiException.class,
                    () -> client.physicalPos().createPayment(request, PhysicalPosClientTest.KEY), field);
        }
        assertTrue(transport.calls().isEmpty());
    }

    @Test
    void requestFieldListsMatchTheFixture() {
        Map<String, Shape> shapes = new LinkedHashMap<>();
        shapes.put("createPayment", PhysicalPosClient.CREATE_PAYMENT);
        shapes.put("paymentTarget", PhysicalPosClient.TARGET);
        shapes.put("saleItem", PhysicalPosClient.SALE_ITEM);
        shapes.put("createReversal", PhysicalPosClient.REVERSAL);
        shapes.put("refundItem", PhysicalPosClient.REFUND_ITEM);
        shapes.put("createStore", PhysicalPosManagementClient.CREATE_STORE);
        shapes.put("updateStore", PhysicalPosManagementClient.UPDATE_STORE);
        shapes.put("statusChange", PhysicalPosManagementClient.STATUS_CHANGE);
        shapes.put("createRegister", PhysicalPosManagementClient.CREATE_REGISTER);
        shapes.put("updateRegister", PhysicalPosManagementClient.UPDATE_REGISTER);
        shapes.put("createTerminal", PhysicalPosManagementClient.CREATE_TERMINAL);
        shapes.put("updateTerminal", PhysicalPosManagementClient.UPDATE_TERMINAL);
        shapes.put("addTerminalAcquirer", PhysicalPosManagementClient.ADD_ACQUIRER);
        shapes.put("updateTerminalAcquirer", PhysicalPosManagementClient.UPDATE_ACQUIRER);
        shapes.put("changeTerminalDevice", PhysicalPosManagementClient.CHANGE_TERMINAL_DEVICE);
        shapes.put("createDevice", PhysicalPosManagementClient.CREATE_DEVICE);
        shapes.put("updateDevice", PhysicalPosManagementClient.UPDATE_DEVICE);
        shapes.put("assignDevice", PhysicalPosManagementClient.ASSIGN_DEVICE);
        shapes.put("changeDeviceLifecycle", PhysicalPosManagementClient.DEVICE_LIFECYCLE);

        Map<String, List<String>> expected = lists("requestFields");
        assertEquals(expected.keySet(), shapes.keySet());
        for (Map.Entry<String, Shape> entry : shapes.entrySet()) {
            assertEquals(new HashSet<>(expected.get(entry.getKey())), new HashSet<>(entry.getValue().names()),
                    entry.getKey());
        }
    }

    @Test
    void filterListsMatchTheFixture() {
        Map<String, List<String>> filters = new LinkedHashMap<>();
        filters.put("listPayments", PhysicalPosClient.PAYMENT_FILTERS);
        filters.put("listTransactions", PhysicalPosClient.TRANSACTION_FILTERS);
        filters.put("listAmbiguousPayments", PhysicalPosClient.AMBIGUOUS_FILTERS);
        filters.put("getSalesSummary", PhysicalPosClient.SALES_SUMMARY_FILTERS);
        filters.put("getTaxSummary", PhysicalPosClient.TAX_SUMMARY_FILTERS);
        filters.put("listSettlements", PhysicalPosClient.SETTLEMENT_FILTERS);
        filters.put("listDeviceModels", PhysicalPosManagementClient.DEVICE_MODEL_FILTERS);
        filters.put("listStores", PhysicalPosManagementClient.STORE_FILTERS);
        filters.put("listRegisters", PhysicalPosManagementClient.REGISTER_FILTERS);
        filters.put("listTerminals", PhysicalPosManagementClient.TERMINAL_FILTERS);
        filters.put("listTerminalAcquirers", PhysicalPosManagementClient.ENTITY_PAGE_FILTERS);
        filters.put("listDevices", PhysicalPosManagementClient.DEVICE_FILTERS);
        filters.put("history", PhysicalPosManagementClient.PAGE_FILTERS);

        Map<String, List<String>> expected = lists("filters");
        assertEquals(expected.keySet(), filters.keySet());
        for (Map.Entry<String, List<String>> entry : filters.entrySet()) {
            assertEquals(new HashSet<>(expected.get(entry.getKey())), new HashSet<>(entry.getValue()), entry.getKey());
        }
    }

    @Test
    void eventTypesMatchTheFixture() {
        @SuppressWarnings("unchecked")
        List<String> expected = (List<String>) fixture.get("eventTypes");
        assertEquals(expected, PhysicalPosEvents.ALL);
    }

    @Test
    @SuppressWarnings("unchecked")
    void webhookOriginalCasesMatchTheFixture() {
        List<Map<String, Object>> cases = rows("webhookOriginalCases");
        assertTrue(cases.size() > 5);
        for (Map<String, Object> row : cases) {
            Map<String, Object> data = (Map<String, Object>) row.get("data");
            Object expected = row.get("original");
            assertEquals(java.util.Optional.ofNullable(expected), PhysicalPosSupport.webhookOriginal(data),
                    String.valueOf(row.get("name")));
        }
    }

    @Test
    void notStartedCodesMatchTheFixture() {
        @SuppressWarnings("unchecked")
        List<String> expected = (List<String>) fixture.get("notStartedCodes");
        assertEquals(Set.copyOf(expected), PhysicalPosSupport.NOT_STARTED_CODES);
    }

    @Test
    @SuppressWarnings("unchecked")
    void cardDataTermListsMatchTheFixture() {
        Map<String, Object> rule = (Map<String, Object>) fixture.get("cardDataKeyRule");
        // Every listed term is card data on its own; masked number terms are exempt, masked secrets are not.
        for (String list : List.of("numberTerms", "secretTerms", "suffixTerms")) {
            for (String term : (List<String>) rule.get(list)) {
                assertTrue(PhysicalPosSupport.isCardDataKey(term), list + ": " + term);
            }
        }
        for (String term : (List<String>) rule.get("wholeKeyTerms")) {
            assertTrue(PhysicalPosSupport.isCardDataKey(term), term);
            assertFalse(PhysicalPosSupport.isCardDataKey(term + "Url"), term + "Url");
        }
        for (String term : (List<String>) rule.get("numberTerms")) {
            assertFalse(PhysicalPosSupport.isCardDataKey("masked_" + term), "masked_" + term);
        }
        for (String term : (List<String>) rule.get("secretTerms")) {
            assertTrue(PhysicalPosSupport.isCardDataKey("masked_" + term), "masked_" + term);
        }
    }
}
