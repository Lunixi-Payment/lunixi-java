# Changelog

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
