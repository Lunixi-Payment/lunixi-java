package com.lunixi.sdk.physicalpos;

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
import com.lunixi.sdk.exception.PhysicalPosCardDataRejectedException;
import com.lunixi.sdk.exception.ServiceUnavailableException;
import com.lunixi.sdk.json.Json;
import com.lunixi.sdk.support.RecordingTransport;

/*
 * Contract source: nest-payment-gateway apps/api-gateway/src/physical-pos/{stores,terminals,devices,settlements,catalog,overview}
 * (api controllers, DTOs, mappers / services). Ids and values below are synthetic.
 */
class PhysicalPosManagementClientTest {

    static final String BASE = "https://gateway.invalid/api/v1/physical-pos";
    static final String STORE_ID = "11111111-1111-4111-8111-111111111111";
    static final String TERMINAL_ID = "22222222-2222-4222-8222-222222222222";
    static final String DEVICE_ID = "33333333-3333-4333-8333-333333333333";
    static final String KEY = "management-key-00000001";

    RecordingTransport transport;
    PhysicalPosManagementClient management;

    @BeforeEach
    void setup() {
        transport = new RecordingTransport("/api/v1/auth/token", "fixture-only");
        management = LunixiClient.create(LunixiConfig.builder().baseUrl("https://gateway.invalid").keyId("test-key")
                .privateKey(Ed25519KeyPair.generate().privateKeyPem()).transport(transport).maxRetries(0).build())
                .physicalPosManagement();
    }

    @Test
    void createStoreSendsAKeyAndReads201() {
        transport.enqueue(201, "{\"data\":{\"store\":{\"id\":\"" + STORE_ID + "\",\"code\":\"IST-01\"},"
                + "\"replayed\":false},\"statusCode\":201}");
        PhysicalPosWriteResult result = management.createStore(Map.of("code", "IST-01", "name", "Kadıköy"), KEY);
        RecordingTransport.Call call = transport.onlyApiCall();
        assertEquals(BASE + "/stores", call.url);
        assertEquals(KEY, call.headers.get("Idempotency-Key"));
        assertNotNull(call.headers.get(CanonicalRequest.HEADER_SIGNATURE));
        assertEquals(201, result.statusCode());
        assertEquals("IST-01", result.entity().get("code"));
        assertFalse(result.replayed());
    }

    @Test
    void generatesAKeyForEveryKeyedRoute() {
        transport.enqueue(200, "{\"data\":{\"register\":{\"id\":\"r\"},\"replayed\":true}}");
        PhysicalPosWriteResult register = management.createRegister(STORE_ID, Map.of("code", "K1", "name", "Kasa 1"),
                null);
        assertEquals(200, register.statusCode());
        assertTrue(register.replayed());
        transport.enqueue(201, "{\"data\":{\"terminal\":{\"id\":\"t\"},\"replayed\":false},\"statusCode\":201}");
        management.createTerminal(Map.of("code", "T1", "displayName", "Terminal 1"), null);
        transport.enqueue(201, "{\"data\":{\"acquirer\":{\"id\":\"a\"},\"replayed\":false},\"statusCode\":201}");
        management.addTerminalAcquirer(TERMINAL_ID, Map.of("vendorAcquirerRef", "62", "merchantNumber", "100",
                "terminalNumber", "T0001", "currencies", List.of("TRY")), null);
        transport.enqueue(200, "{\"data\":{\"terminal\":{\"id\":\"t\"},\"link\":null}}");
        PhysicalPosWriteResult unlinked = management.closeTerminalDeviceLink(TERMINAL_ID,
                Map.of("rowVersion", 3, "reasonCode", "DEVICE_FAULT"), null);
        assertTrue(unlinked.get("link").isEmpty());
        transport.enqueue(201, "{\"data\":{\"device\":{\"id\":\"d\"},\"replayed\":false},\"statusCode\":201}");
        management.createDevice(Map.of("vendorCode", "PAVO", "deviceModelId", "m", "serialNumber", "PAV1",
                "integrationChannel", "APP2APP"), null);
        transport.enqueue(200, "{\"data\":{\"device\":{\"id\":\"d\"},\"closedAssignment\":null,\"replayed\":false}}");
        management.closeDeviceAssignment(DEVICE_ID, Map.of("rowVersion", 2, "reasonCode", "MOVED_TO_STORAGE"), null);
        transport.enqueue(200, "{\"data\":{\"device\":{\"id\":\"d\"},\"replayed\":false}}");
        management.retireDevice(DEVICE_ID, Map.of("rowVersion", 2, "reasonCode", "END_OF_LIFE"), null);

        List<RecordingTransport.Call> calls = transport.apiCalls();
        assertEquals(7, calls.size());
        List<String> urls = List.of(BASE + "/stores/" + STORE_ID + "/registers", BASE + "/terminals",
                BASE + "/terminals/" + TERMINAL_ID + "/acquirers",
                BASE + "/terminals/" + TERMINAL_ID + "/device-link/close", BASE + "/devices",
                BASE + "/devices/" + DEVICE_ID + "/assignment/close", BASE + "/devices/" + DEVICE_ID + "/retire");
        for (int i = 0; i < calls.size(); i++) {
            assertEquals(urls.get(i), calls.get(i).url);
            assertEquals("POST", calls.get(i).method);
            assertNotNull(calls.get(i).headers.get("Idempotency-Key"), calls.get(i).url);
        }
    }

    @Test
    void routesWithoutAKeySendNone() {
        transport.enqueue(200, "{\"data\":{\"store\":{\"id\":\"s\",\"rowVersion\":2}}}");
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("rowVersion", 1);
        patch.put("city", null);
        assertEquals(2L, management.updateStore(STORE_ID, patch).get("rowVersion"));
        RecordingTransport.Call call = transport.onlyApiCall();
        assertEquals("PATCH", call.method);
        assertNull(call.headers.get("Idempotency-Key"));
        Map<String, Object> body = Json.parseObject(call.body);
        assertTrue(body.containsKey("city"));
        assertNull(body.get("city"));
    }

    @Test
    void readsUnwrapTheirRecord() {
        transport.enqueue(200, "{\"data\":{\"overview\":{\"transactions\":null,\"generatedAt\":\"2026-10-27\"}}}");
        assertEquals("2026-10-27", management.getOverview().get("generatedAt"));
        transport.enqueue(200, "{\"data\":[{\"code\":\"PAVO\"}]}");
        assertEquals("PAVO", management.listVendors().get(0).get("code"));
        transport.enqueue(200, "{\"data\":[]}");
        management.listDeviceModels(Map.of("vendorCode", "PAVO"));
        transport.enqueue(200, "{\"data\":{\"store\":{\"id\":\"s\"},\"registerSummary\":{\"activeCount\":1},"
                + "\"assignedDeviceCount\":2}}");
        assertEquals(2L, management.getStore(STORE_ID).get("assignedDeviceCount"));
        transport.enqueue(200, "{\"data\":{\"terminal\":{\"id\":\"t\"},\"acquirers\":{\"items\":[]}}}");
        assertTrue(management.getTerminal(TERMINAL_ID).containsKey("acquirers"));
        transport.enqueue(200, "{\"data\":{\"device\":{\"id\":\"d\",\"lifecycleStatus\":\"ACTIVE\"}}}");
        assertEquals("ACTIVE", management.getDevice(DEVICE_ID).get("lifecycleStatus"));

        List<RecordingTransport.Call> calls = transport.apiCalls();
        assertEquals(BASE + "/overview", calls.get(0).url);
        assertEquals(BASE + "/catalog/vendors", calls.get(1).url);
        assertEquals(BASE + "/catalog/device-models?vendorCode=PAVO", calls.get(2).url);
        assertEquals(BASE + "/stores/" + STORE_ID, calls.get(3).url);
        assertEquals(BASE + "/terminals/" + TERMINAL_ID, calls.get(4).url);
        assertEquals(BASE + "/devices/" + DEVICE_ID, calls.get(5).url);
    }

    @Test
    void entityListsCarryIncludeTotalAndTotalCount() {
        transport.enqueue(200, "{\"data\":{\"items\":[{\"id\":\"d\"}]},\"pageInfo\":{\"hasMore\":false,"
                + "\"nextCursor\":null,\"totalCount\":1}}");
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("lifecycleStatus", "ACTIVE");
        filters.put("includeTotal", true);
        filters.put("limit", 50);
        PhysicalPosPage page = management.listDevices(filters);
        assertEquals(BASE + "/devices?limit=50&includeTotal=true&lifecycleStatus=ACTIVE", transport.onlyApiCall().url);
        assertEquals(1L, page.totalCount().orElseThrow());
        assertFalse(page.hasMore());

        assertThrows(ConfigurationException.class, () -> management.listStoreAudit(STORE_ID,
                Map.of("includeTotal", true)));
        assertThrows(ConfigurationException.class, () -> management.listDevices(Map.of("includeTotal", "true")));
    }

    @Test
    void iteratorsFollowTheCursorOnNestedLists() {
        transport.enqueue(200, "{\"data\":{\"items\":[{\"id\":\"e1\"}]},\"pageInfo\":{\"hasMore\":true,"
                + "\"nextCursor\":\"n1\"}}");
        transport.enqueue(200, "{\"data\":{\"items\":[{\"id\":\"e2\"}]},\"pageInfo\":{\"hasMore\":false}}");
        List<Map<String, Object>> rows = new ArrayList<>();
        management.iterateDeviceAudit(DEVICE_ID, null).forEach(rows::add);
        assertEquals(2, rows.size());
        assertEquals(BASE + "/devices/" + DEVICE_ID + "/audit?cursor=n1", transport.apiCalls().get(1).url);
    }

    @Test
    void checksShapesRowVersionAndCardDataLocally() {
        assertThrows(ConfigurationException.class, () -> management.updateStore(STORE_ID, Map.of("name", "x")));
        assertThrows(ConfigurationException.class, () -> management.updateStore(STORE_ID,
                Map.of("rowVersion", "3", "name", "x")));
        assertThrows(ConfigurationException.class, () -> management.updateStore(STORE_ID,
                Map.of("rowVersion", 0, "name", "x")));
        assertThrows(ConfigurationException.class, () -> management.archiveStore(STORE_ID,
                Map.of("rowVersion", 1, "reasonCode", "OTHER", "organizationId", "o")));
        assertThrows(ConfigurationException.class, () -> management.createStore(Map.of("code", "X"), KEY));
        assertThrows(ConfigurationException.class, () -> management.addTerminalAcquirer(TERMINAL_ID,
                Map.of("vendorAcquirerRef", "62", "merchantNumber", "1", "terminalNumber", "2", "currencies", "TRY"),
                KEY));
        assertThrows(PhysicalPosCardDataRejectedException.class, () -> management.createDevice(
                Map.of("vendorCode", "PAVO", "deviceModelId", "m", "serialNumber", "s", "integrationChannel",
                        "APP2APP", "cardNumber", "x"), KEY));
        assertThrows(ConfigurationException.class, () -> management.getDevice(""));
        assertTrue(transport.calls().isEmpty());
    }

    @Test
    void managementWritesAreNotPaymentOutcomes() {
        transport.enqueue(503, "{\"status\":\"failure\",\"code\":\"physical_pos.service.busy\"}");
        // Not wrapped as an unknown payment outcome: management writes keep the gateway's own exception type.
        ServiceUnavailableException busy = assertThrows(ServiceUnavailableException.class,
                () -> management.createStore(Map.of("code", "IST-01", "name", "Kadıköy"), KEY));
        assertEquals(ServiceUnavailableException.class, busy.getClass());
    }

    @Test
    void lifecycleAssignmentAndDeviceLinkRoutes() {
        transport.enqueue(200, "{\"data\":{\"device\":{\"id\":\"d\"},\"assignment\":{\"id\":\"a\"},"
                + "\"previousAssignmentId\":null}}");
        Map<String, Object> assign = new LinkedHashMap<>();
        assign.put("rowVersion", 4);
        assign.put("storeId", STORE_ID);
        assign.put("registerId", null);
        assign.put("reasonCode", "INITIAL_SETUP");
        assertTrue(management.assignDevice(DEVICE_ID, assign).containsKey("assignment"));
        transport.enqueue(200, "{\"data\":{\"device\":{\"id\":\"d\"}}}");
        management.changeDeviceLifecycle(DEVICE_ID, Map.of("rowVersion", 5, "target", "SUSPENDED",
                "reasonCode", "DEVICE_LOST"));
        transport.enqueue(200, "{\"data\":{\"terminal\":{\"id\":\"t\"},\"link\":{\"id\":\"l\"}}}");
        management.changeTerminalDevice(TERMINAL_ID, Map.of("rowVersion", 1, "deviceId", DEVICE_ID,
                "reasonCode", "RELOCATION"));
        transport.enqueue(200, "{\"data\":{\"acquirer\":{\"id\":\"a\"}}}");
        Map<String, Object> label = new LinkedHashMap<>();
        label.put("rowVersion", 1);
        label.put("label", null);
        management.updateTerminalAcquirer("a-1", label);

        List<RecordingTransport.Call> calls = transport.apiCalls();
        assertEquals(BASE + "/devices/" + DEVICE_ID + "/assignment", calls.get(0).url);
        assertEquals(BASE + "/devices/" + DEVICE_ID + "/lifecycle", calls.get(1).url);
        assertEquals(BASE + "/terminals/" + TERMINAL_ID + "/device-link", calls.get(2).url);
        assertEquals(BASE + "/terminal-acquirers/a-1", calls.get(3).url);
        assertEquals("PATCH", calls.get(3).method);
    }

    static final String DEVICE_KEY_ID = "pdk_AAAAAAAAAAAAAAAAAAAAAAAA";
    static final String PROVIDER_ROW_ID = "66666666-6666-4666-8666-666666666666";
    static final String VENDOR_ACCOUNT_ID = "77777777-7777-4777-8777-777777777777";

    static String provider(String status) {
        return "{\"id\":\"" + PROVIDER_ROW_ID + "\",\"terminalId\":\"" + TERMINAL_ID + "\",\"environment\":\"LIVE\","
                + "\"providerCode\":\"METROPOL\",\"vendorAccountId\":\"" + VENDOR_ACCOUNT_ID + "\","
                + "\"merchantCode\":\"1000000001\",\"terminalCode\":\"2000000002\",\"productCode\":\"3\","
                + "\"label\":null,\"providerStatus\":\"" + status + "\",\"rowVersion\":1}";
    }

    static final String POLICY = "{\"scopeType\":\"ACCOUNT\",\"storeId\":null,\"deviceId\":null,\"active\":true,"
            + "\"mode\":\"SCHEDULED\",\"scheduleTime\":\"23:30\",\"timeZone\":\"Europe/Istanbul\",\"scheduleDays\":[],"
            + "\"retryWindowMinutes\":120,\"retryIntervalMinutes\":15,\"offlineBehavior\":\"RETRY_IN_WINDOW\","
            + "\"overdueAlertHours\":26,\"rowVersion\":2,\"updatedAt\":\"2026-11-03T09:00:00.000Z\"}";

    @Test
    void terminalProviderRoutes() {
        transport.enqueue(200, "{\"data\":{\"items\":[" + provider("ACTIVE") + "]},"
                + "\"pageInfo\":{\"hasMore\":false,\"nextCursor\":null,\"totalCount\":1}}");
        PhysicalPosPage page = management.listTerminalProviders(TERMINAL_ID, Map.of("includeTotal", true));
        assertEquals("METROPOL", page.items().get(0).get("providerCode"));

        transport.enqueue(201, "{\"data\":{\"provider\":" + provider("ACTIVE") + ",\"replayed\":false},"
                + "\"statusCode\":201}");
        Map<String, Object> add = new LinkedHashMap<>();
        add.put("vendorAccountId", VENDOR_ACCOUNT_ID);
        add.put("merchantCode", "1000000001");
        add.put("terminalCode", "2000000002");
        add.put("productCode", "3");
        add.put("label", "Meal");
        PhysicalPosWriteResult added = management.addTerminalProvider(TERMINAL_ID, add, KEY);
        assertEquals(201, added.statusCode());
        assertEquals(PROVIDER_ROW_ID, added.entity().get("id"));

        transport.enqueue(200, "{\"data\":{\"provider\":" + provider("ACTIVE") + ",\"replayed\":true}}");
        PhysicalPosWriteResult replay = management.addTerminalProvider(TERMINAL_ID, Map.of("vendorAccountId",
                VENDOR_ACCOUNT_ID, "merchantCode", "1000000001", "terminalCode", "2000000002"), null);
        assertTrue(replay.replayed());
        assertEquals(200, replay.statusCode());

        transport.enqueue(200, "{\"data\":{\"provider\":" + provider("DISABLED") + "}}");
        Map<String, Object> update = new LinkedHashMap<>();
        update.put("rowVersion", 1);
        update.put("label", null);
        update.put("status", "DISABLED");
        assertEquals("DISABLED", management.updateTerminalProvider(PROVIDER_ROW_ID, update).get("providerStatus"));

        List<RecordingTransport.Call> calls = transport.apiCalls();
        assertEquals(BASE + "/terminals/" + TERMINAL_ID + "/providers?includeTotal=true", calls.get(0).url);
        assertEquals("POST", calls.get(1).method);
        assertEquals(BASE + "/terminals/" + TERMINAL_ID + "/providers", calls.get(1).url);
        assertEquals(KEY, calls.get(1).headers.get("Idempotency-Key"));
        assertEquals(add, Json.parseObject(calls.get(1).body));
        assertTrue(calls.get(2).headers.get("Idempotency-Key").matches("[0-9a-f-]{36}"));
        assertEquals("PATCH", calls.get(3).method);
        assertEquals(BASE + "/terminal-providers/" + PROVIDER_ROW_ID, calls.get(3).url);
        Map<String, Object> patch = Json.parseObject(calls.get(3).body);
        assertEquals(1L, patch.get("rowVersion"));
        assertTrue(patch.containsKey("label"));
        assertNull(patch.get("label"));
        assertEquals("DISABLED", patch.get("status"));
        assertNull(calls.get(3).headers.get("Idempotency-Key"));
    }

    @Test
    void settlementPolicyRoutesForEveryLevel() {
        for (int i = 0; i < 9; i++) {
            transport.enqueue(200, "{\"data\":{\"policy\":" + POLICY + "}}");
        }
        transport.enqueue(200, "{\"data\":{\"effective\":{\"deviceId\":\"" + DEVICE_ID + "\","
                + "\"sourceScopeType\":\"STORE\",\"policy\":" + POLICY + ",\"serverSettlementSupported\":true,"
                + "\"deviceAutoSupported\":false,\"nextScheduledAt\":null,\"lastSettlementAt\":null,"
                + "\"overdue\":false,\"scheduleWarning\":null}}}");
        Map<String, Object> scheduled = new LinkedHashMap<>();
        scheduled.put("mode", "SCHEDULED");
        scheduled.put("scheduleTime", "23:30");
        scheduled.put("timeZone", "Europe/Istanbul");
        scheduled.put("scheduleDays", List.of("MON", "FRI"));
        scheduled.put("retryWindowMinutes", 120);
        scheduled.put("retryIntervalMinutes", 15);
        scheduled.put("offlineBehavior", "ALERT_IMMEDIATELY");
        scheduled.put("overdueAlertHours", 26);
        scheduled.put("expectedRowVersion", 1);

        assertEquals("SCHEDULED", management.getAccountSettlementPolicy().get("mode"));
        management.setAccountSettlementPolicy(scheduled);
        management.clearAccountSettlementPolicy();
        management.getStoreSettlementPolicy(STORE_ID);
        management.setStoreSettlementPolicy(STORE_ID, Map.of("mode", "MANUAL"));
        management.clearStoreSettlementPolicy(STORE_ID, 3);
        management.getDeviceSettlementPolicy(DEVICE_ID);
        management.setDeviceSettlementPolicy(DEVICE_ID, Map.of("mode", "DEVICE_AUTO", "overdueAlertHours", 30));
        assertEquals(2L, ((Number) management.clearDeviceSettlementPolicy(DEVICE_ID, 4).get("rowVersion")).longValue());
        assertEquals("STORE", management.getEffectiveSettlementPolicy(DEVICE_ID).get("sourceScopeType"));

        List<RecordingTransport.Call> calls = transport.apiCalls();
        List<String> routes = new ArrayList<>();
        for (RecordingTransport.Call call : calls) {
            routes.add(call.method + " " + call.url);
            assertNull(call.headers.get("Idempotency-Key"));
        }
        assertEquals(List.of(
                "GET " + BASE + "/settlement-policy",
                "PUT " + BASE + "/settlement-policy",
                "DELETE " + BASE + "/settlement-policy",
                "GET " + BASE + "/stores/" + STORE_ID + "/settlement-policy",
                "PUT " + BASE + "/stores/" + STORE_ID + "/settlement-policy",
                "DELETE " + BASE + "/stores/" + STORE_ID + "/settlement-policy?expectedRowVersion=3",
                "GET " + BASE + "/devices/" + DEVICE_ID + "/settlement-policy",
                "PUT " + BASE + "/devices/" + DEVICE_ID + "/settlement-policy",
                "DELETE " + BASE + "/devices/" + DEVICE_ID + "/settlement-policy?expectedRowVersion=4",
                "GET " + BASE + "/devices/" + DEVICE_ID + "/settlement-policy/effective"), routes);
        Map<String, Object> put = Json.parseObject(calls.get(1).body);
        assertEquals(List.of("MON", "FRI"), put.get("scheduleDays"));
        assertEquals(120L, put.get("retryWindowMinutes"));
        assertEquals(1L, put.get("expectedRowVersion"));
        assertEquals(Map.of("mode", "MANUAL"), Json.parseObject(calls.get(4).body));
        assertNull(calls.get(2).body);
    }

    @Test
    void settlementPolicyAndProviderRequestsAreCheckedLocally() {
        assertThrows(ConfigurationException.class, () -> management.setAccountSettlementPolicy(Map.of()));
        assertThrows(ConfigurationException.class, () -> management.setAccountSettlementPolicy(
                Map.of("mode", "MANUAL", "environment", "LIVE")));
        assertThrows(ConfigurationException.class, () -> management.setStoreSettlementPolicy(STORE_ID,
                Map.of("mode", "SCHEDULED", "retryWindowMinutes", "120")));
        assertThrows(ConfigurationException.class, () -> management.setDeviceSettlementPolicy(DEVICE_ID,
                Map.of("mode", "SCHEDULED", "scheduleDays", "MON")));
        assertThrows(ConfigurationException.class, () -> management.setDeviceSettlementPolicy(DEVICE_ID,
                Map.of("mode", "MANUAL", "expectedRowVersion", 0)));
        assertThrows(ConfigurationException.class, () -> management.clearStoreSettlementPolicy(STORE_ID, 0));
        assertThrows(ConfigurationException.class, () -> management.clearDeviceSettlementPolicy("", 1));
        assertThrows(ConfigurationException.class, () -> management.getEffectiveSettlementPolicy(".."));
        assertThrows(ConfigurationException.class, () -> management.addTerminalProvider(TERMINAL_ID,
                Map.of("vendorAccountId", VENDOR_ACCOUNT_ID, "merchantCode", "1"), null));
        assertThrows(ConfigurationException.class, () -> management.addTerminalProvider(TERMINAL_ID,
                Map.of("vendorAccountId", VENDOR_ACCOUNT_ID, "merchantCode", "1", "terminalCode", "2",
                        "providerCode", "METROPOL"), null));
        assertThrows(ConfigurationException.class, () -> management.updateTerminalProvider(PROVIDER_ROW_ID,
                Map.of("label", "x")));
        assertThrows(ConfigurationException.class, () -> management.updateTerminalProvider("",
                Map.of("rowVersion", 1)));
        assertThrows(ConfigurationException.class, () -> management.listTerminalProviders(TERMINAL_ID,
                Map.of("status", "ACTIVE")));
        assertTrue(transport.calls().isEmpty());
    }

    @Test
    void deviceIdentityRoutes() {
        transport.enqueue(200, "{\"data\":{\"items\":[{\"keyId\":\"" + DEVICE_KEY_ID + "\",\"keyStatus\":\"ACTIVE\","
                + "\"fingerprint\":\"ABCD-EF12\",\"rowVersion\":1}]},"
                + "\"pageInfo\":{\"hasMore\":true,\"nextCursor\":\"k1\"}}");
        PhysicalPosPage keys = management.listDeviceKeys(DEVICE_ID, Map.of("limit", 10));
        assertEquals(DEVICE_KEY_ID, keys.items().get(0).get("keyId"));
        assertTrue(keys.hasMore());
        assertEquals("k1", keys.nextCursor().orElseThrow());

        transport.enqueue(200, "{\"data\":{\"key\":{\"keyId\":\"" + DEVICE_KEY_ID + "\",\"keyStatus\":\"REVOKED\"},"
                + "\"lifecycleStatus\":\"SUSPENDED\",\"replayed\":false}}");
        Map<String, Object> revoke = new LinkedHashMap<>();
        revoke.put("rowVersion", 1);
        revoke.put("reasonCode", "DEVICE_LOST");
        Map<String, Object> revoked = management.revokeDeviceKey(DEVICE_ID, DEVICE_KEY_ID, revoke);
        assertEquals("SUSPENDED", revoked.get("lifecycleStatus"));
        assertEquals(Boolean.FALSE, revoked.get("replayed"));

        transport.enqueue(200, "{\"data\":{\"key\":{\"keyId\":\"" + DEVICE_KEY_ID + "\",\"keyStatus\":\"REVOKED\"},"
                + "\"lifecycleStatus\":\"SUSPENDED\",\"replayed\":true}}");
        management.revertDeviceEnrollment(DEVICE_ID, DEVICE_KEY_ID, Map.of("reasonCode", "NOT_MY_DEVICE"));

        transport.enqueue(200, "{\"data\":{\"deviceId\":\"" + DEVICE_ID + "\","
                + "\"revokedAt\":\"2026-11-02T09:00:00.000Z\",\"revokeReason\":\"MERCHANT_REVOKED\"}}");
        Map<String, Object> code = management.revokeActivationCode(DEVICE_ID, Map.of("reasonCode", "MERCHANT_REVOKED"));
        assertEquals("MERCHANT_REVOKED", code.get("revokeReason"));

        List<RecordingTransport.Call> calls = transport.apiCalls();
        assertEquals(BASE + "/devices/" + DEVICE_ID + "/keys?limit=10", calls.get(0).url);
        assertEquals("GET", calls.get(0).method);
        assertEquals(BASE + "/devices/" + DEVICE_ID + "/keys/" + DEVICE_KEY_ID + "/revoke", calls.get(1).url);
        Map<String, Object> revokeBody = Json.parseObject(calls.get(1).body);
        assertEquals(1L, revokeBody.get("rowVersion"));
        assertEquals("DEVICE_LOST", revokeBody.get("reasonCode"));
        assertEquals(BASE + "/devices/" + DEVICE_ID + "/keys/" + DEVICE_KEY_ID + "/revert-enrollment",
                calls.get(2).url);
        assertEquals(Map.of("reasonCode", "NOT_MY_DEVICE"), Json.parseObject(calls.get(2).body));
        assertEquals(BASE + "/devices/" + DEVICE_ID + "/activation-code/revoke", calls.get(3).url);
        assertEquals(Map.of("reasonCode", "MERCHANT_REVOKED"), Json.parseObject(calls.get(3).body));
        for (int i = 1; i < 4; i++) {
            assertEquals("POST", calls.get(i).method);
            assertNull(calls.get(i).headers.get("Idempotency-Key"));
        }
    }

    @Test
    void deviceIdentityRequestsAreCheckedLocally() {
        assertThrows(ConfigurationException.class, () -> management.revokeDeviceKey(DEVICE_ID, "",
                Map.of("rowVersion", 1, "reasonCode", "OTHER")));
        assertThrows(ConfigurationException.class, () -> management.revokeDeviceKey(DEVICE_ID, DEVICE_KEY_ID,
                Map.of("rowVersion", "1", "reasonCode", "OTHER")));
        assertThrows(ConfigurationException.class, () -> management.revokeDeviceKey(DEVICE_ID, DEVICE_KEY_ID,
                Map.of("reasonCode", "OTHER")));
        assertThrows(ConfigurationException.class, () -> management.revertDeviceEnrollment(DEVICE_ID, DEVICE_KEY_ID,
                Map.of("reasonCode", "OTHER", "rowVersion", 1)));
        assertThrows(ConfigurationException.class, () -> management.revokeActivationCode(DEVICE_ID, Map.of()));
        assertThrows(ConfigurationException.class, () -> management.listDeviceKeys(DEVICE_ID,
                Map.of("includeTotal", true)));
        assertTrue(transport.calls().isEmpty());
    }
}
