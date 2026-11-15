# Changelog

## 1.5.0 — 2026-11-15

- Payment methods on `client.payments()`: `listPaymentMethods()` / `listPaymentMethods(String paymentMethod)` (`GET /payments/methods`, signed; the code is checked locally and `QR` is sent as `QR_CODE`), `directMethodPayment(DirectMethodPaymentRequest | Map, idempotencyKey)` (`POST /payments/direct/payment`, signed; a persisted 16–128 ASCII-character `Idempotency-Key` is required), `confirmPaymentAction(paymentId, actionId, Map)` (only `otp` as digits, `consentAccepted`, `fields`) and `resendPaymentAction(paymentId, actionId)` (`POST /payments/{id}/actions/{actionId}/confirm|resend`, signed). Read provider codes from `listPaymentMethods()`; never fix them in code.
- `DirectMethodPaymentRequest.create(amount, currency, orderId)` builds the body: `withMethodFields(method, providerCode, fields)`, `withCard(card)`, `withForce3D`, `withInstallment`, `withPreauth`, `withCallbackUrl`, `withLocale`, `withDescription`, `withCustomerId`, `withPaymentChannel`, `withPaymentGroup`, `withBuyer`, `withBillingAddress`, `withShippingAddress`, `withBasketItems`, `withMetadata`, `toBody()`. Builder and raw bodies are checked before sending: no `price` / `paidPrice`, known fields only, exactly the chosen method's block (block name = camelCase of the method code), card fields only with `CARD`, the block structure (at most 32 fields, string values up to 1024 characters, `ADDRESS` objects, `GROUP` lists of at most 10 items, reserved names refused), the `providerCode` pattern, a shopping-loan basket of 1 to 100 items and boolean `preauth` / `force3D`.
- Para Transferi (`BANK_TRANSFER`): `withMethodFields(PaymentMethod.BANK_TRANSFER, null, Map.of("bankCode", …))` sends the `bankTransfer` block without `providerCode` (refused in builder and raw bodies); `PaymentMethod.BANK_TRANSFER_OTHER_BANKS_CODE` is `"9999"`. `PaymentBankTransferInstruction.fromResponse(response)` reads the `DISPLAY` action's `bankTransfer` instruction. Exceptions: `listBankTransferExceptions(environment, status, limit)`, `acceptBankTransferException(id)` and `markBankTransferExceptionRefunded(id)` (signed; UUID ids, `limit` 1–200). Constants `PaymentBankTransferExceptionKind`, `PaymentBankTransferExceptionStatus`, `PaymentBankTransferEvents` (`payment.bank_transfer.*`). Bank transfer cannot be a split payment leg.
- Senetle Ödeme (`PROMISSORY_NOTE`): `withPromissoryNoteSchedule(rows)` / `withPromissoryNoteCount(n)` (schedule or note count, never both; 1–24 rows of positive minor-unit strings adding up to `amount`, `YYYY-MM-DD` calendar days in non-descending order). `getPromissoryNotes(paymentId)`, `cancelPromissoryNotes(paymentId[, serialNumbers[, idempotencyKey]])` and `downloadPromissoryNoteDocument(paymentId[, locale])`, which returns the PDF bytes as `PromissoryNoteDocument`. Constants `PromissoryNoteValues` (status, document type, cancel result and error code, document locale) and `PromissoryNoteEvents`.
- Address dictionary: `client.reference()` (`ReferenceClient`) with `listProvinces()`, `listDistricts(provinceCode)` and `listNeighborhoods(districtCode)` (`/api/v1/reference/tr-address/*`, codes checked locally).
- Split payments: `createSplitPayment(Map, idempotencyKey)` (`POST /payments/split`, signed; a running payment's HTTP 400 answer with `data.splitPaymentId` is returned, not thrown) and `listSplitPayments([filters])` (`GET /payments/split`; `status` / `currency` lists are sent as repeated parameters). A non-card leg carries only `mobileBilling`, `wallet` or `promissoryNote` (`SplitPaymentValues.SPLIT_LEG_BLOCK_FIELDS`); meal card, open banking, shopping loan and bank transfer legs are refused before sending. `getSplitPayment` / `refundSplitPayment` are aliases of `getSplit` / `refundSplit`. Constants `SplitPaymentValues` and `SplitPaymentEvents` (with `LEG_EVENTS_SUBSCRIPTION`).
- Constants `PaymentMethod` (codes, `ALL`, `METHOD_BLOCK_FIELDS`, `DIRECT_METHOD_PAYMENT_FIELDS`, `CARD_ONLY_FIELDS`, `PAYMENT_ACTION_CONFIRM_FIELDS`, `SHOPPING_LOAN_MAX_BASKET_ITEMS`, `normalize()`, `blockName()`), `PaymentActionType`, `ProviderSelection` and `ProviderSelectionMode`.
- The new calls throw `ConfigurationException` for a rule violation, before anything is sent; existing methods keep their exceptions.
- Transport: `HttpTransport.sendBinary(...)` (default method) reads a binary answer; `JdkHttpTransport` implements it with a 16 MiB limit (`MAX_BINARY_BYTES`), and `HttpResult` gained a byte constructor and `bytes()`. A custom transport that does not override it refuses document downloads with `ConfigurationException`; nothing else changes for it.
- Cards on `client.payments()`: `binInfo(binOrPan)` (`POST /api/v1/bin/info`; 6–19 digits, the value is never shown in an error message), `installmentOptions(InstallmentOptionsRequest | Map)` (`POST /api/v1/bin/installments`) and `cardTaxonomy()` (`GET /api/v1/bin/card-taxonomy`). `InstallmentOptionsRequest.create(amount, currency)` with `withBinOrPan`, `withInstallment`, `withCardBrand` and `withEnabledInstallments`; `enabledInstallments` follows the `createIntent` rule (distinct integers 1–36 that include 1), is checked before sending and is sent in ascending order (`EnabledInstallments.require`).
- Stored cards: `storeCard(StoreCardRequest | Map, idempotencyKey)` (`POST /payments/cards`, signed) and `deactivateStoredCard(storedCardToken, idempotencyKey)` (`DELETE /payments/cards/{token}`, signed) require a persisted 16–128 ASCII-character `Idempotency-Key`; `listStoredCards(cardUserKey)` and `getStoredCard(storedCardToken)` read them. `StoreCardRequest.create(card, cardUserKey, callbackUrl)` with `withCurrency`; card fields never appear in `toString()` or an error message.
- `createMarketplaceIntent(Map[, idempotencyKey])` (`POST /payments/marketplace/intents`, signed): no `price` / `paidPrice`, and the `sellers[].amountMinor` strings must add up to `amount`, checked before sending.
- `list([filters])` (`GET /payments`; `page`, `limit`, `status`, `customerId`, boolean `includeLegs`), `failoverRecoveryAnalytics([filters])` and `providerCredentialAnalytics(credentialId[, filters])` (`GET /payments/analytics/*`).
- Payment links: `client.paymentLinks()` (`PaymentLinksClient`, `/api/v1/payments/links`, every gateway call signed) with `create`, `list` / `iterate`, `get`, `update`, `pause`, `resume`, `deactivate`, `action`, `send`, `qr`, `listAttempts` / `iterateAttempts`, `listPayments` / `iteratePayments`, `createImageUpload`, `confirmImageUpload` and `uploadImage`. `create` and `send` require an `Idempotency-Key` (8–200 printable ASCII characters); update and the actions take an optional one. Builders `CreatePaymentLinkRequest` (`fixed`, `open`, `itemized`), `PaymentLinkItem`, `PaymentLinkCustomField`; constants `PaymentLinkValues` (value sets, `PAYMENT_LINK_SPEC_FIELDS`, `PAYMENT_LINK_IMMUTABLE_FIELDS`); results `PaymentLinkPage`, `PaymentLinkQrCode`, `PaymentLinkImageUpload`, `PaymentLinkImage`. Raw bodies with unknown fields, updates of `usage` / `amountMode` / `currency` and unknown filters are refused before sending.
- The image PUT of `uploadImage` goes to the presigned storage URL with only the returned `uploadHeaders`: no access token, no request signature. It is sent through the new default method `HttpTransport.sendBytes(...)`, which `JdkHttpTransport` implements; a custom transport that does not override it refuses image uploads with `ConfigurationException`.
- Wallet: `client.wallet()` (`WalletClient`, `/api/v1/wallet/*`, every call signed) with `ping()` and the sub-clients `programs()` (`listMine`, `setCollectionAnchor`), `endUsers()` (`create`, `list` / `iterate`, `get`, `getByExternalId`, `freeze`, `getBalances`, `getKycLevel`, `updateKycLevel`, `getLimits`, `createWallet`), `wallets()` (`createAccount`, `lookup`), `deposits()` (`listInstructions`, `createInstruction`, `simulateInboundCredit`), `corporate()` (`create`, `list` / `iterate`, `importEmployees`, `offboardEmployees`), `fees()` (`quote`), `transfers()` (`quoteW2W`, `initiateW2W`, `completeW2W`, `quoteW2Iban`, `initiateW2Iban`, `completeW2Iban`), `withdrawals()` (`quote`, `initiate`, `complete`, `get`), `operations()` (`list` / `iterate`, `get`, `getReceipt`), `topups()` (`startCard`, `get`, `refund`, `agent`), `payments()` (`request`, `approve`, `collect`, `get`, `getCommission`), `qr()` (`create`, `list` / `iterate`, `get`, `image`, `revoke`, `parse`) and `bulkPayouts()` (`create`, `list`, `get`, `listItems`, `submit`, `approve`, `reject`, `process`, `retryFailed`). Every call is checked against the route table `WalletRoutes` (method, path, `Idempotency-Key` rule, body, nested row and query fields) before sending: unknown or missing fields, an amount that is not a minor-unit digit string and a missing key throw `ConfigurationException`. The complete calls put `operationId` into the body. Results: `WalletPage`, `WalletQrImage`; constants `WalletValues`.
- Bank accounts: `client.bankConnect()` (`BankConnectClient`, `/api/v1/bank-connect`, every call signed) with `listBanks`, `listConnections`, `getConnection`, `listAccounts`, `getAccount`, `requestSync`, `listBalances`, `listTransactions`, `getTransaction` and an `iterate…` for each list. Filters are checked before sending (`BankConnectValues.FILTERS`, value sets, `limit` 1–100, at most 100 account ids, minor-unit amount strings, `minAmountMinor <= maxAmountMinor`, `bookedTo` needs `bookedFrom`, at most 92 days).
- Bank transfers and currency exchange: `bankConnect().transfers()` (`BankTransferClient`: `listPurposes`, `create`, `list` / `iterate`, `get`) and `bankConnect().fx()` (`BankFxClient`: `listRates`, `createQuote`, `createOrder`, `listOrders` / `iterateOrders`, `getOrder`). `create` / `createOrder` check a given `Idempotency-Key` (8–200 printable ASCII characters) or generate a UUID v4, and retry a network error or a 5xx with the same key. Bodies and filters are checked against the route table `BankTransferRoutes` (unknown or missing fields, amounts that are not positive minor-unit strings, a quote with two or no amounts or the same account twice are refused). Results: `BankConnectPage`. Webhooks: `BankConnectEvents` (`bank_account.*`) with `BankAccountTransactionReceived.fromEvent`, and `BankTransferEvents` (`bank_transfer.*`) with `BankTransferWebhookData.fromEvent`.
- Sanction screening: `client.sanction()` (`SanctionClient`, `/api/v1/sanction/*`, access token only) with `screenName`, `screenWallet`, `screenAccount`, `screenTransaction`, `screenPublicProcurementBan`, `screenAdverseMedia`, `evaluateBehavioralAlert`, `listScreeningHistory`, `registerMonitoring` (a `profile` map is sent as `profileJson`), `listMonitoringAlerts`, `listMonitoredEntities`, `listMonitoringMatches`, `getDatasetFreshness`, `searchEntities`, `getEntityProfile`, `getEntityGraph`, `getIndirectExposure`, `getOwnershipAggregation`, `listBlacklistEntries` and an `iterate…` for each list. Bodies and filters are checked against `SanctionRoutes` (`ROUTES`, `NESTED_FIELDS`, `VALUE_SETS`). A 2xx answer whose `status` is not `success` throws `LunixiApiException`. `SanctionClient.isClearScreening(result)` applies the clear-result rule (no match or hit, no blocking flag or decision, and `coverageStatus` `COMPLETE` where reported). Results: `SanctionPage`; constants `SanctionValues`, `SanctionEvents`.
- Insurance: `client.insurance()` (`InsuranceClient`, `/api/v1/insurance`, every call signed) with `listProducts`, `searchProducts`, `listReferenceValues`, `createQuote`, `getQuote`, `createOrder`, `listOrders`, `getOrder`, `bindPayment`, `confirmPayment`, `issuePolicy`, `cancelOrder`, `refundPremium`, `getPolicyDocument`, `getPolicyReceipt`, `getPolicyCertificate`, `getReconciliationSummary`, `listReconciliationOrders`, `exportReconciliation`, `createLead`, `listLeads`, `getLead` and an `iterate…` for each list; helpers `InsuranceClient.consentFromQuote` and `InsuranceClient.premiumBasketItem`. `createOrder`, `confirmPayment`, `issuePolicy`, `cancelOrder`, `refundPremium` and `createLead` require an `Idempotency-Key` (`^[\x21-\x7E]{8,200}$`), `createQuote` and `bindPayment` take one optionally; a quote with both `paymentIntentId` and `context.items` is refused before sending. Policy files return `InsuranceDocument` (PDF bytes) and the export `InsuranceReconciliationExport` (CSV bytes, `truncated`, `nextCursor`, `aborted`). Constants `InsuranceValues` (value sets, field lists, `FILTERS`) and `InsuranceEvents` (`ORDER_EVENTS`, `LEAD_EVENTS`); webhooks `InsuranceWebhookData.fromEvent` and `InsuranceLeadWebhookData.fromEvent`.
- `com.lunixi.sdk.http.Route`: one row of a route table (method, path template, key rule, body and query fields), shared by `WalletRoutes`, `SanctionRoutes`, `BankTransferRoutes` and the four tables below. It also carries `keyRule()` (`Route.KeyRule`: `NONE`, `OPTIONAL`, `REQUIRED`; `idempotencyKey()` stays `true` for `REQUIRED`), `stepUp()`, `free()`, `optionalBody()` and `anyQuery()`.
- Subscriptions: `client.subscriptions()` (`SubscriptionsClient`, `/api/v1/subscriptions/*` and `/api/panel/v1/subscriptions/portal`, access token only) with `create`, `get`, `list`, `cancel`, `pause`, `resume`, `changePlan`, `auditLogs` and the sub-clients `plans()` (`create`, `get`, `list`, `update`, `archive`, `translations`, `upsertTranslation`), `customers()` (`upsert`, `get`, `getByExternalId`, `list`, `creditBalance`, `adjustCredit`, `archive`), `cards()` (`add`, `get`, `list`, `setDefault`, `deactivate`, `verify`, `update`), `invoices()` (`list`, `get`, `voidInvoice`, `retryCharge`), `portal()` (`createSdkSession`, `createPortalLink`, `listLinks`, `getLink`, `updateLink`, `revokeLink`, `linkActivity`, `sendInvite`, `getSettings`, `updateSettings`), `products()`, `features()`, `addons()` and `usage()` (`record`, `checkEntitlement`). `create` requires an `Idempotency-Key`, also sent in the body; plan, customer, card, plan-change and charge-retry writes take one optionally. Every call is checked against `SubscriptionRoutes` (`ROUTES`, `BILLING_RULE_FIELDS`); value sets, the billing rule, supported currencies and minor-unit money members (bodies, filters and answers; `"0"` is a valid free-plan price) are checked before sending. Results: `SubscriptionPage` (`nextPageToken`, `pageSize`) and `RoutePage`; constants `SubscriptionValues` (with `isActive`, `isTerminal`, `portalUrl`) and `SubscriptionEvents`.
- KYC: `client.kyc()` (`KycClient`, `/api/v1/kyc/*`, access token only) with `createSession`, `getSession`, `listSessions`, `setJurisdiction`, `setPrimaryDocument`, `uploadDocument`, `timeline`, `submit`, `manualReview`, `evidenceRequest`, `merchantPolicy`, `updateMerchantPolicy`, `rotateWebhookSecret`, `webhookOutbox`, `retryWebhookEvent` and `erasureRequests`. `person` / `company` are sent as `customer` with `customerType`, `metadata` as `evidenceContextJson`; `ttlMinutes` 1–1440; `listSessions` refuses any filter but `limit` / `cursor` (`pageSize` / `pageToken` read as the same). Route table `KycRoutes`; constants `KycValues` (with `isSessionTerminal`, `assuranceRank`, `assuranceMeets`) and `KycEvents`.
- Identity: `client.identity()` (`IdentityClient`, `/api/v1/identity/live-sessions`, access token only) with `analyzeSync`, `analyze`, `accountLinks`, `getSession`, `relatedSessions`, `timeline`, `topSources` and `listSessions`; helpers `IdentityClient.isDeviceShared` and `IdentityClient.linkReasons`. Route table `IdentityRoutes` (`ROUTES`, `ANALYZE_FIELDS`).
- Marketplace: `client.marketplace()` (`MarketplaceClient`) with the signed split charges `createCheckoutIntent`, `chargeMarketplaceCard`, `chargeMarketplaceCard2d` and `chargeMarketplaceCard3d` (`/api/v1/payments/marketplace/*`, optional `Idempotency-Key`; seller legs `{subDealerId, amountMinor, scenario?}` must add up to `amount`, a card charge needs its commercial context, card data never appears in an error message, a checkout answer without a token or form throws `LunixiApiException`) and the dealer, commission, payout and audit calls under `/api/v1/marketplace/*` (access token only). Route table `MarketplaceRoutes` (`ROUTES`, `SELLER_FIELDS`, `CHARGE_REQUIRED_FIELDS`); constants `MarketplaceValues` (with `onboardingUrl`).
- The new calls throw `ConfigurationException` for a rule violation, before anything is sent; a malformed money member in a subscription or marketplace answer throws `LunixiApiException`.

## 1.4.0 — 2026-11-12

- Document the optional `basketItems[].productCategory` field of the `createIntent()` body: the Lunixi product category code, used by payment methods with legal installment limits (e.g. shopping loan) and other platform features. The body map is sent unchanged; leaving it out keeps today's behaviour, and the gateway answers an unknown code with 400.
- `com.lunixi.sdk.payment.ProductCategory`: the 40 product category codes as `String` constants (e.g. `ProductCategory.MOBILE_PHONE`) and the unmodifiable `ProductCategory.ALL` in dictionary order. The list only grows by appending; values are not checked against it before sending.

## 1.3.0 — 2026-11-04

- Document the optional `enabledInstallments` field of the `createIntent()` body: the installment counts the hosted form may offer for one payment (`List.of(1)` = single payment only). It only narrows the panel settings, the card and the platform maximum; leaving it out means no restriction. The body map is sent unchanged; send distinct integers 1–36 that include 1, in ascending order (the gateway answers any other list with 400).

## 1.2.0 — 2026-11-03

- Physical POS end-of-day webhooks: `PhysicalPosEvents.SETTLEMENT_COMPLETED`, `SETTLEMENT_FAILED`, `SETTLEMENT_OVERDUE`, `SETTLEMENT_SKIPPED` (also `SETTLEMENT`, and in `ALL`); `PhysicalPosWebhookEvent` reads them with `CATEGORY_SETTLEMENT` / `isSettlementEvent()`; they carry no `historySeq`.
- Physical POS payment webhooks: `PhysicalPosWebhookEvent.cashier()` returns the optional `{cashierId, cashierCode}` of a payment started from a device cashier session (empty otherwise; the key is absent then).
- Physical POS end-of-day on `PhysicalPosClient`: `startSettlement(String deviceId, String idempotencyKey)` / `startSettlement(String deviceId)` (`POST /devices/{deviceId}/settlement`, `physical_pos:payments`; empty JSON body, `Idempotency-Key` sent — generated when `null`; `PhysicalPosWriteResult` whose `entity()` is the `run`, 202 new / 200 replay), `listSettlementRuns()` / `iterateSettlementRuns()` (`GET /settlement-runs`; filters `deviceId`, `source`, `runStatus`, `createdFrom`, `createdTo`, `includeTotal`) and `getSettlementRun(String runId)`.
- Physical POS end-of-day policy on `PhysicalPosManagementClient`: `get…`, `set…` and `clear…SettlementPolicy()` for the account, a store and a device (`GET` / `PUT` / `DELETE …/settlement-policy`; `clear…` takes an optional `Integer expectedRowVersion`, sent as a query parameter, with overloads without it) and `getEffectiveSettlementPolicy(String deviceId)`.
- Physical POS terminal payment-provider rows on `PhysicalPosManagementClient`: `listTerminalProviders()` / `iterateTerminalProviders()`, `addTerminalProvider()` (`Idempotency-Key`; 201 / 200) and `updateTerminalProvider()` (`PATCH /terminal-providers/{terminalProviderId}`).
- `createPayment()` accepts `interaction` (`INSTRUMENT_READ` | `CODE_DISPLAY`) and `terminalProviderId` for provider-collected cards such as `instrument` `MEAL_CARD`.

## 1.1.0 — 2026-11-03

- Physical POS device identity on `PhysicalPosManagementClient`: `listDeviceKeys()` / `iterateDeviceKeys()` (`GET /devices/{deviceId}/keys`, `physical_pos:view`), `revokeDeviceKey()`, `revertDeviceEnrollment()` and `revokeActivationCode()` (`physical_pos:identity`; no `Idempotency-Key`). Key ids are `pdk_…`, not UUIDs. Activation codes are not issued with an API key (panel only).

## 1.0.1 — 2026-11-01

- Keep response envelopes defensively immutable at nested levels and make Physical POS getters explicitly read-only; retain strict money-string admission.
- Correct Luhn parity handling and document the narrowly scoped management-facade static-analysis exception; update 1.0.1 installation guidance.

## 1.0.0 — 2026-11-01

- Physical POS payment/item/filter/report money uses canonical signed64 strings; raw numeric/null amounts are rejected before HTTP, malformed monetary 2xx payment replies retain UNKNOWN semantics, and signed webhooks are checked after signature verification.

- Reject explicit null capture/refund amounts; full remaining operations use omission or the dedicated API described in MIGRATION-1.0.md.

- Breaking: public money request/response fields require canonical minor-unit strings; JSON numbers are rejected.
- Exact signed 64-bit fiat arithmetic and currency-aware decimal text conversion preserve values above JavaScript’s safe-integer limit.
- See [MIGRATION-1.0.md](MIGRATION-1.0.md) before upgrading.


`com.lunixi:lunixi-java-sdk` sürüm geçmişi. · Release history of
`com.lunixi:lunixi-java-sdk`.

### Physical POS additions

- Add physical POS (`PHYSICAL_POS`) clients: `LunixiClient.physicalPos()` (`PhysicalPosClient`) for starting, reversing (void, amount or item refund, pre-authorisation close / cancel), cancelling, reading and listing device payments, transaction trees, ambiguous payments, sales / tax summaries and end-of-day settlement lines; `LunixiClient.physicalPosManagement()` (`PhysicalPosManagementClient`) for overview, catalog, stores, registers, logical terminals (bank rows, device link) and devices (assignment, lifecycle, retirement). Every call is signed.
- `createPayment` and `createReversal` require an `Idempotency-Key` (no key-less overloads; nothing is sent without one). Create it with `PhysicalPosClient.newIdempotencyKey()`, store it with the order, then call; retry an unknown outcome with the same key. `cancelPayment` and keyed management writes generate one when you pass `null`. A given key is checked as given: empty, blank or space-padded keys are refused, never trimmed or replaced.
- Writes return `PhysicalPosWriteResult` with `statusCode` (202 accepted / 200 replay; management creates 201 / 200), `replayed` and the key. A payment write succeeds only with a readable `payment` in the 2xx answer.
- Payment writes run their own attempt loop (the transport's automatic retry is off for them; same key and body on every attempt). `PhysicalPosOutcomeUnknownException` (key + last error as cause) when any attempt ended without a usable answer — no answer or body-read failure, a non-JSON 2xx or a 2xx without the payment, a 5xx not marked "nothing started" — whatever the last error was (a later 429 or 409 included). A 5xx with `physical_pos.presence.unavailable`, `signing.unavailable`, `service.busy`, `service.clock_skew` or `service.not_configured` is a definite failure and is retried with the same key.
- Typed errors: `PhysicalPosCardDataRejectedException` (card-data keys, and card numbers in `merchantReference`, item names / tax group codes and the `merchantReference` / `q` filters — 13–19 digits, Luhn, also separated — refused before sending; the gateway's `physical_pos.payment.card_data_rejected`; field path only), `PhysicalPosRateLimitException` (`Retry-After` or `details.retryAfterSeconds`; both carry the write's key).
- Cursor pages (`PhysicalPosPage`, `iterate*`), `PhysicalPosEvents` (`physical_pos.*`) and `PhysicalPosClient.parseWebhookEvent(rawBody, headers, secret[, tolerance])`, which verifies the signature itself with `WebhookVerifier` (or takes the verifier's own `WebhookEvent`); the payload is `data`, or a `payloadJson` object / JSON string; the event type comes from the signed body only.
- Filters: `dateFrom` / `dateTo` accept a `LocalDate` (sent as its own `YYYY-MM-DD`, not converted to Europe/Istanbul), `createdFrom` / `createdTo` an `Instant` (sent as UTC with milliseconds); empty values are left out. Path ids `.` and `..` are refused.
- Core: `RequestOptions.Builder.retry(false)` sends a call once without the transport's automatic retry (default unchanged); `ApiClient.maxRetries()`.
- Sales made directly on the device: `PhysicalPosEvents.PAYMENT_DEVICE_SALE` (`physical_pos.payment.device_sale`, in `ALL`). `PhysicalPosWebhookEvent.isDeviceSale()` and `original()` (a reversal's original payment `{paymentId, origin}`, empty on a root; `origin` `API` or `DEVICE_DIRECT`).
- `PhysicalPosVoidAfterSettlementException` (409 `physical_pos.payment.void_after_settlement`, `allowedOperation()`, carries the reversal's key): a payment in a device end-of-day can no longer be voided; refund it instead.
- Javadoc: `getDevice()` describes `presence` (connection state, readiness for payment, `notReadyReason`); `listSettlements()` lines carry `settlementRef`, `acquirerRefRaw`, `acquirerName`, `deviceName` (`commandId` null for an end-of-day started on the device); `listAcquirers()` rows are `{acquirerCode, displayName}` (no `providerCode`).

## 0.2.1

- Preserve optional backend paymentPageUrl and return it exactly after strict fixed-HTTPS host/path validation; never rebuild hosted checkout from the embedded token. Keep the two-argument CheckoutSession constructor for legacy embedded responses.
- Encrypt hosted URL with the retained browser token; legacy records without a URL keep their original request/key/history and cannot silently redirect or create another attempt.
- Document merchant-site embedding as the default with optional same-intent hosted checkout.

## 0.2.0

- Added signed checkout intent, authenticated payment/split reads, capture/refund/void and split-refund clients. Full response envelopes retain settlement details beside data.
- Added exact minor-unit conversion, immutable order snapshots, split-parent/full-capture evidence, encrypted browser token storage and durable `CommerceService`/native `LedgerStore` integration.
- Added strict current merchant webhook verification and duplicate JSON/header, depth, HTTPS origin and redirect protections. Existing fraud APIs remain available.
- Added the Spring Boot Starter integration path. Source tags and Maven artifacts built locally are distinct from Maven Central publication; see README.
- Security hardening: HTTP/userinfo/path/query gateway URLs now fail at configuration. Unknown `FAILED` stays in review; uncertain create retries retain their original key and stop at 23 hours.

## 0.1.0

İlk sürüm. · First release.

### Eklenenler · Added

- **Yapılandırma · Configuration** — `LunixiConfig` (değişmez, kurulurken
  doğrulanan; taban adres, anahtar kimliği, Ed25519 özel anahtarı, ortam, zaman
  aşımı, yeniden deneme sayısı, taşıyıcı ve jeton deposu enjekte edilebilir).
- **Kimlik doğrulama · Authentication** — `auth.CanonicalRequest` (imzalanan
  kanonik dizge ve `Digest` başlığı), `auth.Ed25519Signer` (PKCS#8 PEM, çıplak
  tohum ve base64 anahtar malzemesi), `auth.Ed25519KeyPair` (anahtar çifti
  üretimi), `auth.TokenManager` + `auth.TokenStore` /
  `auth.InMemoryTokenStore` (imzalı jeton alışverişi ve önbellek).
- **HTTP** — `http.HttpTransport` arayüzü, `http.JdkHttpTransport` (JDK istemcisi,
  yönlendirme izlenmez), `http.HttpResult`.
- **Yanıt zarfı · Response envelope** — `http.Envelope`: `status` / `code` /
  `requestId` / `data` okuması, `data`'nın kardeşi olan `pageInfo` üzerinden
  imleçli sayfalama, `2xx` içinde gelen `failure` durumunun yakalanması.
- **JSON** — `json.Json`: imza sözleşmesine uygun kompakt yazıcı (JavaScript
  dizi-indeksi anahtar sırası, satır ayırıcıların kaçışlanmaması dâhil) ve
  okuyucu.
- **Fraud** — `fraud.FraudClient`: `evaluate`, `decisions().list/get/
  latestForPayment/iterate`, `events().record/recordBatch` (parti sınırı 500,
  kısmi başarı raporu). `fraud.FraudTaxonomy` (kanal, yüzey, aşama, işlem türü,
  kimlik doğrulama modu, ürün bağlamı), `fraud.EvaluateRequest` (yerel alan
  doğrulaması), `fraud.FraudDecision` / `FraudDecisionPage` /
  `FraudEventResult` / `FraudEventBatchResult`, `fraud.FraudEvents` (yedi olay
  tipi).
- **Webhook** — `webhook.WebhookVerifier` (v2 HMAC-SHA256, ham gövde üzerinden,
  sır döndürme penceresinde çoklu `v2` öğesi, sabit-zamanlı karşılaştırma,
  fail-closed), `webhook.WebhookEvent`.
- **Hatalar · Errors** — `exception.LunixiException` ve altındaki tipli aile:
  `ConfigurationException`, `SignatureException`, `LunixiApiException`,
  `AuthenticationException`, `ValidationException`, `RateLimitException`,
  `ServiceUnavailableException`, `WebhookVerificationException`.

### Notlar · Notes

- Çalışma zamanı bağımlılığı yoktur. · No runtime dependencies.
- En düşük Java sürümü ve paket koordinatı `gradle.properties` dosyasında tek
  yerde tutulur. · The minimum Java release and the package coordinate are kept
  in one place, in `gradle.properties`.
- 80 birim testi; ağ çağrısı yapılmaz. · 80 unit tests; no test reaches the
  network.
