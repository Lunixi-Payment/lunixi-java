<p align="center"><a href="https://lunixi.com"><img src="https://raw.githubusercontent.com/Lunixi-Payment/lunixi-java/main/.github/assets/lunixi-logo.svg" alt="Lunixi" height="64"></a></p>

# Lunixi Java SDK

[English](README.md) · [Dokümantasyon](https://docs.lunixi.com/tr/sdks/java) · [Sürüm geçmişi](CHANGELOG.md) · [Güvenlik](SECURITY.md)

**0.2.0**, mevcut fraud istemcisine ödeme oluşturma, tahsilat/iade/iptal, çoklu kart sorgulama/iadesi ve kalıcı ödeme mutabakatı ekler. **JDK 17+**, **sıfır harici çalışma zamanı bağımlılığı**, MIT lisansı.

| Yüzey | Kapsam |
|---|---|
| Kimlik doğrulama/taşıma | Ed25519 PKCS#8/tohum anahtar, kanonik JSON/imza, token önbelleği, sınırlı HTTPS, yönlendirme yasağı, tipli hatalar |
| Ödemeler | `createIntent/get/capture/refund/voidPayment/getSplit/refundSplit` |
| Kalıcı checkout | `CommerceService`, yerel `LedgerStore`, `Money`, `OrderSnapshot`, `PaymentEvidence`, AES-GCM `SecretBox` |
| Fraud | Karar sorgulama, imleçli karar kayıtları, tekli/toplu olay kaydı |
| Webhook | v2 HMAC, zaman/imzalı gövde doğrulama; ödeme için `verifyMerchant` |

Abonelik, kimlik, yaptırım ve KYC ürünlerinin tüm tipli istemcileri bu sürümde bulunmaz; ilgili REST sözleşmesini ayrıca izleyin. SDK sunucuda çalışır; kart numarası/CVV toplamaz, hazır mağaza/yönetici uygulaması değildir.

## Kaynaktan kurulum

Koordinat **`com.lunixi:lunixi-java-sdk:0.2.0`**. GitHub etiketi, yerel Maven kurulumu veya imzasız paket **Maven Central yayını anlamına gelmez**. Bu koordinat Central'da yayımlanana kadar **Gradle 9.7.1** ile kaynak etiketi derleyip yerel Maven deposundan kullanın:

```sh
git clone --depth 1 --branch v0.2.0 https://github.com/Lunixi-Payment/lunixi-java.git
cd lunixi-java
gradle build publishToMavenLocal -PlunixiUnsigned
```

```groovy
repositories { mavenLocal(); mavenCentral() }
dependencies { implementation 'com.lunixi:lunixi-java-sdk:0.2.0' }
```

Maven uygulamalarında aynı groupId/artifactId/version kullanılır; varsayılan yerel depo otomatik aranır. CI herhangi bir kayıt deposuna yükleme yapmaz. İmzasız `centralBundle` yalnız paket yapısını doğrular; Central yayını ayrı imzalı sürüm işlemi gerektirir.

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

Yönlendirmeli form `https://pay.lunixi.com/payment/<token>` adresinde açılır. Gömülü akış resmî Lunixi tarayıcı paketini kullanır. Açık ödeme yöntemleri, yemek kartı, alışveriş kredisi, taksit, sigorta ve çoklu kart Lunixi paneli/formundan yönetilir. Token yalnız yerel oturum/müşteri/sipariş sahipliği ve CSRF kontrolü sonrasında döner. Sağlayıcının tarayıcı POST dönüşü bilgilendirici 303 ile sahiplik kontrolü olan GET'e geçer; başarı alanı ödeme kanıtı değildir.

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

Güncel kaynakta **109 test başarılıdır**; sınır ve iç içe değişmezlik regresyonları da bu sayıya dahildir. İmza, JSON, ham webhook, tam tutar, kalıcı rezervasyon/olay, rollback,23 saat oluşturma sınırı, ortam izolasyonu ve altı ayrı JVM'de aynı istek/anahtar sınanır. Testler canlı tahsilat yapmaz. Java 17 gerçek çalıştırma ve class sürümü 61 denetimiyle doğrulanır.

SpotBugs yalnız gerekçeli kesin sınıf/metot filtreleri kullanır. OSV çözümlenmiş test bağımlılıklarını tarar; POM/runtime kontrolü harici çalışma bağımlılığını yasaklar. Gitleaks tüm temiz kaynakta varsayılan kurallarla çalışır; yalnız birebir sentetik test eşleşmeleri hariçtir, eklenen sahte anahtarın yakalandığı ayrıca kanıtlanır. [Denetim ayrıntıları](docs/verification.md).
