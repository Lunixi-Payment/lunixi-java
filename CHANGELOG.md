# Changelog

`com.lunixi:lunixi-java-sdk` sürüm geçmişi. · Release history of
`com.lunixi:lunixi-java-sdk`.

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
