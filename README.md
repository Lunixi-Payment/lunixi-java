<p align="center">
  <a href="https://lunixi.com">
    <picture>
      <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/Lunixi-Payment/lunixi-java/main/.github/assets/lunixi-logo-dark.svg">
      <img src="https://raw.githubusercontent.com/Lunixi-Payment/lunixi-java/main/.github/assets/lunixi-logo.svg" alt="Lunixi" height="64">
    </picture>
  </a>
</p>

# Lunixi Java SDK

[![CI](https://github.com/Lunixi-Payment/lunixi-java/actions/workflows/ci.yml/badge.svg)](https://github.com/Lunixi-Payment/lunixi-java/actions/workflows/ci.yml) [![Maven Central](https://img.shields.io/maven-central/v/com.lunixi/lunixi-java-sdk)](https://central.sonatype.com/artifact/com.lunixi/lunixi-java-sdk) [![Java](https://img.shields.io/badge/Java-17%2B-007396?logo=openjdk&logoColor=white)](https://adoptium.net) [![Dependencies](https://img.shields.io/badge/runtime%20dependencies-none-brightgreen)](build.gradle) [![License](https://img.shields.io/badge/license-MIT-blue)](LICENSE) [![Docs](https://img.shields.io/badge/docs-docs.lunixi.com-FF6B35)](https://docs.lunixi.com/sdks/java)

**[Documentation](https://docs.lunixi.com/sdks/java) · [Changelog](https://github.com/Lunixi-Payment/lunixi-java/blob/main/CHANGELOG.md) · [Issues](https://github.com/Lunixi-Payment/lunixi-java/issues) · [Security](https://github.com/Lunixi-Payment/lunixi-java/blob/main/SECURITY.md)**

Lunixi platformunun Java istemcisi. · Java client for the Lunixi platform.

| | |
|---|---|
| **groupId** | `com.lunixi` |
| **artifactId** | `lunixi-java-sdk` |
| **version** | `0.1.0` |
| **Java** | 17+ |
| **Runtime dependencies** | yok / none |
| **License** | MIT |

> Bu tablodaki değerler `gradle.properties` dosyasından okunur; koordinat tek
> yerde tutulur. · The values in this table are read from `gradle.properties`;
> the coordinate is kept in one place.

---

# Türkçe

## Kapsam

Bu sürüm, platformun **çekirdek istek katmanını** ve **sahtekârlık (fraud)
yüzeyini** içerir:

| Bileşen | İşlevi |
|---|---|
| `LunixiConfig` | Değişmez, kurulurken doğrulanan yapılandırma. |
| `auth.CanonicalRequest` | İmzalanan kanonik dizgeyi ve `Digest` başlığını kurar. |
| `auth.Ed25519Signer` · `Ed25519KeyPair` | İstek imzalama; anahtar çifti üretme. |
| `auth.TokenManager` · `TokenStore` | İmzalı jeton alışverişi ve jeton önbelleği. |
| `http.HttpTransport` · `JdkHttpTransport` | Değiştirilebilir HTTP taşıyıcısı. |
| `http.Envelope` | Yanıt zarfının tek okuma noktası (sayfalama dahil). |
| `json.Json` | İmza sözleşmesine uygun kompakt JSON yazıcı/okuyucu. |
| `fraud.FraudClient` | Karar, karar kaydı ve olay alımı. |
| `webhook.WebhookVerifier` | Gelen webhook'ların fail-closed doğrulanması. |
| `exception.*` | Tipli hata ailesi. |

Diğer ürün yüzeyleri bu sürümde yoktur. İstek katmanı hepsi için aynıdır; yeni
bir yüzeyin eklenmesi buradakini değiştirmez.

## Kurulum

Gradle:

```groovy
implementation 'com.lunixi:lunixi-java-sdk:0.1.0'
```

Maven:

```xml
<dependency>
  <groupId>com.lunixi</groupId>
  <artifactId>lunixi-java-sdk</artifactId>
  <version>0.1.0</version>
</dependency>
```

SDK'nın **çalışma zamanı bağımlılığı yoktur**: imza, HMAC, HTTP ve JSON JDK ile
karşılanır. Böylece sınıf yolunuzdaki kütüphanelerle sürüm çatışması çıkmaz.

## Anahtar çifti ve kimlik doğrulama

Anahtar çiftini kendiniz üretmenizi ve yalnız **açık anahtarı** yüklemenizi
öneririz; özel anahtar sistemlerinizden hiç çıkmaz.

```java
Ed25519KeyPair keyPair = Ed25519KeyPair.generate();
// keyPair.publicKeyPem()  → panele yükleyeceğiniz yarı
// keyPair.privateKeyPem() → sır yöneticinizde saklayın
```

Özel anahtarı kaynak dosyaya yazmayın ve günlüğe düşürmeyin. Sızan bir anahtar,
iptal edilene kadar hesabınız adına istek imzalayabilir.

Kimlik doğrulama iki aşamalıdır ve SDK ikisini de sizin yerinize yürütür:

1. Gövdesiz, Ed25519 ile imzalı bir istekle kısa ömürlü bir erişim jetonu
   alınır.
2. Jeton her çağrıya eklenir. Olay alımı gibi istek başına imza isteyen uçlarda
   imza başlıkları da eklenir.

## Hızlı başlangıç

```java
LunixiClient client = LunixiClient.create(LunixiConfig.builder()
        .baseUrl("https://api.example.com")
        .keyId(System.getenv("LUNIXI_KEY_ID"))
        .privateKey(System.getenv("LUNIXI_PRIVATE_KEY"))
        .environment(LunixiConfig.Environment.TEST)
        .build());
```

İstemciyi **bir kez kurup saklayın**. Değişmezdir, iş parçacıkları arasında
paylaşılabilir ve jeton önbelleğini taşır; her istekte yeniden kurulan bir
istemci her çağrıda yeniden jeton alır.

### Karar sorma

```java
FraudDecision decision = client.fraud().evaluate(EvaluateRequest.create()
        .standaloneIntegrationMode()
        .channel(FraudTaxonomy.Channel.STANDALONE_API)
        .productContext(FraudTaxonomy.ProductContext.DIRECT_API)
        .transactionRef("order-2026-0001")
        .payment(Map.of("amount", 25000, "currency", "TRY"))
        .customer(Map.of("email", email, "country", "TR"))
        .device(Map.of("ipAddress", ipAddress)));

if (decision.isBlock()) {
    // işlemi reddedin
} else if (decision.isForce3d()) {
    // kart hamili doğrulamasını zorunlu kılın
}
```

Lunixi üzerinden alınan bir ödemede karar yetkilendirme içinde **zaten**
üretilir; o kararı ikinci kez sormak yerine kaydından okuyun:

```java
Optional<FraudDecision> recorded = client.fraud().decisions().latestForPayment(paymentId);
```

`evaluate`, kendi olaylarınızı (kayıt, giriş, para çekme, kendi formunuz veya
kendi sağlayıcınızla yetkilendirdiğiniz bir kart ödemesi) taramak içindir.

### Karar kayıtları (imleçli sayfalama)

```java
FraudDecisionPage page = client.fraud().decisions()
        .list(DecisionLogFilter.create().paymentId(paymentId).limit(50));

for (FraudDecision record : client.fraud().decisions()
        .iterate(DecisionLogFilter.create().limit(100))) {
    // sayfalar imleç izlenerek tembel çekilir
}
```

İmleç **opaktır**: çözmeyin, üretmeyin, değiştirmeyin; bir sonraki istekte
aynen geri gönderin. Bozuk bir imleç sessizce ilk sayfaya dönmez, hata verir.

Toplam satır sayısı imleçli sayfalamada varsayılan olarak **gelmez** (filtreli
sayım pahalıdır). İlerlemek için imleci kullanın.

### Olay alımı

```java
client.fraud().events().record(Map.of(
        "eventId", "evt-0001",
        "eventType", "card_payment",
        "subjectType", "customer",
        "occurredAt", "2026-10-11T08:00:00Z"));

FraudEventBatchResult batch = client.fraud().events().recordBatch(events);
if (!batch.failures().isEmpty()) {
    // DÜZELTİP yeniden gönderin
}
```

İki nokta önemlidir:

- **Parti sınırı 500'dür** ve sınırı aşan parti **tamamen** reddedilir. SDK bunu
  ağa çıkmadan önce denetler, böylece uzun bir besleme tek bir hatayla değil
  yüzlerce kayıp olayla sonuçlanmaz.
- **Parti kısmen başarılı olabilir.** Uç, bir olay doğrulamadan geçmese bile
  `200` döner ve kalanları yazmaya devam eder. Yalnız durum kodunu okuyan bir
  entegrasyon olay kaybeder; `failures()` listesini okuyun.

Aynı `eventId` ile yeniden gönderim güvenlidir: ikinci çağrı yinelenen olarak
tanınır.

### Webhook doğrulama

```java
WebhookEvent event = new WebhookVerifier().verify(rawBody, headers, endpointSecret);
```

`rawBody`, gövdenin **ham** hâli olmalıdır. Çerçeveniz JSON'u ayrıştırdıktan
sonra yeniden seri hâle getirirseniz baytlar değişir ve gerçek bir teslimat
doğrulanamaz. Gövdeyi ayrıştırmadan önce metin ya da bayt dizisi olarak
yakalayın.

Doğrulama fail-closed'dır: her uyuşmazlık istisna fırlatır. Doğrulanmamış bir
yükle işlem yapmayın.

Olay tipleri `FraudEvents` sabitlerindedir (yedi tip).

## Bilmeniz gereken üç tuzak

**1. Zarfta `success` alanı yoktur.** Sonuç, `status` alanında *metin* olarak
taşınır: `success`, `failure` veya `partial`. Boolean bir `success` okuyan
istemci hiçbir şey görmez. SDK yalnız `status` üzerinden okur; bu hatayı
yapmanız mümkün değildir.

**2. `200` yanıt `failure` taşıyabilir.** Zarf, isteğe cevap üretildikten sonra
kurulur; alt katmanda bildirilen bir başarısızlık zarfa düşerken HTTP durumu
`200` kalabilir. SDK her `2xx` yanıtta zarfı da denetler ve `failure` ise
istisna fırlatır. `partial` hata değildir: parti işlemlerinde kısmi başarının
raporlanma biçimidir.

**3. Gövde kompakt gönderilmelidir.** Sunucu, aldığı gövdeyi ayrıştırıp kendi
kompakt biçiminde yeniden üreterek özeti yeniden hesaplar. Biçimlendirilmiş
(girintili), anahtar sırası değişmiş veya farklı kaçışlanmış bir gövde başka bir
özet üretir ve istek imza hatasıyla reddedilir — gövdede gözle görünür bir
yanlışlık olmadığı için nedeni bulmak zordur. Bu yüzden SDK kendi JSON
yazıcısını taşır; gövdeyi SDK'ya `Map` olarak verin, kendiniz seri hâle
getirmeyin.

## Hata ailesi

| Sınıf | Durum |
|---|---|
| `ValidationException` | `400` / `422` — gövde reddedildi. |
| `AuthenticationException` | Jeton alınamadı (imza, anahtar, IP). |
| `RateLimitException` | `429` — kota doldu; `retryAfter()`. |
| `ServiceUnavailableException` | `503` — hizmet erişilemez; `retryAfter()`. |
| `LunixiApiException` | Diğer hata yanıtları ve taşıma hataları. |
| `ConfigurationException` | Yerel doğrulama; ağa çıkılmadan önce. |
| `SignatureException` | Anahtar okunamadı ya da imzalanamadı. |
| `WebhookVerificationException` | Webhook doğrulanamadı. |

Dallanmayı **`code()`** üzerinden yapın; mesaj metni yerelleştirilir ve
değişebilir.

`retryAfter()`, sunucu başlık gönderdiyse doludur. Göndermediyse boştur ve SDK
bir değer **uydurmaz**; uydurulmuş bir gecikme, istemcilerin sunucunun hiç
istemediği bir hızda yeniden denemesi demektir.

## Yeniden deneme davranışı

- `401` → jeton tazelenir ve istek **bir kez** yeniden gönderilir. Reddedilen
  jeton hiçbir şeyin yürütülmediği anlamına gelir; bu yüzden tekrar güvenlidir.
- Taşıma hatası veya `5xx` → yalnız **okuma** ya da **idempotency anahtarı
  taşıyan** çağrılarda yeniden denenir. Anahtarsız bir yazma zaman aşımına
  uğradığında aslında işlenmiş olabilir; körlemesine tekrar aynı işlemi ikinci
  kez gönderirdi.
- `4xx` → hiç yeniden denenmez.

## Yerel derleme ve test

```bash
gradle build   # derleme + testler
gradle test
```

Testler ağa çıkmaz. İmza, zarf, sayfalama ve JSON davranışı birim testlerle
doğrulanır; webhook imzaları **diğer dillerle paylaşılan** vektör dosyasından
okunur, böylece diller birbiriyle ve göndericiyle aynı sonucu ürettiğini
kanıtlar. Hiçbir anahtar deposuya işlenmemiştir; test anahtarları çalışma anında
üretilir.

---

# English

## Scope

This release covers the platform's **core request layer** and its **fraud
surface**: configuration, Ed25519 request signing, the token exchange and its
cache, a swappable HTTP transport, envelope and cursor-paging handling, a
compact JSON writer that satisfies the signing contract, the typed error
family, webhook verification, and the fraud decision, decision-record and event
calls.

Other product surfaces are not in it yet. The request layer is the same for all
of them, so adding one does not change what is here.

## Install

Gradle:

```groovy
implementation 'com.lunixi:lunixi-java-sdk:0.1.0'
```

Maven:

```xml
<dependency>
  <groupId>com.lunixi</groupId>
  <artifactId>lunixi-java-sdk</artifactId>
  <version>0.1.0</version>
</dependency>
```

The SDK has **no runtime dependencies**: signing, HMAC, HTTP and JSON all come
from the JDK, so it cannot collide with the library versions on your classpath.

## Key pair and authentication

Generate the key pair yourself and upload only the **public** half; the private
key then never leaves your systems.

```java
Ed25519KeyPair keyPair = Ed25519KeyPair.generate();
```

Keep `privateKeyPem()` in a secret manager — never in a source file, never in a
log line. A leaked key can sign requests as your account until it is revoked.

Authentication has two steps and the SDK performs both: a bodyless, Ed25519-
signed request obtains a short-lived access token, and that token is attached to
every call. Routes that are authenticated per request — event submission —
additionally carry the signature headers.

## Quick start

```java
LunixiClient client = LunixiClient.create(LunixiConfig.builder()
        .baseUrl("https://api.example.com")
        .keyId(System.getenv("LUNIXI_KEY_ID"))
        .privateKey(System.getenv("LUNIXI_PRIVATE_KEY"))
        .environment(LunixiConfig.Environment.TEST)
        .build());
```

Build the client once and keep it. It is immutable, safe to share between
threads, and holds the token cache; a client created per request fetches a fresh
token every time.

### Ask for a decision

```java
FraudDecision decision = client.fraud().evaluate(EvaluateRequest.create()
        .standaloneIntegrationMode()
        .channel(FraudTaxonomy.Channel.STANDALONE_API)
        .productContext(FraudTaxonomy.ProductContext.DIRECT_API)
        .transactionRef("order-2026-0001")
        .payment(Map.of("amount", 25000, "currency", "TRY")));
```

For a payment taken through Lunixi a decision has **already** been made inside
authorisation — read it back instead of asking again:

```java
Optional<FraudDecision> recorded = client.fraud().decisions().latestForPayment(paymentId);
```

`evaluate` is for screening events of your own: a sign-up, a login, a
withdrawal, a form of yours, or a card payment you authorise through your own
provider.

### Decision records (cursor paging)

```java
FraudDecisionPage page = client.fraud().decisions()
        .list(DecisionLogFilter.create().paymentId(paymentId).limit(50));

for (FraudDecision record : client.fraud().decisions()
        .iterate(DecisionLogFilter.create().limit(100))) {
    // pages are fetched lazily, following cursors
}
```

The cursor is **opaque**: send it back verbatim, never decode or rebuild it. A
damaged cursor is answered with an error rather than quietly restarting from the
first page. A total row count is not returned by default, because a filtered
count is expensive; advance with the cursor.

### Event submission

```java
client.fraud().events().record(Map.of(
        "eventId", "evt-0001",
        "eventType", "card_payment",
        "subjectType", "customer",
        "occurredAt", "2026-10-11T08:00:00Z"));

FraudEventBatchResult batch = client.fraud().events().recordBatch(events);
if (!batch.failures().isEmpty()) {
    // correct and resend
}
```

Two things matter here:

- **The batch limit is 500**, and an oversized batch is rejected **in full**.
  The SDK checks before the call, so a long feed costs one readable error rather
  than hundreds of lost events.
- **A batch can succeed partially.** The endpoint answers `200` and keeps
  writing the remaining events when one of them fails validation. An integration
  that reads only the status code loses events; read `failures()`.

Resending the same `eventId` is safe: the second call is recognised as a
duplicate.

### Webhook verification

```java
WebhookEvent event = new WebhookVerifier().verify(rawBody, headers, endpointSecret);
```

`rawBody` must be the **raw** body. If your framework parses the JSON and you
serialise it again, the bytes change and a genuine delivery fails verification.
Capture the body as a string or byte array before any parsing.

Verification is fail-closed: every discrepancy throws, and an unverified payload
must not be acted on. The event types are the constants on `FraudEvents` (seven
of them).

## Three traps worth knowing

**1. The envelope has no `success` field.** The outcome travels in `status` as a
*string*: `success`, `failure` or `partial`. A client reading a boolean
`success` sees nothing at all. The SDK reads only `status`, so the mistake
cannot be made through it.

**2. A `200` can report a failure.** The envelope is built after the answer is
produced, so a failure reported downstream lands in the envelope while the HTTP
status stays `200`. The SDK inspects the envelope on every `2xx` and throws when
it says `failure`. `partial` is not a failure: it is how a batch reports that
some of its items landed.

**3. The body must be sent compact.** The server parses the body it received and
re-serialises it in its own compact form to recompute the digest. A
pretty-printed body, a different key order or different escaping hashes to
something else and the request is rejected as a signature failure — hard to
diagnose, because nothing in the body looks wrong. That is why the SDK carries
its own JSON writer: hand it a `Map` and let it serialise.

## Errors

| Class | Condition |
|---|---|
| `ValidationException` | `400` / `422` — the body was rejected. |
| `AuthenticationException` | No token could be obtained (signature, key, IP). |
| `RateLimitException` | `429` — quota exhausted; `retryAfter()`. |
| `ServiceUnavailableException` | `503` — unavailable; `retryAfter()`. |
| `LunixiApiException` | Other error answers and transport failures. |
| `ConfigurationException` | Local validation, before anything is sent. |
| `SignatureException` | The key could not be read, or signing failed. |
| `WebhookVerificationException` | A webhook could not be verified. |

Branch on **`code()`**; the message text is localised and may be reworded.

`retryAfter()` is present only when the server sent the header. It is empty
rather than guessed: an invented delay would make every client retry at a rate
the server never asked for.

## Retry behaviour

- `401` → the token is refreshed and the request is sent **once** more. A
  rejected token means nothing was executed, so the repeat is safe.
- Transport failure or `5xx` → retried only for a **read** or a call carrying an
  **idempotency key**. A write without one may have landed despite the timeout,
  and a blind retry would submit it twice.
- `4xx` → never retried.

## Build and test locally

```bash
gradle build
gradle test
```

No test reaches the network. Signing, the envelope, paging and JSON behaviour
are covered by unit tests, and the webhook signatures are read from the vector
file **shared with the other language SDKs** — which is what proves the
implementations agree with each other and with the sender, rather than each
agreeing with itself. No key material is committed; test keys are generated at
run time.
