<p align="center"><a href="https://lunixi.com"><img src="https://raw.githubusercontent.com/Lunixi-Payment/lunixi-java/main/.github/assets/lunixi-logo.svg" alt="Lunixi" height="64"></a></p>

# Lunixi Java SDK

[English](README.md) · [Dokümantasyon](https://docs.lunixi.com/tr/sdks/java) · [Sürüm geçmişi](CHANGELOG.md) · [Güvenlik](SECURITY.md)

**1.0.1**, mevcut fraud istemcisine ödeme oluşturma, tahsilat/iade/iptal, çoklu kart sorgulama/iadesi ve kalıcı ödeme mutabakatı ekler. **JDK 17+**, **sıfır harici çalışma zamanı bağımlılığı**, MIT lisansı.

| Yüzey | Kapsam |
|---|---|
| Kimlik doğrulama/taşıma | Ed25519 PKCS#8/tohum anahtar, kanonik JSON/imza, token önbelleği, sınırlı HTTPS, yönlendirme yasağı, tipli hatalar |
| Ödemeler | `client.payments().createIntent/get/list/capture/refund/voidPayment/getSplit/refundSplit`; ödeme yöntemleri (`listPaymentMethods`, `directMethodPayment`, `confirmPaymentAction`/`resendPaymentAction`), senet, Para Transferi istisnaları, `createSplitPayment`/`listSplitPayments`; `binInfo`, `installmentOptions`, `cardTaxonomy`, saklı kartlar, `createMarketplaceIntent`, analizler; `client.reference()` adres sözlüğü |
| Ödeme linkleri | `client.paymentLinks()`: oluşturma, listeleme, okuma, güncelleme, durdurma/sürdürme/kapatma, gönderme, QR kod, denemeler, ödemeler, üç adımlı görsel yükleme |
| Cüzdan | `client.wallet()`: programlar, son kullanıcılar, cüzdanlar, para yatırma, kurumsal hesaplar, ücretler, transferler, para çekme, işlemler, bakiye yükleme, ödemeler, QR kodlar, toplu ödemeler |
| Banka hesapları | `client.bankConnect()`: bankalar, bağlantılar, hesaplar, bakiyeler, hesap hareketleri, eşitleme; `transfers()` ve `fx()` |
| Yaptırım | `client.sanction()`: isim, cüzdan, hesap, işlem, kamu ihalesi yasağı ve olumsuz medya taraması, izleme, varlık istihbaratı, engelli listesi |
| Sigorta | `client.insurance()`: ürünler, referans kodları, teklifler, siparişler, prim tahsilatı ve iadesi, poliçe dosyaları, mutabakat, potansiyel müşteriler |
| Abonelik | `client.subscriptions()`: abonelikler, `plans()`, `customers()`, `cards()`, `invoices()`, `portal()`, `products()`, `features()`, `addons()`, `usage()` |
| KYC | `client.kyc()`: KYC / KYB / kanıt oturumları, belge yükleme, manuel inceleme, üye işyeri politikası, KYC webhook kuyruğu, silme talepleri |
| Kimlik | `client.identity()`: canlı oturum analizi, bir cihazdaki bağlı hesaplar, oturumlar, zaman çizelgeleri, riskli kaynaklar |
| Pazaryeri | `client.marketplace()`: alt bayiler arasında bölünen checkout ve kart tahsilatları, bayi yaşam döngüsü, komisyonlar, hakediş ödemeleri, denetim kaydı |
| Kalıcı checkout | `CommerceService`, yerel `LedgerStore`, `Money`, `OrderSnapshot`, `PaymentEvidence`, AES-GCM `SecretBox` |
| Fraud | Karar sorgulama, imleçli karar kayıtları, tekli/toplu olay kaydı |
| Webhook | v2 HMAC, zaman/imzalı gövde doğrulama; ödeme için `verifyMerchant` |

SDK sunucuda çalışır; kart numarası/CVV toplamaz, hazır mağaza/yönetici uygulaması değildir.



1.0.1 para alanlarını kanonik küçük birim string değerleriyle taşır. [Geçiş rehberini](MIGRATION-1.0.md) uygulayın; Maven Central üzerinde yayımlanan sürümü aşağıdaki komutlarla kurun.

## Maven Central üzerinden kurulum

**[`com.lunixi:lunixi-java-sdk:1.0.1`](https://central.sonatype.com/artifact/com.lunixi/lunixi-java-sdk/1.0.1)** koordinatını JDK 17+ ile kullanın.

Gradle:

```groovy
repositories { mavenCentral() }
dependencies { implementation 'com.lunixi:lunixi-java-sdk:1.0.1' }
```

Maven:

```xml
<dependency>
  <groupId>com.lunixi</groupId>
  <artifactId>lunixi-java-sdk</artifactId>
  <version>1.0.1</version>
</dependency>
```

Maven standart Central deposunu kullanır; bu bağımlılık için özel depo veya yerel kaynak derlemesi gerekmez.

### Geliştirme için kaynak ve yerel Maven alternatifi

Geliştirme sırasında incelenmiş kaynak etiketini **Gradle 9.7.1** ile derleyip yerel Maven deposuna kurabilirsiniz:

```sh
git clone --depth 1 --branch v1.0.1 https://github.com/Lunixi-Payment/lunixi-java.git
cd lunixi-java
gradle build publishToMavenLocal -PlunixiUnsigned
```

```groovy
repositories { mavenLocal(); mavenCentral() }
dependencies { implementation 'com.lunixi:lunixi-java-sdk:1.0.1' }
```

Maven bu geliştirme kurulumu için varsayılan yerel depoyu otomatik okur. Kaynak derleme komutları uzak kayıt deposuna yayın yapmaz. İmzasız `centralBundle` yalnız paket yapısını doğrular; registry yayını ayrı yetkilendirilmiş imzalı sürüm işlemi gerektirir.

## Sunucu yapılandırması

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

Özel anahtarı, webhook secret'ı ve bağımsız 32 bayt saklama anahtarını korumalı sunucu ayarında tutun. Lunixi'ye yalnız Ed25519 açık anahtarını yükleyin. Her merchant/ortam için istemciyi bir kez kurup token önbelleğiyle saklayın. Gateway origin veya ortam değişikliği eski kalıcı ödeme denemesini kullanamaz.

## Ödeme ve framework entegrasyonu

[Türkçe ödeme/Commerce rehberi](docs/commerce.tr.md), tam tutar, kalıcı işlem anahtarı, imzalı webhook, atomik yerel sipariş/stok, olay kutusu, kayıp yanıt toparlama ve teslim blokelerini anlatır. [English guide](docs/commerce.en.md).

Yerel JDBC deposu, otomatik yapılandırma ve çalışan MVC uygulaması için [Lunixi Spring Boot Starter](https://github.com/Lunixi-Payment/lunixi-spring-boot-starter), [checkout örneği](https://github.com/Lunixi-Payment/lunixi-spring-boot-starter/tree/main/samples/spring-boot) ve [Spring rehberi](https://docs.lunixi.com/tr/sdks/spring-boot) kullanılır. SDK test dosya deposunu üretimde kullanmayın.

Varsayılan gömülü akış mağazanın kendi sayfasında resmî Lunixi tarayıcı paketi ve `checkout.token()` ile açılır. İsteğe bağlı `checkout.hostedUrl()`, backend `paymentPageUrl` değerini aynen döndürür; gömülü token’dan adres üretilmez. Açık ödeme yöntemleri, yemek kartı, alışveriş kredisi, taksit, sigorta ve çoklu kart Lunixi paneli/formundan yönetilir. Token yalnız yerel oturum/müşteri/sipariş sahipliği ve CSRF kontrolü sonrasında döner. Sağlayıcının tarayıcı POST dönüşü bilgilendirici 303 ile sahiplik kontrolü olan GET'e geçer; başarı alanı ödeme kanıtı değildir.

`createIntent` gateway gövdesini map olarak alır. İsteğe bağlı `enabledInstallments` listesi, bir ödeme için formun sunacağı taksit sayılarını yalnızca daraltır; form, panel ayarlarınızın, kartın ve platform üst sınırının da izin verdiği sayıları gösterir. Kısıt istemiyorsanız alanı göndermeyin; `List.of(1)` yalnız tek çekime izin verir. 1–36 arası, tekrarsız ve 1'i içeren tamsayıları artan sırada gönderin; gateway başka bir listeyi (boş liste dahil) 400 ile reddeder. Her `basketItems` satırı isteğe bağlı `productCategory` alanını taşıyabilir: Lunixi ürün kategorisi kodu (ör. `"MOBILE_PHONE"`); yasal taksit sınırı olan ödeme yöntemleri (ör. alışveriş kredisi) ve diğer platform özellikleri tarafından kullanılır. Alanı göndermezseniz mevcut davranış korunur; gateway bilinmeyen kodu 400 ile reddeder. Kodlar `ProductCategory` sabitleri olarak hazırdır (`com.lunixi.sdk.payment.ProductCategory.MOBILE_PHONE`, 40 kodun tamamı `ProductCategory.ALL` içinde); liste yalnız sona ekleme ile büyüdüğünden değer düz string olarak kalır ve SDK göndermeden önce denetlemez.

```java
Map<String, Object> request = new LinkedHashMap<>();
request.put("amount", "10050");
request.put("currency", "TRY");
request.put("orderId", order.number());
request.put("callbackUrl", "https://merchant.example/payments/return");
request.put("enabledInstallments", List.of(1, 2, 3)); // isteğe bağlı: en fazla 3 taksit
// Her ödemenin istediği sipariş bilgileri (ödeme sayfanızdan alın, yer tutucu kullanmayın).
request.put("buyer", Map.of("name", "Ada", "surname", "Yilmaz", "identityNumber", "11111111111",
        "email", "ada@example.com", "gsmNumber", "+905350000000",
        "city", "Istanbul", "country", "Turkey", "zipCode", "34000"));
request.put("billingAddress", Map.of("contactName", "Ada Yilmaz", "address", "Example Street 1",
        "city", "Istanbul", "country", "Turkey", "zipCode", "34000"));
request.put("basketItems", List.of(Map.of("id", "SKU-1", "name", "Hediye kartı", "category1", "Dijital",
        "itemType", "VIRTUAL", "price", "10050")));
CheckoutSession checkout = client.payments().createIntent(request, persistedAttemptKey);
```

Her ödeme gövdesi sipariş bilgilerini taşır (`com.lunixi.sdk.payment.OrderDetails`): `buyer` (`name`, `surname`, `identityNumber` — T.C. kimlik veya pasaport numarası, `email` — ASCII, `gsmNumber`, `city`, `country`, `zipCode`, `ip` — alıcının tarayıcı IP'si), `billingAddress` (`contactName`, `address`, `city`, `country`, `zipCode`) ve en az bir `basketItems` satırı (`id`, `name`, `category1`, `itemType`, `price`; fiyatların toplamı tutara eşittir); `PHYSICAL` bir kalem ayrıca `shippingAddress` ister. `createIntent` ve pazaryeri intent'lerinde (`createMarketplaceIntent`, `marketplace().createCheckoutIntent`) `ip` gönderilmeyebilir: Lunixi formu bu değeri alıcının tarayıcısından alır. `createIntent`, `createMarketplaceIntent`, `directMethodPayment`, `createSplitPayment`, pazaryeri kart tahsilatları ve `storeCard` (yalnız alıcı ve fatura adresi; `StoreCardRequest.withBuyer` / `withBillingAddress`) eksik alanların yollarını belirten `ConfigurationException` ile göndermeden önce durur (`OrderDetails.missing(body, withBasket, checkoutIntent)` aynı listeyi döndürür). Gateway eksik alanı 400 `payment.order.field_required`, geçersiz alanı `payment.order.field_invalid` ile yanıtlar (`OrderDetails.FIELD_REQUIRED`, `FIELD_INVALID`).

## Ödeme yöntemleri

`client.payments().listPaymentMethods()` hesabınıza açık yöntemleri listeler (imzalı): her yöntem için `blockName`, `providerSelection` (`ProviderSelection`), `providerSelectionMode` (`ProviderSelectionMode`), `preauth` desteği, limitler ve gönderilecek `inputFields` alanlarıyla sağlayıcılar. `listPaymentMethods(PaymentMethod.MOBILE_BILLING)` tek yöntemi döndürür (`QR`, `QR_CODE` olarak gönderilir). `providerCode` değerini her zaman bu listeden okuyun; kodda sabitlemeyin.

`directMethodPayment(request, idempotencyKey)` herhangi bir yöntemle ödemeyi sunucunuzdan başlatır (`POST /api/v1/payments/direct/payment`, imzalı, önceden kalıcılaştırılmış 16–128 ASCII karakterli anahtar zorunlu). Gövdeyi `DirectMethodPaymentRequest` ile kurun; ham gövde map'i aynı kurallarla denetlenir. Yanıt gateway zarfıdır: `data.status` ve alıcının işlem yapması gerektiğinde `data.action` (`PaymentActionType`: `OTP`, `REDIRECT`, `DISPLAY`, `APP_APPROVAL`, `INPUT`). `OTP` veya `INPUT` adımını `confirmPaymentAction(paymentId, actionId, Map.of("otp", "123456"))` ile tamamlayın (yalnız `otp`, `consentAccepted`, `fields`); `resendPaymentAction(paymentId, actionId)` yeni kod gönderir. Sipariş kararını webhook'a göre verin.

```java
Map<String, Object> methods = client.payments().listPaymentMethods(PaymentMethod.MOBILE_BILLING);
String providerCode = chooseProvider(methods); // from data.methods[].providers[].providerCode
Map<String, Object> response = client.payments().directMethodPayment(
        DirectMethodPaymentRequest.create("10000", "TRY", order.number())
                .withMethodFields(PaymentMethod.MOBILE_BILLING, providerCode, Map.of("gsmNumber", "+905321234567"))
                .withBuyer(buyer).withBillingAddress(billingAddress).withBasketItems(basketItems), // sipariş bilgileri
        persistedAttemptKey);
```

Builder ve ham denetim şunları göndermeden önce reddeder: `price` / `paidPrice`, bilinmeyen alanlar, başka bir yöntemin bloğu, `CARD` olmadan kart alanları (`card`, `installment`, `installmentQuoteToken`, `force3D`), eksik blok, hatalı blok (en fazla 32 alan; en fazla 1024 karakterlik string değerler; en fazla 16 alanlı `ADDRESS` nesneleri; en fazla 10 nesnelik `GROUP` listeleri), geçersiz `providerCode` ve 1–100 sepet kalemi olmayan alışveriş kredisi ödemesi. Kural ihlalleri `ConfigurationException` fırlatır.

### Para Transferi

Alıcı parayı kendi bankasından sizin banka hesabınıza gönderir; Lunixi parayı tutmaz. Alıcının bankasını yöntem satırındaki `bankCode` seçeneklerinden gönderin (`PaymentMethod.BANK_TRANSFER_OTHER_BANKS_CODE`, `"9999"`, "Diğer bankalar" seçeneğidir); blok `providerCode` almaz. Yanıt, transfer talimatını taşıyan bir `DISPLAY` adımıyla HTTP 202'dir:

```java
Map<String, Object> response = client.payments().directMethodPayment(
        DirectMethodPaymentRequest.create("10000", "TRY", order.number())
                .withMethodFields(PaymentMethod.BANK_TRANSFER, null, Map.of("bankCode", selectedBankCode)),
        persistedAttemptKey);
PaymentBankTransferInstruction.fromResponse(response).ifPresent(instruction -> show(
        instruction.bankName(), instruction.holderName(), instruction.iban(), instruction.amountMinor(),
        instruction.paymentCode(), instruction.expiresAt()));
```

Alıcı `paymentCode` değerini transfer açıklamasına yazar. Geç, eksik ve fazla gelen transferleri `listBankTransferExceptions(environment, status, limit)` ile listeleyin (`TEST` / `LIVE`, `PaymentBankTransferExceptionStatus`, 1–200); `acceptBankTransferException(id)` ile kabul edin ya da kendi bankanızdan iade ettikten sonra `markBankTransferExceptionRefunded(id)` ile işaretleyin. Webhook'lar: `PaymentBankTransferEvents` (`payment.bank_transfer.*`); sonuç yine `payment.captured` / `payment.failed` olayıdır.

### Senetle Ödeme

Planı ya vade tablosu ya da senet adedi olarak verin, ikisini birden değil:

```java
DirectMethodPaymentRequest.create("10000", "TRY", order.number())
        .withMethodFields(PaymentMethod.PROMISSORY_NOTE, providerCode, debtorFields)
        .withPromissoryNoteSchedule(List.of(
                Map.of("amount", "5000", "dueDate", "2027-01-15"),
                Map.of("amount", "5000", "dueDate", "2027-02-15")));
```

Vade tablosu, toplamı `amount` değerine eşit pozitif küçük birim string tutarlı ve sıralı `YYYY-MM-DD` takvim günlü 1–24 satırdan oluşur; `withPromissoryNoteCount(3)` tutarın bağlantı ayarlarınıza göre bölünmesini sağlar. `getPromissoryNotes(paymentId)` senetleri döndürür (`PromissoryNoteValues.Status`), `cancelPromissoryNotes(paymentId, serialNumbers)` iptal eder (`null` = açık senetlerin tümü; senet başına `PromissoryNoteValues.CancelResult` / `CancelErrorCode`) ve `downloadPromissoryNoteDocument(paymentId, PromissoryNoteValues.DocumentLocale.TR)` yazdırılabilir PDF'i `PromissoryNoteDocument.bytes()` olarak döndürür. Webhook'lar: `PromissoryNoteEvents`. Adres alanları (`ADDRESS`) kodlarını `client.reference().listProvinces()`, `listDistricts(provinceCode)` ve `listNeighborhoods(districtCode)` çağrılarından alır; boş liste o seviyenin henüz yayımlanmadığını gösterir.

Özel bir `HttpTransport` belgeleri yalnız `sendBinary(...)` uyguladığında indirir; `JdkHttpTransport` bunu uygular (en fazla 16 MiB).

### Parçalı ödeme

`createSplitPayment(body, idempotencyKey)` parçalı ödemeyi başlatır ve bir adımdan sonra sürdürür: aynı gövdeyi aynı anahtarla gönderin. Gövde sipariş bilgilerini taşır (`buyer`, `billingAddress`, `basketItems`; `PHYSICAL` kalemde `shippingAddress`; `SplitPaymentValues.SPLIT_PAYMENT_FIELDS`). Ödeme sürerken yanıt (durumu `code` olarak ve `data.splitPaymentId` ile taşıyan HTTP 400) hata olarak fırlatılmaz, döndürülür. Kart dışı bir parça, `providerCode` ile birlikte `mobileBilling`, `wallet` veya `promissoryNote` alanlarından tam olarak birini taşır; yemek kartı, açık bankacılık, alışveriş kredisi ve Para Transferi parça olamaz. `listSplitPayments(filters)` parçalı ödemeleri listeler (`status`, `currency`, `createdFrom`, `createdTo`, `orderId`, `ownerRef`, `limit`, `after`); `getSplit` / `refundSplit` birini okur ve iade eder. Webhook'lar: `SplitPaymentEvents`; parça olaylarını da almak için uç noktaya `SplitPaymentEvents.LEG_EVENTS_SUBSCRIPTION` ekleyin.

### Kartlar, taksitler ve pazaryeri intent'leri

`binInfo(binOrPan)` 6 veya 8 haneli bir BIN için kart, banka ve taksit uygunluğunu döndürür (`POST /api/v1/bin/info`); `cardTaxonomy()` marka, aile ve banka listelerini döndürür. `installmentOptions(InstallmentOptionsRequest.create(amount, currency))` taksit tablosunu döndürür; `withBinOrPan(...)` ve `withInstallment(n)` (n > 1) ile yanıt, `DirectMethodPaymentRequest.withInstallment(n, quoteToken)` için `quoteToken` değerini taşır. `withEnabledInstallments(List.of(1, 2, 3))`, `createIntent` çağrısının `enabledInstallments` kuralını izler (1'i içeren, tekrarsız 1–36 arası tamsayılar); liste göndermeden önce denetlenir ve artan sırada gönderilir (`EnabledInstallments.require`).

Saklı kartlar: `storeCard(StoreCardRequest.create(card, cardUserKey, callbackUrl).withBuyer(buyer).withBillingAddress(billingAddress), idempotencyKey)` (alıcı ve fatura adresi zorunlu, sepet yok) ve `deactivateStoredCard(storedCardToken, idempotencyKey)` imzalıdır ve önceden kalıcılaştırılmış 16–128 ASCII karakterli anahtar ister; `listStoredCards(cardUserKey)` ve `getStoredCard(storedCardToken)` kayıtları okur. Kart alanları verdiğiniz gibi gönderilir; SDK hata mesajında veya `toString()` çıktısında hiçbir zaman görünmez.

`createMarketplaceIntent(body[, idempotencyKey])` (`POST /api/v1/payments/marketplace/intents`, imzalı) intent gövdesine ek olarak `sellers[]` (`{subDealerId, amountMinor, scenario?}`) ve `multiSellerPolicy` alır. Satıcıların `amountMinor` değerlerinin toplamı `amount` değerine eşit olmalıdır; farklı bir toplam göndermeden önce reddedilir.

`list(filters)` ödemeleri listeler (`page`, `limit`, `status`, `customerId`, boolean `includeLegs`). `failoverRecoveryAnalytics(filters)` ve `providerCredentialAnalytics(credentialId, filters)` analiz raporlarını okur.

## Ödeme linkleri

`client.paymentLinks()` ödeme linklerini yönetir (`/api/v1/payments/links`). Her gateway çağrısı imzalıdır; üye işyerini ve ortamı imzalayan anahtar belirler. Gövdeyi `CreatePaymentLinkRequest.fixed(...)`, `.open(...)` veya `.itemized(...)` ile kurun (satırlar: `PaymentLinkItem`, sorular: `PaymentLinkCustomField`); ham gövde map'i `PaymentLinkValues.PAYMENT_LINK_SPEC_FIELDS` ile denetlenir. Tutarlar kanonik küçük birim string değerleridir.

```java
Map<String, Object> link = client.paymentLinks().create(
        CreatePaymentLinkRequest.fixed(PaymentLinkValues.Usage.SINGLE_USE, "150000", "TRY", "Invoice 2026-001")
                .withRecipient(Map.of("email", "buyer@example.com"))
                .withExpiresAt(Instant.now().plus(Duration.ofDays(7))),
        persistedOperationKey);
client.paymentLinks().send((String) link.get("id"), PaymentLinkValues.DeliveryChannel.EMAIL, persistedSendKey);
```

`create` ve `send` bir `Idempotency-Key` ister (8–200 yazdırılabilir ASCII karakter); `update(linkId, expectedRowVersion, changes[, key])`, `pause`, `resume`, `deactivate` ve `action` isteğe bağlı anahtar alır. `usage`, `amountMode` ve `currency` güncellenemez. `list`, `listAttempts` ve `listPayments` bir `PaymentLinkPage` döndürür (`items`, `hasMore`, `nextCursor`, `totalCount`); `iterate`, `iterateAttempts` ve `iteratePayments` imleçleri izler. `qr(linkId, format, size)` görsel baytlarını döndürür (`svg` veya `png`, 128–2048 px).

Link görselleri üç adımda yüklenir: `createImageUpload(fileName, contentType, sizeBytes)` (PNG, JPEG veya WebP), baytların dönen `uploadUrl` adresine PUT ile gönderilmesi, ardından `confirmImageUpload(assetId)`; `uploadImage(fileName, contentType, bytes)` üçünü birden çalıştırır ve yayımlanan `PaymentLinkImage` değerini döndürür. PUT isteği gateway'e değil depolama sunucusuna gider: yalnız dönen `uploadHeaders` başlıklarını taşır, erişim token'ını veya istek imzasını asla taşımaz. İstek `HttpTransport.sendBytes(...)` ile gönderilir; `JdkHttpTransport` bunu uygular, özel bir transport görsel yüklemek için bu metodu ezmelidir.

## Cüzdan

`client.wallet()` cüzdan API'sini (`/api/v1/wallet/*`) alt istemcilerle kapsar: `programs()`, `endUsers()`, `wallets()`, `deposits()`, `corporate()`, `fees()`, `transfers()`, `withdrawals()`, `operations()`, `topups()`, `payments()`, `qr()` ve `bulkPayouts()`, ayrıca `ping()`. Okumalar dahil her çağrı imzalıdır. Her çağrı, hiçbir şey gönderilmeden önce `WalletRoutes` ile denetlenir (rota başına bir satır: metot, yol, `Idempotency-Key` kuralı, gövde ve sorgu alanları): bilinmeyen alan veya filtre, eksik zorunlu alan, küçük birim rakam string'i olmayan `amount` ya da eksik anahtar `ConfigurationException` fırlatır.

```java
Map<String, Object> started = client.wallet().transfers().initiateW2W(Map.of(
        "endUserId", endUserId, "sourceWalletAccountId", accountId,
        "amount", "12550", "currency", "TRY", "destinationPhone", "+905551112233"), persistedKey);
client.wallet().transfers().completeW2W((String) started.get("operationId"), Map.of(
        "endUserId", endUserId, "integrityHash", started.get("integrityHash"), "nonce", started.get("nonce")),
        persistedCompleteKey);
```

Para iki adımda hareket eder: `initiate…` limiti ayırır, `complete…` dönen `integrityHash` ve `nonce` değerleriyle (istendiğinde OTP ile) kesinleştirir; complete çağrıları `operationId` değerini gövdeye kendileri ekler. Listeler bir `WalletPage` döndürür; `iterate(filters)` imleçleri izler. `qr().image(qrId, format, scale)` SVG veya PNG baytlarını döndürür. Toplu ödemeler dört göz ilkesini izler: onaylayan anahtar, oluşturan anahtardan farklı olmalıdır.

## Banka hesapları

`client.bankConnect()` bağlı banka hesaplarınızı okur (`/api/v1/bank-connect`): `listBanks`, `listConnections` / `getConnection`, `listAccounts` / `getAccount`, `listBalances`, `listTransactions` / `getTransaction` (her liste `iterate…` ile) ve `requestSync(accountId)`. Her çağrı imzalıdır. Filtreler göndermeden önce `BankConnectValues.FILTERS` ile denetlenir: değer kümeleri, 1–100 arası `limit`, en fazla 100 hesap kimliği, `minAmountMinor <= maxAmountMinor` koşuluyla küçük birim tutar string'leri, yalnız `bookedFrom` ile birlikte `bookedTo` ve en fazla 92 günlük aralık. Banka bağlama yalnız Lunixi panelinden yapılır.

`transfers()` giden transferleri gönderir (`listPurposes`, `create`, `list` / `iterate`, `get`); `fx()` bir bağlantının iki hesabı arasında döviz bozdurur (`listRates`, `createQuote`, `createOrder`, `listOrders` / `iterateOrders`, `getOrder`). İkisi de `bank-connect:transfer` kapsamını ve IP izin listesi olan bir anahtarı ister. `create` ve `createOrder` verdiğiniz `Idempotency-Key` değerini denetler (8–200 yazdırılabilir ASCII karakter) ya da bir UUID v4 üretir; ağ hatası veya 5xx aynı anahtarla yeniden denenir. Süreçler arasında güvenle yeniden denemek için kendi anahtarınızı saklayın. Gövdeler `BankTransferRoutes` ile denetlenir. Webhook'lar: `BankConnectEvents` (`bank_account.*`, `BankAccountTransactionReceived.fromEvent` ile okunur) ve `BankTransferEvents` (`bank_transfer.*`, `BankTransferWebhookData.fromEvent` ile okunur); bunlar ödeme yönteminin `payment.bank_transfer.*` olayları değildir.

## Yaptırım taraması

`client.sanction()` isimleri, kripto cüzdanlarını, banka hesaplarını, işlemleri, kamu ihalesi yasaklarını ve olumsuz medyayı tarar, sürekli izleme çalıştırır, varlık istihbaratını ve engelli listenizi okur (`/api/v1/sanction/*`). Bu çağrılar yalnız erişim token'ını taşır (istek imzası yoktur). Gövdeler ve filtreler göndermeden önce `SanctionRoutes` ile denetlenir (alanlar, iç içe listeler, işlem için `parties` / `accounts` alanlarından en az biri, risk alanı ve mod kümeleri).

Tarama karar değil eşleşme döndürür. Sonucu `SanctionClient.isClearScreening(result)` ile okuyun: sonuç yalnız hiçbir eşleşme yoksa ve kapsam bildirildiği yerde `coverageStatus` değeri `COMPLETE` ise temizdir. Çalıştırılamayan tarama 503 (`ServiceUnavailableException`) döner; `status` değeri `success` olmayan 2xx yanıt da hata fırlatır; ikisi de boş sonuç olarak dönmez.

## Sigorta

`client.insurance()` sepet kalemleri için sigorta sunar, satar ve yönetir (`/api/v1/insurance`): `listProducts`, `searchProducts`, `listReferenceValues`, `createQuote` / `getQuote`, `createOrder` / `listOrders` / `getOrder`, `bindPayment`, `confirmPayment`, `issuePolicy`, `cancelOrder`, `refundPremium`, `getPolicyDocument`, `getPolicyReceipt`, `getPolicyCertificate`, `getReconciliationSummary` / `listReconciliationOrders` / `exportReconciliation` ve `createLead` / `listLeads` / `getLead` (her liste `iterate…` ile). Her çağrı imzalıdır.

`createOrder`, `confirmPayment`, `issuePolicy`, `cancelOrder`, `refundPremium` ve `createLead` bir `Idempotency-Key` ister (8–200 yazdırılabilir ASCII karakter); `createQuote` ve `bindPayment` isteğe bağlı anahtar alır. Teklifin sepeti ya `paymentIntentId` ya da `context.items` olur, ikisi birden olmaz. Bir sigorta türünün kendi nesnesine ait alan kuralları SDK'da tekrarlanmaz: gateway bütün hataları tek seferde 422 `insurance.validation.invalid` ile döndürür. `InsuranceClient.consentFromQuote(option, acceptedAt)` siparişin `consent` değerini, `InsuranceClient.premiumBasketItem(order, name, category1)` primi tahsil eden ödemenin prim kalemini kurar. Poliçe dosyaları `InsuranceDocument` (PDF baytları), dışa aktarma `InsuranceReconciliationExport` (`truncated`, `nextCursor` ve `aborted` ile CSV baytları) döndürür. Webhook'lar: `InsuranceEvents`; `InsuranceWebhookData.fromEvent` (siparişler) ve `InsuranceLeadWebhookData.fromEvent` (potansiyel müşteriler) ile okunur.

## Abonelik

`client.subscriptions()` `/api/v1/subscriptions/*` rotalarını (ve `/api/panel/v1/subscriptions/portal` altındaki portal linki rotalarını) kapsar: `create`, `get`, `list`, `cancel`, `pause`, `resume`, `changePlan` ve `auditLogs`, ayrıca `plans()`, `customers()`, `cards()`, `invoices()` (kataloğun `invoices.void` rotası için `voidInvoice`), `portal()`, `products()`, `features()`, `addons()` ve `usage()` alt istemcileri. Çağrılar yalnız erişim token'ını taşır (istek imzası yoktur). `create` bir `Idempotency-Key` ister (argüman veya isteğin `idempotencyKey` alanı; gövdede de gönderilir); plan, müşteri, kart, plan değişikliği ve tahsilat yeniden denemesi yazımları isteğe bağlı anahtar alır.

```java
Map<String, Object> subscription = client.subscriptions().create(Map.of(
        "customerId", customerId, "planId", planId, "paymentCardId", cardId), persistedKey);
SubscriptionPage invoices = client.subscriptions().invoices().list(Map.of("subscriptionId", subscription.get("id")));
```

Her çağrı göndermeden önce `SubscriptionRoutes` ile denetlenir: bilinmeyen veya eksik gövde alanları, bilinmeyen filtreler, eksik anahtar ve değer kümeleri (`collectionMethod`, `billing.frequency`, `behavior`, `verificationMode`, `sdkMode`) `ConfigurationException` fırlatır. Tutarlar kanonik küçük birim string değerleridir (`"9900"` = 99,00 TRY, ücretsiz plan için `"0"`); gövdelerde, filtrelerde ve yanıtlarda denetlenir. Abonelik, plan, müşteri, kart, fatura ve denetim kaydı listeleri bir `SubscriptionPage` döndürür (`nextPageToken()` değerini `pageToken` olarak geri gönderin); katalog ve portal linki listeleri bir `RoutePage` döndürür. `SubscriptionValues.isActive`, `isTerminal` ve `portalUrl(link)` bir aboneliği ve barındırılan portal linkini okur; webhook'lar `SubscriptionEvents` içindedir.

## KYC

`client.kyc()` KYC, KYB ve kanıt oturumlarını oluşturur ve izler (`/api/v1/kyc/*`, yalnız erişim token'ı): `createSession`, `getSession`, `listSessions`, `setJurisdiction`, `setPrimaryDocument`, `uploadDocument`, `timeline`, `submit`, `manualReview`, `evidenceRequest` ve yönetim çağrıları `merchantPolicy`, `updateMerchantPolicy`, `rotateWebhookSecret`, `webhookOutbox`, `retryWebhookEvent` ve `erasureRequests`. `person` veya `company` map'i `customerType` ile birlikte `customer` olarak, `metadata` ise JSON metni `evidenceContextJson` olarak gönderilir. `listSessions` yalnız `limit` ve `cursor` alır (`pageSize` / `pageToken` aynı alanlar olarak okunur); başka bir filtre sessizce atılmaz, reddedilir. Sonucun belirleyicisi `kyc.*` webhook'udur (`KycEvents`); `KycValues.isSessionTerminal` ve `assuranceMeets` bir oturumu okur. Hata mesajları alan adlarını verir, değerleri asla vermez: belge görseli hiçbir hata mesajında görünmez.

## Kimlik

`client.identity()` cihaz ve hesap ilişkilendirme sinyallerini okur (`/api/v1/identity/live-sessions`, yalnız erişim token'ı): `analyzeSync`, `analyze`, `accountLinks`, `getSession`, `relatedSessions`, `timeline`, `topSources` ve `listSessions`. Karar değil; risk işaretleri, gerekçe kodları ve kimlik anlık görüntüsü döndürür. `IdentityClient.isDeviceShared(accountLinks[, threshold])` ve `linkReasons(accountLinks)` bir `accountLinks` yanıtını okur.

## Pazaryeri

`client.marketplace()` bir ödemeyi alt bayiler arasında böler ve alt bayileri yönetir. Bölünmüş tahsilatlar (`createCheckoutIntent`, `chargeMarketplaceCard2d`, `chargeMarketplaceCard3d` ve ham `chargeMarketplaceCard`) imzalı olarak ve isteğe bağlı `Idempotency-Key` ile `/api/v1/payments/marketplace/*` rotalarına gider. Her alt bayi için bir parça, `{subDealerId, amountMinor, scenario?}`, ve `multiSellerPolicy` (varsayılan `ALL_OR_NOTHING`) verin; parçaların toplamı `amount` değerine eşit olmalıdır. Kart tahsilatı `amount`, `currency`, `orderId`, `card` ve sipariş bilgilerini (`buyer`, `billingAddress`, en az bir sepet kalemi; `PHYSICAL` kalemde `shippingAddress`) ister; checkout intent de sipariş bilgilerini ister, orada `buyer.ip` gönderilmeyebilir. Kart verisi verildiği gibi gönderilir ve hiçbir hata mesajına konmaz. Token veya form içermeyen checkout yanıtı `LunixiApiException` fırlatır.

```java
client.marketplace().createCheckoutIntent(Map.of("amount", "100000", "currency", "TRY", "orderId", orderId),
        List.of(Map.of("subDealerId", "d_1", "amountMinor", "60000"),
                Map.of("subDealerId", "d_2", "amountMinor", "40000")),
        MarketplaceValues.MultiSellerPolicy.ALL_OR_NOTHING, persistedKey);
```

Bayi, komisyon, hakediş ödemesi ve denetim rotaları (`/api/v1/marketplace/*`, yalnız erişim token'ı) şunlardır: `createDealer`, `bulkCreateDealers`, `listDealers`, `getDealer`, `updateDealerStatus`, `onboardingToken` (`MarketplaceValues.onboardingUrl(link)`), `dealerApplication`, `submitDealerApplication`, `setDealerTerms`, `signDealerContract`, `activateDealer`, `recordDealerKycResult`, `payoutLedger`, `transactions`, `commissionRules`, `upsertCommissionRule`, `previewCommissionRule`, `resolveCommission`, `createPayout`, `markPayoutPaid`, `bulkMarkPayoutsPaid`, `auditLog` ve `verifyAuditLog`. Listeler bir `RoutePage` döndürür.

## Fraud ve hatalar

Kendi olaylarınız için `client.fraud().evaluate(...)`, mevcut Lunixi ödemesi için `decisions().latestForPayment(paymentId)` kullanın. Toplu olay kaydı 500 olayla sınırlıdır; HTTP 200 olsa da öğe hatalarını okuyun. İmleci değiştirmeden geri gönderin. [Fraud rehberi](https://docs.lunixi.com/tr/fraud/overview).

Tipli hata ve `code()` üzerinden karar verin. Zarfın metin `status` değeri `success/failure/partial` olabilir; 2xx içindeki failure da hata verir. Finansal işlemler 16–128 ASCII karakterli, önceden kalıcılaştırılmış idempotency anahtarı ister. Belirsiz yanıt sonrası yeni anahtar üretmeyin; aynı istek ve anahtarla mutabakat yapın.

## Derleme ve doğrulama

```sh
gradle --no-daemon -I tools/verification.init.gradle clean test spotbugsMain resolvedAuditDependencies centralBundle -PlunixiUnsigned
python3 tools/check-artifacts.py
python3 tools/audit-dependencies.py
python3 tools/check-secrets.py
```

Geçmiş doğrulama kaydında **109 test başarılıdır**; sınır ve iç içe değişmezlik regresyonları da bu sayıya dahildir. İmza, JSON, ham webhook, tam tutar, kalıcı rezervasyon/olay, rollback,23 saat oluşturma sınırı, ortam izolasyonu ve altı ayrı JVM'de aynı istek/anahtar sınanır. Testler canlı tahsilat yapmaz. Java 17 gerçek çalıştırma ve class sürümü 61 denetimiyle doğrulanır.

SpotBugs yalnız gerekçeli kesin sınıf/metot filtreleri kullanır. OSV çözümlenmiş test bağımlılıklarını tarar; POM/runtime kontrolü harici çalışma bağımlılığını yasaklar. Gitleaks tüm temiz kaynakta varsayılan kurallarla çalışır; yalnız birebir sentetik test eşleşmeleri hariçtir, eklenen sahte anahtarın yakalandığı ayrıca kanıtlanır. [Denetim ayrıntıları](docs/verification.md).
