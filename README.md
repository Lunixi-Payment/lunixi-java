<p align="center"><a href="https://lunixi.com"><img src="https://raw.githubusercontent.com/Lunixi-Payment/lunixi-java/main/.github/assets/lunixi-logo.svg" alt="Lunixi" height="64"></a></p>

# Lunixi Java SDK

**Version 1.0.1 uses canonical minor-unit strings for money.** This is a breaking change from 0.x; update request and response handling together. See [migration](MIGRATION-1.0.md).

[![CI](https://github.com/Lunixi-Payment/lunixi-java/actions/workflows/ci.yml/badge.svg)](https://github.com/Lunixi-Payment/lunixi-java/actions/workflows/ci.yml) · [Türkçe](README.tr.md) · [Documentation](https://docs.lunixi.com/sdks/java) · [Changelog](CHANGELOG.md) · [Security](SECURITY.md)

**1.0.1** adds payment intents, capture/refund/void, split payment reads/refunds and durable checkout reconciliation to the existing fraud SDK. Requires **JDK 17+**; **zero external runtime dependencies**. MIT license.

| Surface | Included |
|---|---|
| Authentication/transport | Ed25519 PKCS#8/seed keys, canonical JSON signing, cached bearer tokens, bounded HTTPS transport, no redirects, typed errors |
| Payments | `client.payments().createIntent/get/list/capture/refund/voidPayment/getSplit/refundSplit`; payment methods (`listPaymentMethods`, `directMethodPayment`, `confirmPaymentAction`/`resendPaymentAction`), promissory notes, Para Transferi exceptions, `createSplitPayment`/`listSplitPayments`; `binInfo`, `installmentOptions`, `cardTaxonomy`, stored cards, `createMarketplaceIntent`, analytics; `client.reference()` address dictionary |
| Payment links | `client.paymentLinks()`: create, list, read, update, pause/resume/deactivate, send, QR code, attempts, payments, three-step image upload |
| Wallet | `client.wallet()`: programs, end users, wallets, deposits, corporate accounts, fees, transfers, withdrawals, operations, top-ups, payments, QR codes, bulk payouts |
| Bank accounts | `client.bankConnect()`: banks, connections, accounts, balances, transactions, sync; `transfers()` and `fx()` |
| Sanctions | `client.sanction()`: name, wallet, account, transaction, procurement-ban and adverse-media screening, monitoring, entity intelligence, deny list |
| Insurance | `client.insurance()`: products, reference codes, quotes, orders, premium collection and refunds, policy files, reconciliation, leads |
| Subscriptions | `client.subscriptions()`: subscriptions, `plans()`, `customers()`, `cards()`, `invoices()`, `portal()`, `products()`, `features()`, `addons()`, `usage()` |
| KYC | `client.kyc()`: KYC / KYB / evidence sessions, document upload, manual review, merchant policy, KYC webhook outbox, erasure requests |
| Identity | `client.identity()`: live-session analysis, linked accounts on a device, sessions, timelines, risky sources |
| Marketplace | `client.marketplace()`: split checkout and card charges across sub-dealers, dealer lifecycle, commissions, payouts, audit log |
| Durable checkout | `CommerceService`, native `LedgerStore` SPI, exact `Money`, immutable `OrderSnapshot`, authenticated `PaymentEvidence`, AES-GCM `SecretBox` |
| Fraud | Typed evaluation, decision records/cursor paging, event recording/batches |
| Webhooks | v2 HMAC, freshness, signed envelope binding; `verifyMerchant` for payment reconciliation |

This SDK is a server package; it does not collect PAN/CVV or implement a storefront/admin application.



Historical release record: **Java SDK 0.2.1 is published on [Maven Central](https://repo.maven.apache.org/maven2/com/lunixi/lunixi-java-sdk/0.2.1/lunixi-java-sdk-0.2.1.pom).** Its release files were verified byte-for-byte against the signed bundle on 2026-10-31.

## Installation

Use JDK 17+ and install [version 1.0.1 from Maven Central](https://central.sonatype.com/artifact/com.lunixi/lunixi-java-sdk/1.0.1):

```groovy
// build.gradle
repositories { mavenCentral() }
dependencies { implementation 'com.lunixi:lunixi-java-sdk:1.0.1' }
```

For Maven, use `<groupId>com.lunixi</groupId>`, `<artifactId>lunixi-java-sdk</artifactId>` and `<version>1.0.1</version>`. Source development can use the matching `v1.0.1` Git tag; normal application installation needs no local composite build.

## Configure the server

```java
import com.lunixi.sdk.LunixiClient;
import com.lunixi.sdk.LunixiConfig;

LunixiClient client = LunixiClient.create(LunixiConfig.builder()
        .baseUrl("https://api-gateway.lunixi.com")
        .keyId(System.getenv("LUNIXI_KEY_ID"))
        .privateKey(System.getenv("LUNIXI_PRIVATE_KEY"))
        .environment(LunixiConfig.Environment.TEST)
        .maxRetries(0)
        .build());
```

Keep the private key, webhook secret and separate 32-byte storage key in protected server configuration. Register only the public Ed25519 key in Lunixi. Use one reusable client per merchant/environment; retain its token cache. Changing the gateway origin or environment cannot reuse an existing commerce attempt.

## Payments and native framework integration

Read the [English payment/commerce guide](docs/commerce.en.md) for exact money, stable operation keys, strict webhook verification, persistent inbox/atomic native writes, lost-response recovery and financial holds. [Türkçe rehber](docs/commerce.tr.md).

For a native JDBC ledger, auto-configuration and a runnable MVC application use [Lunixi Spring Boot Starter](https://github.com/Lunixi-Payment/lunixi-spring-boot-starter) and its [checkout sample](https://github.com/Lunixi-Payment/lunixi-spring-boot-starter/tree/main/samples/spring-boot). [Spring guide](https://docs.lunixi.com/sdks/spring-boot). Do not use the SDK's test file ledger as production storage.

Merchant-site embedding is the default and uses the original `checkout.token()` with the official browser bundle. Optional `checkout.hostedUrl()` returns the exact validated backend `paymentPageUrl`, never a URL rebuilt from the embedded token. Enabled methods, meal cards, shopping credit, installments, insurance and multi-card options remain configured in the Lunixi merchant panel/form. Browser tokens are disclosed only after native session/customer/order ownership and CSRF checks. Provider browser POST returns are informational 303 hops into an owned GET; browser fields never mark paid.

`createIntent` takes the gateway body as a map. The optional `enabledInstallments` list narrows the installment counts the form offers for one payment; the form shows only the counts your panel settings, the card and the platform maximum also allow. Leave it out for no restriction; `List.of(1)` allows a single payment only. Send distinct integers 1–36 that include 1, in ascending order; the gateway rejects any other list (an empty list included) with 400. Each `basketItems` entry may carry the optional `productCategory`: the Lunixi product category code (for example `"MOBILE_PHONE"`), used by payment methods with legal installment limits (e.g. shopping loan) and other platform features. Leaving it out keeps today's behaviour; the gateway rejects an unknown code with 400. The codes are available as `ProductCategory` constants (`com.lunixi.sdk.payment.ProductCategory.MOBILE_PHONE`, all 40 in `ProductCategory.ALL`); the list only grows by appending, so the value stays a plain string and the SDK does not check it before sending.

```java
Map<String, Object> request = new LinkedHashMap<>();
request.put("amount", "10050");
request.put("currency", "TRY");
request.put("orderId", order.number());
request.put("callbackUrl", "https://merchant.example/payments/return");
request.put("enabledInstallments", List.of(1, 2, 3)); // optional: at most 3 installments
CheckoutSession checkout = client.payments().createIntent(request, persistedAttemptKey);
```

## Payment methods

`client.payments().listPaymentMethods()` lists the methods open for your account (signed): per method its `blockName`, `providerSelection` (`ProviderSelection`), `providerSelectionMode` (`ProviderSelectionMode`), `preauth` support, limits and providers with the `inputFields` to send. `listPaymentMethods(PaymentMethod.MOBILE_BILLING)` returns one method (`QR` is sent as `QR_CODE`). Always read `providerCode` from this list; never fix it in code.

`directMethodPayment(request, idempotencyKey)` starts a payment with any method from your server (`POST /api/v1/payments/direct/payment`, signed, persisted 16–128 ASCII-character key required). Build the body with `DirectMethodPaymentRequest`; a raw body map is checked with the same rules. The answer is the gateway envelope: `data.status`, and `data.action` when the buyer must act (`PaymentActionType`: `OTP`, `REDIRECT`, `DISPLAY`, `APP_APPROVAL`, `INPUT`). Complete an `OTP` or `INPUT` action with `confirmPaymentAction(paymentId, actionId, Map.of("otp", "123456"))` (only `otp`, `consentAccepted`, `fields`); `resendPaymentAction(paymentId, actionId)` sends a new code. Decide on the order from the webhook.

```java
Map<String, Object> methods = client.payments().listPaymentMethods(PaymentMethod.MOBILE_BILLING);
String providerCode = chooseProvider(methods); // from data.methods[].providers[].providerCode
Map<String, Object> response = client.payments().directMethodPayment(
        DirectMethodPaymentRequest.create("10000", "TRY", order.number())
                .withMethodFields(PaymentMethod.MOBILE_BILLING, providerCode, Map.of("gsmNumber", "+905321234567")),
        persistedAttemptKey);
```

The builder and the raw check refuse, before sending: `price` / `paidPrice`, unknown fields, a block of another method, card fields (`card`, `installment`, `installmentQuoteToken`, `force3D`) without `CARD`, a missing block, a malformed block (at most 32 fields; string values up to 1024 characters; `ADDRESS` objects of up to 16 fields; `GROUP` lists of up to 10 objects), an invalid `providerCode`, and a shopping-loan payment without 1 to 100 basket items. Rule violations throw `ConfigurationException`.

### Para Transferi (bank transfer)

The buyer sends the money from their own bank to your bank account; Lunixi does not hold it. Send the buyer's bank from the method row's `bankCode` options (`PaymentMethod.BANK_TRANSFER_OTHER_BANKS_CODE`, `"9999"`, is "Other banks"); the block takes no `providerCode`. The answer is HTTP 202 with a `DISPLAY` action holding the transfer instruction:

```java
Map<String, Object> response = client.payments().directMethodPayment(
        DirectMethodPaymentRequest.create("10000", "TRY", order.number())
                .withMethodFields(PaymentMethod.BANK_TRANSFER, null, Map.of("bankCode", selectedBankCode)),
        persistedAttemptKey);
PaymentBankTransferInstruction.fromResponse(response).ifPresent(instruction -> show(
        instruction.bankName(), instruction.holderName(), instruction.iban(), instruction.amountMinor(),
        instruction.paymentCode(), instruction.expiresAt()));
```

The buyer writes `paymentCode` in the transfer description. Late, short and excess transfers are listed with `listBankTransferExceptions(environment, status, limit)` (`TEST` / `LIVE`, `PaymentBankTransferExceptionStatus`, 1–200) and decided with `acceptBankTransferException(id)` or `markBankTransferExceptionRefunded(id)` after you refunded from your own bank. Webhooks: `PaymentBankTransferEvents` (`payment.bank_transfer.*`); the outcome is still `payment.captured` / `payment.failed`.

### Senetle Ödeme (promissory notes)

Give the plan as a schedule or a note count, never both:

```java
DirectMethodPaymentRequest.create("10000", "TRY", order.number())
        .withMethodFields(PaymentMethod.PROMISSORY_NOTE, providerCode, debtorFields)
        .withPromissoryNoteSchedule(List.of(
                Map.of("amount", "5000", "dueDate", "2027-01-15"),
                Map.of("amount", "5000", "dueDate", "2027-02-15")));
```

A schedule has 1–24 rows of positive minor-unit strings adding up to `amount`, with `YYYY-MM-DD` calendar days in order; `withPromissoryNoteCount(3)` lets your connection settings split the amount. `getPromissoryNotes(paymentId)` returns the notes (`PromissoryNoteValues.Status`), `cancelPromissoryNotes(paymentId, serialNumbers)` cancels them (`null` = every open note; `PromissoryNoteValues.CancelResult` / `CancelErrorCode` per note) and `downloadPromissoryNoteDocument(paymentId, PromissoryNoteValues.DocumentLocale.TR)` returns the printable PDF as `PromissoryNoteDocument.bytes()`. Webhooks: `PromissoryNoteEvents`. Address fields (`ADDRESS`) take codes from `client.reference().listProvinces()`, `listDistricts(provinceCode)` and `listNeighborhoods(districtCode)`; an empty list means the level is not published yet.

A custom `HttpTransport` downloads documents only when it implements `sendBinary(...)`; `JdkHttpTransport` does (up to 16 MiB).

### Split payments

`createSplitPayment(body, idempotencyKey)` starts a split payment and continues it after a step: send the same body with the same key. While it is running the answer (HTTP 400 with the status as `code` and `data.splitPaymentId`) is returned, not thrown. A non-card leg carries exactly one of `mobileBilling`, `wallet` or `promissoryNote` with its `providerCode`; meal card, open banking, shopping loan and bank transfer cannot be legs. `listSplitPayments(filters)` lists them (`status`, `currency`, `createdFrom`, `createdTo`, `orderId`, `ownerRef`, `limit`, `after`); `getSplit` / `refundSplit` read and refund one. Webhooks: `SplitPaymentEvents`; add `SplitPaymentEvents.LEG_EVENTS_SUBSCRIPTION` to an endpoint to also receive leg events.

### Cards, installments and marketplace intents

`binInfo(binOrPan)` returns the card, bank and installment eligibility of a 6- or 8-digit BIN (`POST /api/v1/bin/info`), `cardTaxonomy()` the brand, family and bank lists. `installmentOptions(InstallmentOptionsRequest.create(amount, currency))` returns the installment table; with `withBinOrPan(...)` and `withInstallment(n)` (n > 1) the answer carries the `quoteToken` for `DirectMethodPaymentRequest.withInstallment(n, quoteToken)`. `withEnabledInstallments(List.of(1, 2, 3))` follows the `enabledInstallments` rule of `createIntent` (distinct integers 1–36 that include 1); the list is checked before sending and sent in ascending order (`EnabledInstallments.require`).

Stored cards: `storeCard(StoreCardRequest.create(card, cardUserKey, callbackUrl), idempotencyKey)` and `deactivateStoredCard(storedCardToken, idempotencyKey)` are signed and need a persisted 16–128 ASCII-character key; `listStoredCards(cardUserKey)` and `getStoredCard(storedCardToken)` read them. The card fields are sent as you pass them and never appear in an SDK error message or `toString()`.

`createMarketplaceIntent(body[, idempotencyKey])` (`POST /api/v1/payments/marketplace/intents`, signed) takes the intent body plus `sellers[]` (`{subDealerId, amountMinor, scenario?}`) and `multiSellerPolicy`. The sellers' `amountMinor` strings must add up to `amount`; a different total is refused before sending.

`list(filters)` lists payments (`page`, `limit`, `status`, `customerId`, boolean `includeLegs`). `failoverRecoveryAnalytics(filters)` and `providerCredentialAnalytics(credentialId, filters)` read the analytics reports.

## Payment links

`client.paymentLinks()` manages payment links (`/api/v1/payments/links`). Every gateway call is signed; the signing key decides the merchant and the environment. Build the body with `CreatePaymentLinkRequest.fixed(...)`, `.open(...)` or `.itemized(...)` (lines: `PaymentLinkItem`, questions: `PaymentLinkCustomField`); a raw body map is checked against `PaymentLinkValues.PAYMENT_LINK_SPEC_FIELDS`. Amounts are canonical minor-unit strings.

```java
Map<String, Object> link = client.paymentLinks().create(
        CreatePaymentLinkRequest.fixed(PaymentLinkValues.Usage.SINGLE_USE, "150000", "TRY", "Invoice 2026-001")
                .withRecipient(Map.of("email", "buyer@example.com"))
                .withExpiresAt(Instant.now().plus(Duration.ofDays(7))),
        persistedOperationKey);
client.paymentLinks().send((String) link.get("id"), PaymentLinkValues.DeliveryChannel.EMAIL, persistedSendKey);
```

`create` and `send` require an `Idempotency-Key` (8–200 printable ASCII characters); `update(linkId, expectedRowVersion, changes[, key])`, `pause`, `resume`, `deactivate` and `action` take an optional one. `usage`, `amountMode` and `currency` cannot be updated. `list`, `listAttempts` and `listPayments` return a `PaymentLinkPage` (`items`, `hasMore`, `nextCursor`, `totalCount`); `iterate`, `iterateAttempts` and `iteratePayments` follow the cursors. `qr(linkId, format, size)` returns the image bytes (`svg` or `png`, 128–2048 px).

Link images take three steps: `createImageUpload(fileName, contentType, sizeBytes)` (PNG, JPEG or WebP), a PUT of the bytes to the returned `uploadUrl`, then `confirmImageUpload(assetId)`; `uploadImage(fileName, contentType, bytes)` runs all three and returns the published `PaymentLinkImage`. The PUT goes to the storage host, not the gateway: it carries only the returned `uploadHeaders`, never the access token or the request signature. It is sent through `HttpTransport.sendBytes(...)`; `JdkHttpTransport` implements it, and a custom transport must override it to upload images.

## Wallet

`client.wallet()` covers the wallet API (`/api/v1/wallet/*`) through sub-clients: `programs()`, `endUsers()`, `wallets()`, `deposits()`, `corporate()`, `fees()`, `transfers()`, `withdrawals()`, `operations()`, `topups()`, `payments()`, `qr()` and `bulkPayouts()`, plus `ping()`. Every call is signed, reads included. Each call is checked against `WalletRoutes` (one row per route: method, path, `Idempotency-Key` rule, body and query fields) before anything is sent: an unknown field or filter, a missing required field, an `amount` that is not a minor-unit digit string or a missing key throws `ConfigurationException`.

```java
Map<String, Object> started = client.wallet().transfers().initiateW2W(Map.of(
        "endUserId", endUserId, "sourceWalletAccountId", accountId,
        "amount", "12550", "currency", "TRY", "destinationPhone", "+905551112233"), persistedKey);
client.wallet().transfers().completeW2W((String) started.get("operationId"), Map.of(
        "endUserId", endUserId, "integrityHash", started.get("integrityHash"), "nonce", started.get("nonce")),
        persistedCompleteKey);
```

Money moves in two steps: `initiate…` reserves the limit, `complete…` commits it with the returned `integrityHash` and `nonce` (and the OTP when asked); the complete calls put `operationId` into the body themselves. Lists return a `WalletPage`; `iterate(filters)` follows the cursors. `qr().image(qrId, format, scale)` returns the SVG or PNG bytes. Bulk payouts follow maker-checker: the approving key must differ from the creating key.

## Bank accounts

`client.bankConnect()` reads your connected bank accounts (`/api/v1/bank-connect`): `listBanks`, `listConnections` / `getConnection`, `listAccounts` / `getAccount`, `listBalances`, `listTransactions` / `getTransaction` (each list with `iterate…`) and `requestSync(accountId)`. Every call is signed. Filters are checked before sending against `BankConnectValues.FILTERS`: the value sets, `limit` 1–100, at most 100 account ids, minor-unit amount strings with `minAmountMinor <= maxAmountMinor`, `bookedTo` only with `bookedFrom`, and a window of at most 92 days. Connecting a bank happens in the Lunixi dashboard only.

`transfers()` sends outgoing transfers (`listPurposes`, `create`, `list` / `iterate`, `get`) and `fx()` exchanges currency between two accounts of one connection (`listRates`, `createQuote`, `createOrder`, `listOrders` / `iterateOrders`, `getOrder`). Both need the `bank-connect:transfer` scope and a key with an IP allowlist. `create` and `createOrder` check the `Idempotency-Key` you pass (8–200 printable ASCII characters) or generate a UUID v4, and a network error or a 5xx is retried with the same key; keep your own key to retry safely across processes. Bodies are checked against `BankTransferRoutes`. Webhooks: `BankConnectEvents` (`bank_account.*`, read with `BankAccountTransactionReceived.fromEvent`) and `BankTransferEvents` (`bank_transfer.*`, read with `BankTransferWebhookData.fromEvent`); these are not the payment method's `payment.bank_transfer.*` events.

## Sanction screening

`client.sanction()` screens names, crypto wallets, bank accounts, transactions, public procurement bans and adverse media, runs ongoing monitoring and reads entity intelligence and your deny list (`/api/v1/sanction/*`). These calls carry the access token only (no request signature). Bodies and filters are checked against `SanctionRoutes` (fields, nested lists, at least one of `parties` / `accounts` for a transaction, the risk domain and mode sets) before sending.

Screening returns matches, not verdicts. Read a result with `SanctionClient.isClearScreening(result)`: it is clear only when nothing matched and, where coverage is reported, `coverageStatus` is `COMPLETE`. A screening that cannot run is a 503 (`ServiceUnavailableException`), and a 2xx answer whose `status` is not `success` throws; neither comes back as an empty result.

## Insurance

`client.insurance()` offers, sells and services insurance for basket items (`/api/v1/insurance`): `listProducts`, `searchProducts`, `listReferenceValues`, `createQuote` / `getQuote`, `createOrder` / `listOrders` / `getOrder`, `bindPayment`, `confirmPayment`, `issuePolicy`, `cancelOrder`, `refundPremium`, `getPolicyDocument`, `getPolicyReceipt`, `getPolicyCertificate`, `getReconciliationSummary` / `listReconciliationOrders` / `exportReconciliation`, and `createLead` / `listLeads` / `getLead` (each list with `iterate…`). Every call is signed.

`createOrder`, `confirmPayment`, `issuePolicy`, `cancelOrder`, `refundPremium` and `createLead` require an `Idempotency-Key` (8–200 printable ASCII characters); `createQuote` and `bindPayment` take one optionally. A quote's basket is either `paymentIntentId` or `context.items`, never both. The field rules of an insurance type's own object are not repeated in the SDK: the gateway answers 422 `insurance.validation.invalid` with every error at once. `InsuranceClient.consentFromQuote(option, acceptedAt)` builds the order's `consent`, and `InsuranceClient.premiumBasketItem(order, name, category1)` the premium item of a payment that collects the premium. Policy files return `InsuranceDocument` (PDF bytes) and the export returns `InsuranceReconciliationExport` (CSV bytes with `truncated`, `nextCursor` and `aborted`). Webhooks: `InsuranceEvents`, read with `InsuranceWebhookData.fromEvent` (orders) and `InsuranceLeadWebhookData.fromEvent` (leads).

## Subscriptions

`client.subscriptions()` covers `/api/v1/subscriptions/*` (and the portal-link routes under `/api/panel/v1/subscriptions/portal`): `create`, `get`, `list`, `cancel`, `pause`, `resume`, `changePlan` and `auditLogs`, plus the sub-clients `plans()`, `customers()`, `cards()`, `invoices()` (`voidInvoice` for the catalog's `invoices.void`), `portal()`, `products()`, `features()`, `addons()` and `usage()`. Calls carry the access token only (no request signature). `create` requires an `Idempotency-Key` (the argument or the request's `idempotencyKey`; it is also sent in the body); plan, customer, card, plan-change and charge-retry writes take one optionally.

```java
Map<String, Object> subscription = client.subscriptions().create(Map.of(
        "customerId", customerId, "planId", planId, "paymentCardId", cardId), persistedKey);
SubscriptionPage invoices = client.subscriptions().invoices().list(Map.of("subscriptionId", subscription.get("id")));
```

Every call is checked against `SubscriptionRoutes` before sending: unknown or missing body fields, unknown filters, a missing key and the value sets (`collectionMethod`, `billing.frequency`, `behavior`, `verificationMode`, `sdkMode`) throw `ConfigurationException`. Amounts are canonical minor-unit strings (`"9900"` = 99.00 TRY, `"0"` for a free plan), checked in bodies, filters and answers. Subscription, plan, customer, card, invoice and audit-log lists return a `SubscriptionPage` (send `nextPageToken()` back as `pageToken`); the catalog and portal-link lists return a `RoutePage`. `SubscriptionValues.isActive`, `isTerminal` and `portalUrl(link)` read a subscription and a hosted portal link; webhooks are `SubscriptionEvents`.

## KYC

`client.kyc()` creates and follows KYC, KYB and evidence sessions (`/api/v1/kyc/*`, access token only): `createSession`, `getSession`, `listSessions`, `setJurisdiction`, `setPrimaryDocument`, `uploadDocument`, `timeline`, `submit`, `manualReview`, `evidenceRequest`, and the admin calls `merchantPolicy`, `updateMerchantPolicy`, `rotateWebhookSecret`, `webhookOutbox`, `retryWebhookEvent` and `erasureRequests`. A `person` or `company` map is sent as `customer` with its `customerType`, and `metadata` as the JSON text `evidenceContextJson`. `listSessions` takes only `limit` and `cursor` (`pageSize` / `pageToken` are read as the same); any other filter is refused rather than dropped. The `kyc.*` webhook (`KycEvents`) is the authority for the result; `KycValues.isSessionTerminal` and `assuranceMeets` read a session. Error messages name fields, never values: a document image never appears in one.

## Identity

`client.identity()` reads device and account-linking signals (`/api/v1/identity/live-sessions`, access token only): `analyzeSync`, `analyze`, `accountLinks`, `getSession`, `relatedSessions`, `timeline`, `topSources` and `listSessions`. It returns risk flags, reason codes and the identity snapshot, not a verdict. `IdentityClient.isDeviceShared(accountLinks[, threshold])` and `linkReasons(accountLinks)` read an `accountLinks` answer.

## Marketplace

`client.marketplace()` splits a payment across sub-dealers and manages them. The split charges (`createCheckoutIntent`, `chargeMarketplaceCard2d`, `chargeMarketplaceCard3d`, and the raw `chargeMarketplaceCard`) go to `/api/v1/payments/marketplace/*`, signed, with an optional `Idempotency-Key`. Pass one leg per sub-dealer, `{subDealerId, amountMinor, scenario?}`, and the `multiSellerPolicy` (`ALL_OR_NOTHING` by default); the legs must add up to `amount`, and a card charge needs `amount`, `currency`, `orderId`, `card`, `buyer`, `billingAddress` and at least one basket item. Card data is sent as given and never put in an error message. A checkout answer without a token or form throws `LunixiApiException`.

```java
client.marketplace().createCheckoutIntent(Map.of("amount", "100000", "currency", "TRY", "orderId", orderId),
        List.of(Map.of("subDealerId", "d_1", "amountMinor", "60000"),
                Map.of("subDealerId", "d_2", "amountMinor", "40000")),
        MarketplaceValues.MultiSellerPolicy.ALL_OR_NOTHING, persistedKey);
```

The dealer, commission, payout and audit routes (`/api/v1/marketplace/*`, access token only) are `createDealer`, `bulkCreateDealers`, `listDealers`, `getDealer`, `updateDealerStatus`, `onboardingToken` (`MarketplaceValues.onboardingUrl(link)`), `dealerApplication`, `submitDealerApplication`, `setDealerTerms`, `signDealerContract`, `activateDealer`, `recordDealerKycResult`, `payoutLedger`, `transactions`, `commissionRules`, `upsertCommissionRule`, `previewCommissionRule`, `resolveCommission`, `createPayout`, `markPayoutPaid`, `bulkMarkPayoutsPaid`, `auditLog` and `verifyAuditLog`. Lists return a `RoutePage`.

## Fraud and errors

Use `client.fraud().evaluate(EvaluateRequest.create()...)` to screen your own events; read `client.fraud().decisions().latestForPayment(paymentId)` for an existing Lunixi payment. `events().recordBatch(events)` accepts up to 500 events; inspect per-item failures even on HTTP 200. Cursors are opaque. See [Fraud documentation](https://docs.lunixi.com/fraud/overview).

Branch on typed errors and `code()`, never localized message text. Envelope `status` is `success`, `failure` or `partial`; a 2xx failure envelope still throws. Reads and writes carrying stable idempotency keys may retry within configuration. Financial mutations require a persisted 16–128 ASCII-character key; never create another key after an uncertain response. Retain exact request bytes and reconcile settlement first.

## Build, verify and distribute

```sh
gradle --no-daemon -I tools/verification.init.gradle clean test spotbugsMain resolvedAuditDependencies centralBundle -PlunixiUnsigned
python3 tools/check-artifacts.py
python3 tools/audit-dependencies.py
python3 tools/check-secrets.py
```

The current source suite has **109 passing tests** covering crypto/canonical ordering, strict webhook proof, exact money, persistent reservation/replay, rollback, 23-hour create fencing, context isolation, nested public immutability, malformed alias boundaries and six fresh-JVM byte-identical retries. Tests use synthetic external transport and do not charge a merchant. JDK 17 compatibility is checked by actual execution and class-file version61 inspection.

SpotBugs uses method-specific reviewed filters for read-only defensive-copy getters and the intentionally shared token-store SPI, with no blanket exclusions. OSV audits resolved test dependencies; runtime dependency/POM checks enforce the zero-dependency claim. The full clean source scan retains default Gitleaks rules, excludes only exact reviewed synthetic fixtures and proves an added credential is rejected. Inspect [verification notes](docs/verification.md).

Before upgrading from 0.x, follow [the money-string migration](MIGRATION-1.0.md).
