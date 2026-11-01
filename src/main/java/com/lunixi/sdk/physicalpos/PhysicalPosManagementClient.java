package com.lunixi.sdk.physicalpos;

import static com.lunixi.sdk.physicalpos.PhysicalPosSupport.BASE;

import java.util.List;
import java.util.Map;

import com.lunixi.sdk.http.Envelope;
import com.lunixi.sdk.internal.ApiClient;
import com.lunixi.sdk.internal.RequestOptions;
import com.lunixi.sdk.physicalpos.PhysicalPosSupport.Shape;

/**
 * Physical POS management — overview, catalog, stores and registers, logical terminals with their bank rows and
 * device link, and devices with their assignment and lifecycle ({@code /api/v1/physical-pos}).
 *
 * <p>Every call is signed. Reads need {@code physical_pos:view}, writes {@code physical_pos:manage}; a TEST key
 * reads only ({@code 403 physical_pos.environment.test_key_read_only} on writes). Updates and status changes carry
 * the record's last read {@code rowVersion} (a JSON integer); a stale one is {@code 409 physical_pos.version_conflict}.
 * Reason codes are closed sets checked by the gateway ({@code 400 physical_pos.input.reason_code_invalid}).
 *
 * <p>The creates and the routes marked "Idempotency-Key" below always send a key — yours, or a generated UUID when
 * you pass {@code null}. The key is permanent on these routes: the same key and body returns the first answer
 * ({@code replayed: true}, HTTP 200); reusing it for anything else is {@code 409 physical_pos.idempotency_mismatch}.
 * Use a new UUID for every operation.
 *
 * <p>Responses are the envelope's {@code data}, as maps in wire order. Device identity (activation codes, device
 * keys) is not part of this client.
 */
public final class PhysicalPosManagementClient {

    static final Shape STATUS_CHANGE = new Shape("status change request").rowVersion().text("reasonCode", true, false);

    static final Shape CREATE_STORE = new Shape("createStore request")
            .text("code", true, false).text("name", true, false)
            .text("city", false, false).text("district", false, false).text("addressLine", false, false)
            .text("timezone", false, false).text("externalRef", false, false);

    static final Shape UPDATE_STORE = new Shape("updateStore request")
            .rowVersion().text("name", false, false)
            .text("city", false, true).text("district", false, true).text("addressLine", false, true)
            .text("timezone", false, false).text("externalRef", false, true);

    static final Shape CREATE_REGISTER = new Shape("createRegister request")
            .text("code", true, false).text("name", true, false);

    static final Shape UPDATE_REGISTER = new Shape("updateRegister request").rowVersion().text("name", false, false);

    static final Shape CREATE_TERMINAL = new Shape("createTerminal request")
            .text("code", true, false).text("displayName", true, false).text("vendorCode", false, false)
            .text("virtualSerial", false, false).text("vendorTerminalRef", false, false);

    static final Shape UPDATE_TERMINAL = new Shape("updateTerminal request")
            .rowVersion().text("displayName", false, false).text("vendorCode", false, false);

    static final Shape ADD_ACQUIRER = new Shape("addTerminalAcquirer request")
            .text("vendorAcquirerRef", true, false).text("merchantNumber", true, false)
            .text("terminalNumber", true, false).text("label", false, false).textList("currencies", true);

    static final Shape UPDATE_ACQUIRER = new Shape("updateTerminalAcquirer request")
            .rowVersion().text("label", false, true).textList("currencies", false).text("status", false, false);

    static final Shape CHANGE_TERMINAL_DEVICE = new Shape("changeTerminalDevice request")
            .rowVersion().text("deviceId", true, false).text("reasonCode", true, false);

    static final Shape CREATE_DEVICE = new Shape("createDevice request")
            .text("vendorCode", true, false).text("deviceModelId", true, false).text("serialNumber", true, false)
            .text("displayName", false, false).text("externalRef", false, false).text("storeId", false, false)
            .text("registerId", false, false).text("integrationChannel", true, false)
            .text("channelVariant", false, false).text("appMode", false, false);

    static final Shape UPDATE_DEVICE = new Shape("updateDevice request")
            .rowVersion().text("displayName", false, true).text("externalRef", false, true)
            .text("integrationChannel", false, false).text("channelVariant", false, true)
            .text("appMode", false, true);

    static final Shape ASSIGN_DEVICE = new Shape("assignDevice request")
            .rowVersion().text("storeId", true, false).text("registerId", false, true).text("reasonCode", true, false);

    static final Shape DEVICE_LIFECYCLE = new Shape("changeDeviceLifecycle request")
            .rowVersion().text("target", true, false).text("reasonCode", true, false);

    static final List<String> PAGE_FILTERS = PhysicalPosSupport.list("limit", "cursor");
    static final List<String> ENTITY_PAGE_FILTERS = PhysicalPosSupport.list("limit", "cursor", "includeTotal");
    static final List<String> STORE_FILTERS = PhysicalPosSupport.list("limit", "cursor", "includeTotal", "status", "q");
    static final List<String> REGISTER_FILTERS = PhysicalPosSupport.list("limit", "cursor", "includeTotal", "status");
    static final List<String> TERMINAL_FILTERS = PhysicalPosSupport.list(
            "limit", "cursor", "includeTotal", "status", "q");
    static final List<String> DEVICE_FILTERS = PhysicalPosSupport.list(
            "limit", "cursor", "includeTotal", "storeId", "registerId", "vendorCode", "deviceModelId", "deviceType",
            "lifecycleStatus", "ownershipStatus", "integrationChannel", "q");
    static final List<String> DEVICE_MODEL_FILTERS = PhysicalPosSupport.list("vendorCode", "deviceType", "status");

    private final ApiClient api;

    public PhysicalPosManagementClient(ApiClient api) {
        this.api = api;
    }

    // ── Overview and catalog ─────────────────────────────────────────────────────────────────────────────────

    /** @return counts of stores, registers, terminals and devices ({@code GET /overview}, {@code data.overview}) */
    public Map<String, Object> getOverview() {
        return entity(get(BASE + "/overview", null, List.of(), "getOverview"), "overview");
    }

    /** @return the device vendors ({@code GET /catalog/vendors}) */
    public List<Map<String, Object>> listVendors() {
        return Envelope.rows(get(BASE + "/catalog/vendors", null, List.of(), "listVendors"));
    }

    /**
     * @param filters {@code vendorCode}, {@code deviceType}, {@code status}, or {@code null}
     * @return the device models ({@code GET /catalog/device-models})
     */
    public List<Map<String, Object>> listDeviceModels(Map<String, ?> filters) {
        return Envelope.rows(get(BASE + "/catalog/device-models", filters, DEVICE_MODEL_FILTERS, "listDeviceModels"));
    }

    /** @return the active acquirer banks {@code {acquirerCode, displayName}} ({@code GET /catalog/acquirers}) */
    public List<Map<String, Object>> listAcquirers() {
        return Envelope.rows(get(BASE + "/catalog/acquirers", null, List.of(), "listAcquirers"));
    }

    // ── Stores ───────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * @param filters {@code limit}, {@code cursor}, {@code includeTotal}, {@code status}, {@code q}, or {@code null}
     * @return stores, each with {@code registerCount} ({@code GET /stores})
     */
    public PhysicalPosPage listStores(Map<String, ?> filters) {
        return page(BASE + "/stores", filters, STORE_FILTERS, "listStores");
    }

    /** @return every store matching the filters, page by page */
    public Iterable<Map<String, Object>> iterateStores(Map<String, ?> filters) {
        return PhysicalPosSupport.iterate(filters, this::listStores);
    }

    /**
     * Creates a store ({@code POST /stores}, Idempotency-Key): {@code code}, {@code name}, optional {@code city},
     * {@code district}, {@code addressLine}, {@code timezone}, {@code externalRef}.
     *
     * @param request        the body
     * @param idempotencyKey your key, or {@code null} for a generated UUID
     * @return {@code store}, {@code replayed}, status 201 / 200, the key
     */
    public PhysicalPosWriteResult createStore(Map<String, ?> request, String idempotencyKey) {
        return keyedWrite(BASE + "/stores", PhysicalPosSupport.body(request, CREATE_STORE), idempotencyKey,
                "store", 201);
    }

    /** @return {@code {store, registerSummary, assignedDeviceCount}} ({@code GET /stores/{storeId}}) */
    public Map<String, Object> getStore(String storeId) {
        return PhysicalPosSupport.data(get(storePath(storeId), null, List.of(), "getStore"));
    }

    /**
     * Updates a store ({@code PATCH /stores/{storeId}}): {@code rowVersion} and the fields to change; {@code null}
     * clears {@code city}, {@code district}, {@code addressLine}, {@code externalRef}.
     *
     * @return the store
     */
    public Map<String, Object> updateStore(String storeId, Map<String, ?> request) {
        return entity(write("PATCH", storePath(storeId), PhysicalPosSupport.body(request, UPDATE_STORE)), "store");
    }

    /**
     * Archives a store and its active registers ({@code POST /stores/{storeId}/archive}).
     *
     * @param request {@code {rowVersion, reasonCode}}
     * @return {@code {store, archivedRegisterCount}}
     */
    public Map<String, Object> archiveStore(String storeId, Map<String, ?> request) {
        return PhysicalPosSupport.data(write("POST", storePath(storeId) + "/archive",
                PhysicalPosSupport.body(request, STATUS_CHANGE)));
    }

    /**
     * Restores an archived store ({@code POST /stores/{storeId}/restore}); its registers stay archived.
     *
     * @param request {@code {rowVersion, reasonCode}}
     * @return the store
     */
    public Map<String, Object> restoreStore(String storeId, Map<String, ?> request) {
        return entity(write("POST", storePath(storeId) + "/restore", PhysicalPosSupport.body(request, STATUS_CHANGE)),
                "store");
    }

    /** @return the store's audit events ({@code GET /stores/{storeId}/audit}; {@code limit}, {@code cursor}) */
    public PhysicalPosPage listStoreAudit(String storeId, Map<String, ?> filters) {
        return page(storePath(storeId) + "/audit", filters, PAGE_FILTERS, "listStoreAudit");
    }

    /** @return every audit event of the store, page by page */
    public Iterable<Map<String, Object>> iterateStoreAudit(String storeId, Map<String, ?> filters) {
        String path = storePath(storeId) + "/audit";
        return PhysicalPosSupport.iterate(filters, page -> page(path, page, PAGE_FILTERS, "listStoreAudit"));
    }

    // ── Registers ────────────────────────────────────────────────────────────────────────────────────────────

    /** @return the store's registers ({@code GET /stores/{storeId}/registers}; limit, cursor, includeTotal, status) */
    public PhysicalPosPage listRegisters(String storeId, Map<String, ?> filters) {
        return page(storePath(storeId) + "/registers", filters, REGISTER_FILTERS, "listRegisters");
    }

    /** @return every register of the store, page by page */
    public Iterable<Map<String, Object>> iterateRegisters(String storeId, Map<String, ?> filters) {
        String path = storePath(storeId) + "/registers";
        return PhysicalPosSupport.iterate(filters, page -> page(path, page, REGISTER_FILTERS, "listRegisters"));
    }

    /**
     * Creates a register in a store ({@code POST /stores/{storeId}/registers}, Idempotency-Key): {@code code},
     * {@code name}.
     *
     * @return {@code register}, {@code replayed}, status 201 / 200, the key
     */
    public PhysicalPosWriteResult createRegister(String storeId, Map<String, ?> request, String idempotencyKey) {
        String path = storePath(storeId) + "/registers";
        return keyedWrite(path, PhysicalPosSupport.body(request, CREATE_REGISTER), idempotencyKey, "register", 201);
    }

    /** Renames a register ({@code PATCH /registers/{registerId}}: {@code rowVersion}, {@code name}). @return the register */
    public Map<String, Object> updateRegister(String registerId, Map<String, ?> request) {
        return entity(write("PATCH", registerPath(registerId), PhysicalPosSupport.body(request, UPDATE_REGISTER)),
                "register");
    }

    /** Archives a register ({@code {rowVersion, reasonCode}}). @return the register */
    public Map<String, Object> archiveRegister(String registerId, Map<String, ?> request) {
        return entity(write("POST", registerPath(registerId) + "/archive",
                PhysicalPosSupport.body(request, STATUS_CHANGE)), "register");
    }

    /** Restores a register ({@code {rowVersion, reasonCode}}). @return the register */
    public Map<String, Object> restoreRegister(String registerId, Map<String, ?> request) {
        return entity(write("POST", registerPath(registerId) + "/restore",
                PhysicalPosSupport.body(request, STATUS_CHANGE)), "register");
    }

    /** @return the register's audit events ({@code limit}, {@code cursor}) */
    public PhysicalPosPage listRegisterAudit(String registerId, Map<String, ?> filters) {
        return page(registerPath(registerId) + "/audit", filters, PAGE_FILTERS, "listRegisterAudit");
    }

    /** @return every audit event of the register, page by page */
    public Iterable<Map<String, Object>> iterateRegisterAudit(String registerId, Map<String, ?> filters) {
        String path = registerPath(registerId) + "/audit";
        return PhysicalPosSupport.iterate(filters, page -> page(path, page, PAGE_FILTERS, "listRegisterAudit"));
    }

    // ── Terminals ────────────────────────────────────────────────────────────────────────────────────────────

    /** @return logical terminals ({@code GET /terminals}; limit, cursor, includeTotal, status, q) */
    public PhysicalPosPage listTerminals(Map<String, ?> filters) {
        return page(BASE + "/terminals", filters, TERMINAL_FILTERS, "listTerminals");
    }

    /** @return every terminal matching the filters, page by page */
    public Iterable<Map<String, Object>> iterateTerminals(Map<String, ?> filters) {
        return PhysicalPosSupport.iterate(filters, this::listTerminals);
    }

    /**
     * Creates a logical terminal ({@code POST /terminals}, Idempotency-Key): {@code code}, {@code displayName},
     * optional {@code vendorCode}, {@code virtualSerial}, {@code vendorTerminalRef}.
     *
     * @return {@code terminal}, {@code replayed}, status 201 / 200, the key
     */
    public PhysicalPosWriteResult createTerminal(Map<String, ?> request, String idempotencyKey) {
        return keyedWrite(BASE + "/terminals", PhysicalPosSupport.body(request, CREATE_TERMINAL), idempotencyKey,
                "terminal", 201);
    }

    /** @return {@code {terminal, acquirers: {items, pageInfo}}} ({@code GET /terminals/{terminalId}}) */
    public Map<String, Object> getTerminal(String terminalId) {
        return PhysicalPosSupport.data(get(terminalPath(terminalId), null, List.of(), "getTerminal"));
    }

    /** Updates a terminal ({@code rowVersion}, {@code displayName}, {@code vendorCode} once). @return the terminal */
    public Map<String, Object> updateTerminal(String terminalId, Map<String, ?> request) {
        return entity(write("PATCH", terminalPath(terminalId), PhysicalPosSupport.body(request, UPDATE_TERMINAL)),
                "terminal");
    }

    /** Closes a terminal ({@code {rowVersion, reasonCode}}). @return the terminal */
    public Map<String, Object> closeTerminal(String terminalId, Map<String, ?> request) {
        return entity(write("POST", terminalPath(terminalId) + "/close",
                PhysicalPosSupport.body(request, STATUS_CHANGE)), "terminal");
    }

    /**
     * @return the terminal's bank rows ({@code GET /terminals/{terminalId}/acquirers}; limit, cursor, includeTotal).
     *         A row's {@code id} is the {@code terminalAcquirerId} of a payment.
     */
    public PhysicalPosPage listTerminalAcquirers(String terminalId, Map<String, ?> filters) {
        return page(terminalPath(terminalId) + "/acquirers", filters, ENTITY_PAGE_FILTERS, "listTerminalAcquirers");
    }

    /** @return every bank row of the terminal, page by page */
    public Iterable<Map<String, Object>> iterateTerminalAcquirers(String terminalId, Map<String, ?> filters) {
        String path = terminalPath(terminalId) + "/acquirers";
        return PhysicalPosSupport.iterate(filters,
                page -> page(path, page, ENTITY_PAGE_FILTERS, "listTerminalAcquirers"));
    }

    /**
     * Adds a bank row ({@code POST /terminals/{terminalId}/acquirers}, Idempotency-Key): {@code vendorAcquirerRef},
     * {@code merchantNumber}, {@code terminalNumber}, {@code currencies}, optional {@code label}.
     *
     * @return {@code acquirer}, {@code replayed}, status 201 / 200, the key
     */
    public PhysicalPosWriteResult addTerminalAcquirer(String terminalId, Map<String, ?> request,
                                                      String idempotencyKey) {
        return keyedWrite(terminalPath(terminalId) + "/acquirers", PhysicalPosSupport.body(request, ADD_ACQUIRER),
                idempotencyKey, "acquirer", 201);
    }

    /**
     * Updates a bank row ({@code PATCH /terminal-acquirers/{terminalAcquirerId}}: {@code rowVersion}, {@code label}
     * (null clears), {@code currencies}, {@code status}).
     *
     * @return the bank row
     */
    public Map<String, Object> updateTerminalAcquirer(String terminalAcquirerId, Map<String, ?> request) {
        String path = BASE + "/terminal-acquirers/" + PhysicalPosSupport.segment(terminalAcquirerId,
                "terminalAcquirerId");
        return entity(write("PATCH", path, PhysicalPosSupport.body(request, UPDATE_ACQUIRER)), "acquirer");
    }

    /**
     * Links the terminal to a device, closing the previous link ({@code POST /terminals/{terminalId}/device-link}:
     * {@code rowVersion}, {@code deviceId}, {@code reasonCode}).
     *
     * @return {@code {terminal, link}}
     */
    public Map<String, Object> changeTerminalDevice(String terminalId, Map<String, ?> request) {
        return PhysicalPosSupport.data(write("POST", terminalPath(terminalId) + "/device-link",
                PhysicalPosSupport.body(request, CHANGE_TERMINAL_DEVICE)));
    }

    /**
     * Unlinks the terminal's device ({@code POST /terminals/{terminalId}/device-link/close}, Idempotency-Key:
     * {@code rowVersion}, {@code reasonCode}).
     *
     * @return {@code terminal} with {@code link} (or null) in {@link PhysicalPosWriteResult#data()}, the key
     */
    public PhysicalPosWriteResult closeTerminalDeviceLink(String terminalId, Map<String, ?> request,
                                                          String idempotencyKey) {
        return keyedWrite(terminalPath(terminalId) + "/device-link/close",
                PhysicalPosSupport.body(request, STATUS_CHANGE), idempotencyKey, "terminal", 200);
    }

    /** @return the terminal's device link history ({@code limit}, {@code cursor}) */
    public PhysicalPosPage listTerminalLinks(String terminalId, Map<String, ?> filters) {
        return page(terminalPath(terminalId) + "/links", filters, PAGE_FILTERS, "listTerminalLinks");
    }

    /** @return every device link of the terminal, page by page */
    public Iterable<Map<String, Object>> iterateTerminalLinks(String terminalId, Map<String, ?> filters) {
        String path = terminalPath(terminalId) + "/links";
        return PhysicalPosSupport.iterate(filters, page -> page(path, page, PAGE_FILTERS, "listTerminalLinks"));
    }

    /** @return the terminal's audit events ({@code limit}, {@code cursor}) */
    public PhysicalPosPage listTerminalAudit(String terminalId, Map<String, ?> filters) {
        return page(terminalPath(terminalId) + "/audit", filters, PAGE_FILTERS, "listTerminalAudit");
    }

    /** @return every audit event of the terminal, page by page */
    public Iterable<Map<String, Object>> iterateTerminalAudit(String terminalId, Map<String, ?> filters) {
        String path = terminalPath(terminalId) + "/audit";
        return PhysicalPosSupport.iterate(filters, page -> page(path, page, PAGE_FILTERS, "listTerminalAudit"));
    }

    // ── Devices ──────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * @param filters {@code limit}, {@code cursor}, {@code includeTotal}, {@code storeId}, {@code registerId},
     *                {@code vendorCode}, {@code deviceModelId}, {@code deviceType}, {@code lifecycleStatus},
     *                {@code ownershipStatus}, {@code integrationChannel}, {@code q}, or {@code null}
     * @return devices ({@code GET /devices})
     */
    public PhysicalPosPage listDevices(Map<String, ?> filters) {
        return page(BASE + "/devices", filters, DEVICE_FILTERS, "listDevices");
    }

    /** @return every device matching the filters, page by page */
    public Iterable<Map<String, Object>> iterateDevices(Map<String, ?> filters) {
        return PhysicalPosSupport.iterate(filters, this::listDevices);
    }

    /**
     * Registers a device ({@code POST /devices}, Idempotency-Key): {@code vendorCode}, {@code deviceModelId},
     * {@code serialNumber}, {@code integrationChannel}, optional {@code displayName}, {@code externalRef},
     * {@code storeId}, {@code registerId}, {@code channelVariant}, {@code appMode}.
     *
     * @return {@code device}, {@code replayed}, status 201 / 200, the key
     */
    public PhysicalPosWriteResult createDevice(Map<String, ?> request, String idempotencyKey) {
        return keyedWrite(BASE + "/devices", PhysicalPosSupport.body(request, CREATE_DEVICE), idempotencyKey,
                "device", 201);
    }

    /**
     * The device detail ({@code GET /devices/{deviceId}}). Only this read fills {@code presence}:
     * {@code connectionState} ({@code ONLINE} | {@code OFFLINE} | {@code UNKNOWN}), {@code lastSeenAt},
     * {@code stateChangedAt}, {@code offlineReason}, {@code appVersion}, {@code appHealthy},
     * {@code paymentAppReachable}, {@code readyForPayment}, {@code notReadyReason}, {@code source},
     * {@code wakePaths}, {@code busy}. A payment on a device that is not ready gets {@code 409
     * physical_pos.payment.device_offline} (device-side reason) or {@code 503 physical_pos.presence.unavailable};
     * write results carry {@code presence: null}.
     *
     * @return the device detail
     */
    public Map<String, Object> getDevice(String deviceId) {
        return entity(get(devicePath(deviceId), null, List.of(), "getDevice"), "device");
    }

    /**
     * Updates a device ({@code PATCH /devices/{deviceId}}: {@code rowVersion}, {@code displayName},
     * {@code externalRef}, {@code integrationChannel}, {@code channelVariant}, {@code appMode}; null clears the
     * clearable ones).
     *
     * @return the device
     */
    public Map<String, Object> updateDevice(String deviceId, Map<String, ?> request) {
        return entity(write("PATCH", devicePath(deviceId), PhysicalPosSupport.body(request, UPDATE_DEVICE)), "device");
    }

    /**
     * Assigns a device to a store / register ({@code POST /devices/{deviceId}/assignment}: {@code rowVersion},
     * {@code storeId}, {@code registerId}, {@code reasonCode}).
     *
     * @return {@code {device, assignment, previousAssignmentId}}
     */
    public Map<String, Object> assignDevice(String deviceId, Map<String, ?> request) {
        return PhysicalPosSupport.data(write("POST", devicePath(deviceId) + "/assignment",
                PhysicalPosSupport.body(request, ASSIGN_DEVICE)));
    }

    /**
     * Moves a device to storage ({@code POST /devices/{deviceId}/assignment/close}, Idempotency-Key:
     * {@code rowVersion}, {@code reasonCode}).
     *
     * @return {@code device} with {@code closedAssignment} in {@link PhysicalPosWriteResult#data()}, the key
     */
    public PhysicalPosWriteResult closeDeviceAssignment(String deviceId, Map<String, ?> request,
                                                        String idempotencyKey) {
        return keyedWrite(devicePath(deviceId) + "/assignment/close", PhysicalPosSupport.body(request, STATUS_CHANGE),
                idempotencyKey, "device", 200);
    }

    /**
     * Changes a device's lifecycle ({@code POST /devices/{deviceId}/lifecycle}: {@code rowVersion}, {@code target}
     * {@code ACTIVE} | {@code SUSPENDED} | {@code MAINTENANCE}, {@code reasonCode}).
     *
     * @return the device
     */
    public Map<String, Object> changeDeviceLifecycle(String deviceId, Map<String, ?> request) {
        return entity(write("POST", devicePath(deviceId) + "/lifecycle",
                PhysicalPosSupport.body(request, DEVICE_LIFECYCLE)), "device");
    }

    /**
     * Retires a device for good ({@code POST /devices/{deviceId}/retire}, Idempotency-Key: {@code rowVersion},
     * {@code reasonCode}).
     *
     * @return {@code device}, {@code replayed}, the key
     */
    public PhysicalPosWriteResult retireDevice(String deviceId, Map<String, ?> request, String idempotencyKey) {
        return keyedWrite(devicePath(deviceId) + "/retire", PhysicalPosSupport.body(request, STATUS_CHANGE),
                idempotencyKey, "device", 200);
    }

    /** @return the device's assignment history ({@code limit}, {@code cursor}) */
    public PhysicalPosPage listDeviceAssignments(String deviceId, Map<String, ?> filters) {
        return page(devicePath(deviceId) + "/assignments", filters, PAGE_FILTERS, "listDeviceAssignments");
    }

    /** @return every assignment of the device, page by page */
    public Iterable<Map<String, Object>> iterateDeviceAssignments(String deviceId, Map<String, ?> filters) {
        String path = devicePath(deviceId) + "/assignments";
        return PhysicalPosSupport.iterate(filters, page -> page(path, page, PAGE_FILTERS, "listDeviceAssignments"));
    }

    /** @return the device's audit events ({@code limit}, {@code cursor}) */
    public PhysicalPosPage listDeviceAudit(String deviceId, Map<String, ?> filters) {
        return page(devicePath(deviceId) + "/audit", filters, PAGE_FILTERS, "listDeviceAudit");
    }

    /** @return every audit event of the device, page by page */
    public Iterable<Map<String, Object>> iterateDeviceAudit(String deviceId, Map<String, ?> filters) {
        String path = devicePath(deviceId) + "/audit";
        return PhysicalPosSupport.iterate(filters, page -> page(path, page, PAGE_FILTERS, "listDeviceAudit"));
    }

    // ── Internals ────────────────────────────────────────────────────────────────────────────────────────────

    private static String storePath(String storeId) {
        return BASE + "/stores/" + PhysicalPosSupport.segment(storeId, "storeId");
    }

    private static String registerPath(String registerId) {
        return BASE + "/registers/" + PhysicalPosSupport.segment(registerId, "registerId");
    }

    private static String terminalPath(String terminalId) {
        return BASE + "/terminals/" + PhysicalPosSupport.segment(terminalId, "terminalId");
    }

    private static String devicePath(String deviceId) {
        return BASE + "/devices/" + PhysicalPosSupport.segment(deviceId, "deviceId");
    }

    private Map<String, Object> get(String path, Map<String, ?> filters, List<String> allowed, String route) {
        RequestOptions options = PhysicalPosSupport.query(filters, allowed, route).build();
        return PhysicalPosSupport.send(api, "GET", path, null, options, null);
    }

    private Map<String, Object> write(String method, String path, Map<String, Object> body) {
        RequestOptions options = RequestOptions.builder().stepUp(true).build();
        return PhysicalPosSupport.send(api, method, path, body, options, null);
    }

    private PhysicalPosWriteResult keyedWrite(String path, Map<String, Object> body, String idempotencyKey,
                                              String entityName, int newStatus) {
        String key = PhysicalPosSupport.idempotencyKey(idempotencyKey);
        RequestOptions options = RequestOptions.builder().stepUp(true).idempotencyKey(key).build();
        Map<String, Object> envelope = PhysicalPosSupport.send(api, "POST", path, body, options, key);
        Map<String, Object> data = PhysicalPosSupport.data(envelope);
        boolean replayed = Boolean.TRUE.equals(data.get("replayed"));
        int status = PhysicalPosSupport.statusCode(envelope, replayed ? 200 : newStatus);
        return new PhysicalPosWriteResult(entityName, data, status, key);
    }

    private PhysicalPosPage page(String path, Map<String, ?> filters, List<String> allowed, String route) {
        return new PhysicalPosPage(get(path, filters, allowed, route));
    }

    private static Map<String, Object> entity(Map<String, Object> envelope, String name) {
        return PhysicalPosSupport.object(PhysicalPosSupport.data(envelope).get(name));
    }
}
