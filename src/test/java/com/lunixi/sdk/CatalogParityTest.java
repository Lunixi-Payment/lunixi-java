package com.lunixi.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.lunixi.sdk.bankconnect.BankConnectEvents;
import com.lunixi.sdk.bankconnect.BankConnectValues;
import com.lunixi.sdk.bankconnect.BankTransferEvents;
import com.lunixi.sdk.bankconnect.BankTransferRoutes;
import com.lunixi.sdk.http.Route;
import com.lunixi.sdk.identity.IdentityRoutes;
import com.lunixi.sdk.insurance.InsuranceEvents;
import com.lunixi.sdk.insurance.InsuranceValues;
import com.lunixi.sdk.json.Json;
import com.lunixi.sdk.kyc.KycEvents;
import com.lunixi.sdk.kyc.KycRoutes;
import com.lunixi.sdk.kyc.KycValues;
import com.lunixi.sdk.marketplace.MarketplaceRoutes;
import com.lunixi.sdk.marketplace.MarketplaceValues;
import com.lunixi.sdk.payment.PaymentBankTransferEvents;
import com.lunixi.sdk.payment.PaymentBankTransferExceptionStatus;
import com.lunixi.sdk.payment.PaymentMethod;
import com.lunixi.sdk.payment.ProductCategory;
import com.lunixi.sdk.payment.SplitPaymentValues;
import com.lunixi.sdk.paymentlink.PaymentLinkValues;
import com.lunixi.sdk.sanction.SanctionRoutes;
import com.lunixi.sdk.sanction.SanctionValues;
import com.lunixi.sdk.subscription.SubscriptionEvents;
import com.lunixi.sdk.subscription.SubscriptionRoutes;
import com.lunixi.sdk.subscription.SubscriptionValues;
import com.lunixi.sdk.wallet.WalletRoutes;
import com.lunixi.sdk.wallet.WalletValues;

/**
 * Cross-SDK parity against the shared catalog at the repository root ({@code contracts/products/*.json}, written
 * from the Node and PHP SDKs). Every {@code required} operation of the products this SDK carries must be a public
 * method reachable from {@link LunixiClient} through its accessors ({@code paymentLinks.create} →
 * {@code client.paymentLinks().create(…)}). Names that differ on purpose come from
 * {@code contracts/sdk-names.json} ({@code "java"}) and {@link #LOCAL_NAMES}. The route tables ({@code WALLET_ROUTES},
 * {@code SANCTION_ROUTES}, {@code BANK_TRANSFER_ROUTES}, {@code SUBSCRIPTION_ROUTES}, {@code KYC_ROUTES},
 * {@code IDENTITY_ROUTES}, {@code MARKETPLACE_ROUTES}) are compared field by field, and the catalog's constant groups
 * name by name. Outside the monorepo (the public repository copy) the catalog is absent and the test is skipped.
 */
class CatalogParityTest {

    /** Products this SDK carries; the others are out of this check on purpose. */
    static final List<String> PRODUCTS = List.of("payments", "fraud", "payment-links", "reference", "wallet",
            "bank-connect", "sanction", "insurance", "subscriptions", "kyc", "identity", "marketplace");

    /**
     * Java names that differ from the catalog and are not yet in {@code contracts/sdk-names.json}: {@code void} is a
     * Java keyword. The shared file wins when it names the operation.
     */
    static final Map<String, String> LOCAL_NAMES = Map.of("subscriptions.invoices.void", "voidInvoice");

    static Path contracts;
    static Map<String, Object> javaNames;

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void locate() throws IOException {
        Path dir = Path.of("").toAbsolutePath();
        for (int level = 0; level < 4 && dir != null && contracts == null; level++, dir = dir.getParent()) {
            if (Files.isDirectory(dir.resolve("contracts").resolve("products"))) {
                contracts = dir.resolve("contracts");
            }
        }
        javaNames = new LinkedHashMap<>(LOCAL_NAMES);
        if (contracts != null && Files.isRegularFile(contracts.resolve("sdk-names.json"))) {
            Object names = read(contracts.resolve("sdk-names.json")).get("java");
            if (names instanceof Map<?, ?> map) {
                javaNames.putAll((Map<String, Object>) map);
            }
        }
    }

    static Map<String, Object> read(Path file) throws IOException {
        return Json.parseObject(Files.readString(file, StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    static Map<String, Map<String, Object>> section(String product, String name) throws IOException {
        Path file = contracts.resolve("products").resolve(product + ".json");
        assumeTrue(Files.isRegularFile(file), "catalog file missing: " + file);
        return (Map<String, Map<String, Object>>) read(file).get(name);
    }

    @Test
    void everyRequiredOperationIsAPublicMethod() throws IOException {
        assumeTrue(contracts != null, "contracts/products not found; outside the monorepo");
        List<String> missing = new ArrayList<>();
        int checked = 0;
        for (String product : PRODUCTS) {
            Map<String, Map<String, Object>> operations = section(product, "operations");
            assertFalse(operations == null || operations.isEmpty(), product + " has no operations");
            for (Map.Entry<String, Map<String, Object>> operation : operations.entrySet()) {
                if (!"required".equals(operation.getValue().get("status"))) {
                    continue;
                }
                String name = operation.getKey();
                if (javaNames.containsKey(name) && javaNames.get(name) == null) {
                    continue;
                }
                checked++;
                if (!resolves(name, (String) javaNames.get(name))) {
                    missing.add(name);
                }
            }
        }
        assertFalse(checked == 0, "no required operation was checked");
        assertEquals(List.of(), missing, "required catalog operations without a Java method");
    }

    /** {@code a.b.leaf}: {@code a()} and {@code b()} are accessors without arguments, {@code leaf} a public method. */
    static boolean resolves(String operation, String override) {
        String[] parts = operation.split("\\.");
        Class<?> type = LunixiClient.class;
        for (int i = 0; i < parts.length - 1; i++) {
            try {
                Method accessor = type.getMethod(parts[i]);
                if (Modifier.isStatic(accessor.getModifiers())) {
                    return false;
                }
                type = accessor.getReturnType();
            } catch (NoSuchMethodException noAccessor) {
                return false;
            }
        }
        String leaf = override != null ? override : parts[parts.length - 1];
        return Arrays.stream(type.getMethods())
                .anyMatch(method -> method.getName().equals(leaf) && !Modifier.isStatic(method.getModifiers())
                        && method.getDeclaringClass() != Object.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void wireListsMatchTheCatalog() throws IOException {
        assumeTrue(contracts != null, "contracts/products not found; outside the monorepo");
        Map<String, Map<String, List<String>>> expected = new LinkedHashMap<>();
        expected.put("payments", Map.of(
                "METHOD_BLOCK_FIELDS", PaymentMethod.METHOD_BLOCK_FIELDS,
                "CARD_ONLY_FIELDS", PaymentMethod.CARD_ONLY_FIELDS,
                "DIRECT_METHOD_PAYMENT_FIELDS", PaymentMethod.DIRECT_METHOD_PAYMENT_FIELDS,
                "PAYMENT_ACTION_CONFIRM_FIELDS", PaymentMethod.PAYMENT_ACTION_CONFIRM_FIELDS,
                "LIST_SPLIT_PAYMENTS_FILTERS", SplitPaymentValues.LIST_SPLIT_PAYMENTS_FILTERS,
                "SPLIT_PAYMENT_FIELDS", SplitPaymentValues.SPLIT_PAYMENT_FIELDS,
                "BankTransferEvents.ALL", PaymentBankTransferEvents.ALL,
                "BankTransferException.STATUSES", PaymentBankTransferExceptionStatus.ALL,
                "ProductCategory.ALL", ProductCategory.ALL));
        expected.put("payment-links", Map.of(
                "PAYMENT_LINK_SPEC_FIELDS", PaymentLinkValues.PAYMENT_LINK_SPEC_FIELDS,
                "PAYMENT_LINK_IMMUTABLE_FIELDS", PaymentLinkValues.PAYMENT_LINK_IMMUTABLE_FIELDS,
                "PaymentLinkState.ALL", PaymentLinkValues.State.ALL,
                "PaymentLinkAttemptState.ALL", PaymentLinkValues.AttemptState.ALL,
                "PaymentLinkUsage.ALL", PaymentLinkValues.Usage.ALL,
                "PaymentLinkAmountMode.ALL", PaymentLinkValues.AmountMode.ALL,
                "PaymentLinkCustomField.TYPES", PaymentLinkValues.CustomFieldType.ALL));
        Map<String, List<String>> wallet = new LinkedHashMap<>();
        wallet.put("WALLET_AMOUNT_FIELDS", WalletRoutes.AMOUNT_FIELDS);
        wallet.put("WalletAccountType.ALL", WalletValues.AccountType.ALL);
        wallet.put("WalletEndUserStatus.ALL", WalletValues.EndUserStatus.ALL);
        wallet.put("WalletKycLevel.ALL", WalletValues.KycLevel.ALL);
        wallet.put("WalletOtpChannel.ALL", WalletValues.OtpChannel.ALL);
        wallet.put("WalletQrType.ALL", WalletValues.QrType.ALL);
        wallet.put("WalletQrImageFormat.ALL", WalletValues.QrImageFormat.ALL);
        wallet.put("WalletBulkTargetType.ALL", WalletValues.BulkTargetType.ALL);
        wallet.put("WalletCorporateAccountStatus.ALL", WalletValues.CorporateAccountStatus.ALL);
        expected.put("wallet", wallet);
        Map<String, List<String>> bank = new LinkedHashMap<>();
        BankConnectValues.FILTERS.forEach((route, filters) -> bank.put("BANK_CONNECT_FILTERS." + route, filters));
        bank.put("BANK_CONNECT_EVENT_TYPES", BankConnectEvents.ALL);
        bank.put("BANK_TRANSFER_EVENT_TYPES", BankTransferEvents.ALL);
        bank.put("BankConnectionStatus.ALL", BankConnectValues.ConnectionStatus.ALL);
        bank.put("BankAccountStatus.ALL", BankConnectValues.AccountStatus.ALL);
        bank.put("BankTransactionDirection.ALL", BankConnectValues.TransactionDirection.ALL);
        bank.put("BankTransactionType.ALL", BankConnectValues.TransactionType.ALL);
        bank.put("BankAvailability.ALL", BankConnectValues.Availability.ALL);
        bank.put("BankCapability.ALL", BankConnectValues.Capability.ALL);
        bank.put("BankSyncStatus.ALL", BankConnectValues.SyncStatus.ALL);
        bank.put("BankTransferStatus.ALL", BankConnectValues.TransferStatus.ALL);
        bank.put("BankTransferRail.ALL", BankConnectValues.TransferRail.ALL);
        bank.put("BankTransferInitiatedVia.ALL", BankConnectValues.TransferInitiatedVia.ALL);
        bank.put("BankFxOrderStatus.ALL", BankConnectValues.FxOrderStatus.ALL);
        bank.put("BankFxRequestedSide.ALL", BankConnectValues.FxRequestedSide.ALL);
        expected.put("bank-connect", bank);
        Map<String, List<String>> sanction = new LinkedHashMap<>();
        SanctionRoutes.VALUE_SETS.forEach((field, values) -> sanction.put("SANCTION_VALUE_SETS." + field, values));
        sanction.put("RiskDomain.ALL", SanctionValues.RiskDomain.ALL);
        sanction.put("ScreenMode.ALL", SanctionValues.ScreenMode.ALL);
        sanction.put("WalletScreenMode.ALL", SanctionValues.WalletScreenMode.ALL);
        sanction.put("SubjectType.ALL", SanctionValues.SubjectType.ALL);
        sanction.put("CoverageStatus.ALL", SanctionValues.CoverageStatus.ALL);
        sanction.put("HitType.ALL", SanctionValues.HitType.ALL);
        expected.put("sanction", sanction);
        Map<String, List<String>> insurance = new LinkedHashMap<>();
        insurance.put("INSURANCE_QUOTE_FIELDS", InsuranceValues.QUOTE_FIELDS);
        insurance.put("INSURANCE_LEAD_FIELDS", InsuranceValues.LEAD_FIELDS);
        insurance.put("INSURANCE_PRODUCT_SEARCH_FIELDS", InsuranceValues.PRODUCT_SEARCH_FIELDS);
        insurance.put("INSURANCE_ORDER_FIELDS", InsuranceValues.ORDER_FIELDS);
        insurance.put("INSURANCE_CONSENT_FIELDS", InsuranceValues.CONSENT_FIELDS);
        insurance.put("INSURANCE_EXTERNAL_PAYMENT_FIELDS", InsuranceValues.EXTERNAL_PAYMENT_FIELDS);
        insurance.put("INSURANCE_EXTERNAL_REFUND_FIELDS", InsuranceValues.EXTERNAL_REFUND_FIELDS);
        InsuranceValues.FILTERS.forEach((route, filters) -> insurance.put("INSURANCE_FILTERS." + route, filters));
        insurance.put("INSURANCE_EVENT_TYPES", InsuranceEvents.ALL);
        insurance.put("InsuranceEvents.ORDER_EVENTS", InsuranceEvents.ORDER_EVENTS);
        insurance.put("InsuranceEvents.LEAD_EVENTS", InsuranceEvents.LEAD_EVENTS);
        insurance.put("InsurancePremiumCollectedBy.ALL", InsuranceValues.PremiumCollectedBy.ALL);
        insurance.put("InsuranceQuoteStatus.ALL", InsuranceValues.QuoteStatus.ALL);
        insurance.put("InsuranceOrderStatus.ALL", InsuranceValues.OrderStatus.ALL);
        insurance.put("InsuranceCollectionStatus.ALL", InsuranceValues.CollectionStatus.ALL);
        insurance.put("InsurancePolicyStatus.ALL", InsuranceValues.PolicyStatus.ALL);
        insurance.put("InsuranceRefundStatus.ALL", InsuranceValues.RefundStatus.ALL);
        insurance.put("InsuranceDocumentStatus.ALL", InsuranceValues.DocumentStatus.ALL);
        insurance.put("InsurancePaymentMode.ALL", InsuranceValues.PaymentMode.ALL);
        insurance.put("InsuranceOrderSurface.ALL", InsuranceValues.OrderSurface.ALL);
        insurance.put("InsuranceLeadStatus.ALL", InsuranceValues.LeadStatus.ALL);
        insurance.put("InsuranceReferenceKind.ALL", InsuranceValues.ReferenceKind.ALL);
        expected.put("insurance", insurance);
        Map<String, List<String>> subscriptions = new LinkedHashMap<>();
        subscriptions.put("SUBSCRIPTION_ACTIVE_STATES", SubscriptionValues.Status.ACTIVE_STATES);
        subscriptions.put("SUBSCRIPTION_TERMINAL_STATES", SubscriptionValues.Status.TERMINAL_STATES);
        subscriptions.put("SubscriptionStatus.ACTIVE_STATES", SubscriptionValues.Status.ACTIVE_STATES);
        subscriptions.put("SubscriptionStatus.TERMINAL_STATES", SubscriptionValues.Status.TERMINAL_STATES);
        subscriptions.put("SUBSCRIPTION_BILLING_RULE_FIELDS", SubscriptionRoutes.BILLING_RULE_FIELDS);
        subscriptions.put("SUBSCRIPTION_EVENT_TYPES", SubscriptionEvents.ALL);
        subscriptions.put("CollectionMethod.ALL", SubscriptionValues.CollectionMethod.ALL);
        expected.put("subscriptions", subscriptions);
        Map<String, List<String>> kyc = new LinkedHashMap<>();
        kyc.put("KYC_EVENT_TYPES", KycEvents.ALL);
        kyc.put("KYC_SESSION_TERMINAL_STATES", KycValues.SessionStatus.TERMINAL_STATES);
        kyc.put("SessionStatus.TERMINAL_STATES", KycValues.SessionStatus.TERMINAL_STATES);
        kyc.put("SessionType.ALL", KycValues.SessionType.ALL);
        kyc.put("EvidencePurpose.ALL", KycValues.EvidencePurpose.ALL);
        kyc.put("AchievedAssurance.DOMAINS", KycValues.AssuranceDomain.ALL);
        expected.put("kyc", kyc);
        expected.put("identity", Map.of("IDENTITY_ANALYZE_FIELDS", IdentityRoutes.ANALYZE_FIELDS));
        expected.put("marketplace", Map.of(
                "MARKETPLACE_SELLER_FIELDS", MarketplaceRoutes.SELLER_FIELDS,
                "MARKETPLACE_CHARGE_REQUIRED_FIELDS", MarketplaceRoutes.CHARGE_REQUIRED_FIELDS));
        int compared = 0;
        for (Map.Entry<String, Map<String, List<String>>> product : expected.entrySet()) {
            Map<String, Map<String, Object>> groups = section(product.getKey(), "groups");
            for (Map.Entry<String, List<String>> list : product.getValue().entrySet()) {
                Map<String, Object> group = groups.get(list.getKey());
                if (group == null) {
                    continue;
                }
                assertEquals((List<String>) group.get("values"), list.getValue(),
                        product.getKey() + " " + list.getKey());
                compared++;
            }
        }
        assertFalse(compared == 0, "no catalog list was compared");
    }

    @Test
    @SuppressWarnings("unchecked")
    void patternsMatchTheCatalog() throws IOException {
        assumeTrue(contracts != null, "contracts/products not found; outside the monorepo");
        Map<String, Map<String, String>> expected = new LinkedHashMap<>();
        expected.put("wallet", Map.of("AMOUNT_PATTERN", WalletRoutes.AMOUNT_PATTERN.pattern()));
        expected.put("bank-connect", Map.of(
                "BANK_TRANSFER_IDEMPOTENCY_KEY_PATTERN", BankTransferRoutes.IDEMPOTENCY_KEY_PATTERN.pattern(),
                "BANK_TRANSFER_PAYMENT_PURPOSE_PATTERN", BankTransferRoutes.PAYMENT_PURPOSE_PATTERN.pattern()));
        expected.put("insurance", Map.of(
                "INSURANCE_IDEMPOTENCY_KEY_PATTERN", InsuranceValues.IDEMPOTENCY_KEY_PATTERN.pattern()));
        int compared = 0;
        for (Map.Entry<String, Map<String, String>> product : expected.entrySet()) {
            Map<String, Map<String, Object>> groups = section(product.getKey(), "groups");
            for (Map.Entry<String, String> pattern : product.getValue().entrySet()) {
                Map<String, Object> group = groups.get(pattern.getKey());
                if (group == null) {
                    continue;
                }
                assertEquals(((Map<String, Object>) group.get("values")).get("$pattern"), pattern.getValue(),
                        product.getKey() + " " + pattern.getKey());
                compared++;
            }
        }
        assertFalse(compared == 0, "no catalog pattern was compared");
    }

    /**
     * Each route table matches the catalog's {@code routes} group row for row: the same route names, and per route
     * the method, the path, the key rule, the signing, screening, binary, free-body, optional-body and any-query
     * flags, and the body / query field lists in order.
     */
    @Test
    void routeTablesMatchTheCatalog() throws IOException {
        assumeTrue(contracts != null, "contracts/products not found; outside the monorepo");
        assertRoutes("wallet", "WALLET_ROUTES", WalletRoutes.ROUTES);
        assertRoutes("sanction", "SANCTION_ROUTES", SanctionRoutes.ROUTES);
        assertRoutes("bank-connect", "BANK_TRANSFER_ROUTES", BankTransferRoutes.ROUTES);
        assertRoutes("subscriptions", "SUBSCRIPTION_ROUTES", SubscriptionRoutes.ROUTES);
        assertRoutes("kyc", "KYC_ROUTES", KycRoutes.ROUTES);
        assertRoutes("identity", "IDENTITY_ROUTES", IdentityRoutes.ROUTES);
        assertRoutes("marketplace", "MARKETPLACE_ROUTES", MarketplaceRoutes.ROUTES);
    }

    /**
     * Each constant group of the catalog ({@code kind: enum}, written from the Node SDK) is a Java constants class
     * with the same names and values: {@code SubscriptionStatus.PAST_DUE} → {@code SubscriptionValues.Status.PAST_DUE}.
     */
    @Test
    @SuppressWarnings("unchecked")
    void constantGroupsMatchTheCatalog() throws IOException, ReflectiveOperationException {
        assumeTrue(contracts != null, "contracts/products not found; outside the monorepo");
        Map<String, Map<String, Class<?>>> expected = new LinkedHashMap<>();
        Map<String, Class<?>> subscriptions = new LinkedHashMap<>();
        subscriptions.put("SubscriptionStatus", SubscriptionValues.Status.class);
        subscriptions.put("SubscriptionInvoiceStatus", SubscriptionValues.InvoiceStatus.class);
        subscriptions.put("SubscriptionCollectionMethod", SubscriptionValues.CollectionMethod.class);
        subscriptions.put("SubscriptionBillingFrequency", SubscriptionValues.BillingFrequency.class);
        subscriptions.put("SubscriptionCardVerificationMode", SubscriptionValues.CardVerificationMode.class);
        subscriptions.put("SubscriptionPlanChangeBehavior", SubscriptionValues.PlanChangeBehavior.class);
        subscriptions.put("SubscriptionPortalSdkMode", SubscriptionValues.PortalSdkMode.class);
        subscriptions.put("SubscriptionEvents", SubscriptionEvents.class);
        expected.put("subscriptions", subscriptions);
        Map<String, Class<?>> kyc = new LinkedHashMap<>();
        kyc.put("KycSessionType", KycValues.SessionType.class);
        kyc.put("KycSessionStatus", KycValues.SessionStatus.class);
        kyc.put("KycEvidencePurpose", KycValues.EvidencePurpose.class);
        kyc.put("KycAssuranceLevel", KycValues.AssuranceLevel.class);
        kyc.put("KycAssuranceDomain", KycValues.AssuranceDomain.class);
        kyc.put("KycEvents", KycEvents.class);
        expected.put("kyc", kyc);
        Map<String, Class<?>> marketplace = new LinkedHashMap<>();
        marketplace.put("MarketplaceDealerStatus", MarketplaceValues.DealerStatus.class);
        marketplace.put("MarketplaceDealerType", MarketplaceValues.DealerType.class);
        marketplace.put("MarketplaceMultiSellerPolicy", MarketplaceValues.MultiSellerPolicy.class);
        expected.put("marketplace", marketplace);
        int compared = 0;
        for (Map.Entry<String, Map<String, Class<?>>> product : expected.entrySet()) {
            Map<String, Map<String, Object>> groups = section(product.getKey(), "groups");
            for (Map.Entry<String, Class<?>> constants : product.getValue().entrySet()) {
                Map<String, Object> group = groups.get(constants.getKey());
                assumeTrue(group != null, product.getKey() + " has no " + constants.getKey() + " group");
                Map<String, Object> values = (Map<String, Object>) group.get("values");
                for (Map.Entry<String, Object> value : values.entrySet()) {
                    assertEquals(value.getValue(), constants.getValue().getField(value.getKey()).get(null),
                            product.getKey() + " " + constants.getKey() + "." + value.getKey());
                }
                Object all = constants.getValue().getField("ALL").get(null);
                assertEquals(List.copyOf(values.values()), all, product.getKey() + " " + constants.getKey() + " ALL");
                compared++;
            }
        }
        assertEquals("https://portal.lunixi.com", SubscriptionValues.PORTAL_HOSTED_BASE);
        assertEquals(((Map<String, Object>) section("subscriptions", "groups").get("PortalLink").get("values"))
                .get("DEFAULT_HOSTED_BASE"), SubscriptionValues.PORTAL_HOSTED_BASE);
        assertEquals(((Map<String, Object>) section("marketplace", "groups").get("DealerOnboardingLink")
                .get("values")).get("DEFAULT_HOSTED_BASE"), MarketplaceValues.DEALER_ONBOARDING_HOSTED_BASE);
        assertFalse(compared == 0, "no catalog constant group was compared");
    }

    @SuppressWarnings("unchecked")
    static void assertRoutes(String product, String groupName, Map<String, Route> table) throws IOException {
        Map<String, Object> group = section(product, "groups").get(groupName);
        assumeTrue(group != null, product + " has no " + groupName + " group");
        assertEquals("routes", group.get("kind"), groupName);
        Map<String, Map<String, Object>> catalog = (Map<String, Map<String, Object>>) group.get("values");
        assertEquals(List.copyOf(catalog.keySet()), List.copyOf(table.keySet()), groupName + " route names");
        for (Map.Entry<String, Map<String, Object>> row : catalog.entrySet()) {
            String label = groupName + "." + row.getKey();
            Map<String, Object> expected = row.getValue();
            Route route = table.get(row.getKey());
            assertEquals(expected.get("method"), route.method(), label + " method");
            assertEquals(expected.get("path"), route.path(), label + " path");
            Object key = expected.get("idempotencyKey");
            if (key instanceof String rule) {
                assertEquals(rule, route.keyRule().wire(), label + " idempotencyKey");
            } else {
                assertEquals(Boolean.TRUE.equals(key), route.idempotencyKey(), label + " idempotencyKey");
            }
            if (expected.containsKey("stepUp")) {
                assertEquals(expected.get("stepUp"), route.stepUp(), label + " stepUp");
            }
            assertEquals(Boolean.TRUE.equals(expected.get("free")), route.free(), label + " free");
            assertEquals(Boolean.TRUE.equals(expected.get("optionalBody")), route.optionalBody(),
                    label + " optionalBody");
            assertEquals(Boolean.TRUE.equals(expected.get("anyQuery")), route.anyQuery(), label + " anyQuery");
            assertEquals(Boolean.TRUE.equals(expected.get("screening")), route.screening(), label + " screening");
            assertEquals(Boolean.TRUE.equals(expected.get("binary")), route.binary(), label + " binary");
            assertEquals(list(expected, "required"), route.required(), label + " required");
            assertEquals(list(expected, "optional"), route.optional(), label + " optional");
            assertEquals(list(expected, "anyOf"), route.anyOf(), label + " anyOf");
            assertEquals(list(expected, "query"), route.query(), label + " query");
        }
    }

    @SuppressWarnings("unchecked")
    static List<String> list(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? List.of() : (List<String>) value;
    }
}
