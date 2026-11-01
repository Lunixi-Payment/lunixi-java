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
 * Contract source: nest-payment-gateway apps/api-gateway/src/physical-pos/{stores,terminals,devices,catalog,overview}
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
}
