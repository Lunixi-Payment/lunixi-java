# Ödeme ve kalıcı yerel checkout

Java SDK 0.2.1, kimlik doğrulamalı ödeme API'leri ile kalıcı checkout rezervasyonu/mutabakatını içerir. JDK 17+ gerekir; harici çalışma zamanı kütüphanesi yoktur. [Kurulum](../README.tr.md) · [English](commerce.en.md) · [Çalışan Spring örneği](https://github.com/Lunixi-Payment/lunixi-spring-boot-starter/tree/main/samples/spring-boot) · [Spring rehberi](https://docs.lunixi.com/tr/sdks/spring-boot).

## Tam tutar ve kalıcı sipariş

Merchant, sipariş ID, tutar ve para birimini kimliği doğrulanmış uygulamanın kayıtlı siparişinden okuyun; tarayıcı alanlarını kaynak kabul etmeyin. `Money.minor("19.90",Money.exponent("TRY"))` 1990 döndürür. Float, negatif değer, fazla ondalık veya 9223372036854775807 üzeri tutar ağa çıkmadan reddedilir. JPY 0, KWD 3; desteklenen diğer para birimleri 2 ondalıktır. Eski `price/paidPrice` üst alanları reddedilir.

```java
import com.lunixi.sdk.payment.CommerceService;
import com.lunixi.sdk.payment.CheckoutSession;
import com.lunixi.sdk.payment.OrderSnapshot;
import com.lunixi.sdk.payment.Money;
import com.lunixi.sdk.payment.SecretBox;
import java.util.Base64;

SecretBox box = new SecretBox(Base64.getDecoder().decode(System.getenv("LUNIXI_STORAGE_KEY")));
CommerceService commerce = new CommerceService(client, nativeStore, box);
OrderSnapshot order = new OrderSnapshot(merchantId, storedOrderId,
        Money.minor(storedDecimalTotal, Money.exponent(storedCurrency)), storedCurrency);
CheckoutSession checkout = commerce.start(durableOrderKey, order,
        "https://shop.example/return/owned-order", false, false, "tr");
String browserToken = checkout.token(); // mağazanın kendi sayfasında gömülü form
// İsteğe bağlı yönlendirme: checkout.hostedUrl(), backend paymentPageUrl değerini aynen döndürür.
```

`client/nativeStore` sunucunun enjekte ettiği nesnelerdir. Sipariş anahtarı tenant/mağaza/sipariş bağlamında kalıcıdır. İlk `false` gömülü form, sonraki `false` anlık tahsilat anlamına gelir; açıkça kayıtlı eski yönlendirme tercihleri değiştirilmez. Token yalnız yerel oturum/müşteri/sipariş sahipliği, CSRF ve değişmemiş tutar kontrolünden sonra müşteriye verilir.

Rezervasyon finansal POST'tan önce commit edilir. Sipariş izi, ayarlar, gateway/ortam, özgün anahtar ve zaman saklanır; süreç yeniden açıldığında aynı gövde baytları/anahtar kullanılır. Token bağımsız 32 bayt AES-GCM anahtarıyla şifrelenir ve ihtiyatlı 840 saniye saklanır. Anahtarı DB/web kökü dışında ayrıca yedekleyin; checkout nesnesi veya URL'sini loglamayın.

Teyitsiz oluşturma 23 saat sonrasında finansal POST göndermez; geri alınmış saat de reddedilir. Süre dolması, timeout, ayar/tutar değişikliği veya yalnız `FAILED` yeni anahtar yetkisi vermez. GET mutabakatı açık kalır. Kayıp oluşturma yanıtı yalnız doğrulanmış ödeme olayı ve özgün opak deneme özeti/sipariş izini kanıtlayan kimlik doğrulamalı GET ile bağlanır; bağlı ödeme ID değiştirilemez. Eski gateway/ortam/credential bağlamını saklama süresince koruyun.

## Yerel transaction ve olay kutusu

`LedgerStore.transaction` süreçler arası özel sipariş kilidi almalı; rezervasyon, benzersiz olay kutusu, yerel sipariş/stok/history ve bildirim kuyruğunu **aynı bağlantı/transaction** ile tutmalıdır. `load/save` tam kaydı saklar. `claimEvent(merchantId,eventId,bodyHash)` ilkinde ekler, birebir tekrarda false döndürür, aynı kimlik/farklı imzalı gövdeyi reddeder. Hata halinde olay tüketimi ve yerel değişiklik birlikte geri alınır. Rezervasyon commit'ini ağ çağrısı sonrasına erteleyen dış transaction yasaktır. Bellek seti veya SDK'nın test dosya deposu üretim deposu değildir.

Yerel uygulama callback'i aynı kanıtla tekrar çalışınca stok/siparişi yeniden değiştirmemelidir; olaysız zamanlanmış GET kendi başına olay kutusu ID'si sağlamaz. Fraud/manual teslim blokelerini koruyun, eski bakiyeyle durumu geriye almayın; e-posta/sevkiyatı transaction içinde başlatmayın. Bildirimi aynı transaction'da kuyruğa alıp commit sonrasında gönderin. Spring starter açık şema kurulumu ve gerçek JDBC deposu, örnek uygulama ise yerel kullanıcı/CSRF/sipariş/stok/işlem kuyruğu sağlar.

## İmzalı webhook ve mutabakat

Ham gövdeyi JSON ayrıştırmadan önce alın. 1 MiB sınırı, geçersiz UTF-8 ve yinelenen imza başlığı reddi `Map` oluşmadan uygulanmalıdır; Spring `MerchantWebhooks` bu bayt/başlık kontrollerini yapar. Proxy özgün baytları korumalıdır.

```java
WebhookEvent event = client.webhooks().verifyMerchant(rawBody, headers, endpointSecret);
commerce.reconcile(durableOrderKey, event, rawBody, decision -> {
    nativeOrders.applyEvidenceInCurrentTransaction(decision);
});
```

`nativeOrders` uygulamanızın mevcut transaction'a katılan yerel kodudur. Ayrılmış siparişi kayıtlı ödeme bağından veya sıkı ayrıştırılmış **imzalı** sipariş adayından bulun; doğrulanmamış JSON ile bulmayın. v2 HMAC sabit zamanlı karşılaştırılır; varsayılan beş dakika, imzalı gövde ID/type/data ve başlık bağı doğrulanır. Anahtar geçişinde göndericinin birden çok v2 imzası desteklenir.

Commerce, ödeme GET veya olay kutusundan önce strict merchant `payment.*` olayı ve birebir doğrulanmış ham gövde özeti ister. Eski genel `verify`, uyumlu fraud gövdeleri içindir ve ödeme mutabakatında yeterli değildir. Zaman kontrolünü kapatmayın. İmza doğrulama tek başına olay tüketmez; kalıcı kutu tüketir. Yalnız yerel commit sonrası 2xx dönün; hatada hassas veri göstermeden tekrar edilebilir non-2xx verin.

Zamanlanmış mutabakat `reconcile(key,null,null,nativeApply)` kullanabilir; tarayıcı ödeme ID'sini kullanmayın. Kimlik doğrulamalı ödeme/çoklu kart sorgusu merchant, ödeme, sipariş izi, para birimi, sepet, vade farkı ve üst ödeme sonucunu denetler. Tam zarfı koruyun: bakiyeler `data` yanında `details` içinde olabilir. Tam doğru tahsilat ödenmiş olabilir; ön provizyon, kısmi tahsilat, eksik/telafi sürecindeki çoklu kart ve belirsiz/FAILED sonuç bekler. `isPaid()` yalnız paid için true'dur; yerel teslim blokesi yine geçerlidir. Vade farkını kayıtlı sepete ikinci kez eklemeyin.

## Finansal API kapsamı ve işlem kuyruğu

`client.payments()` şu gerçek uçları sunar: `createIntent` POST `/api/v1/payments/intents`; `get` GET `/api/v1/payments/{id}`; `capture/refund/voidPayment` POST `/api/v1/payments/{id}/capture|refund|void`; `getSplit` GET `/api/v1/payments/split/{id}`; `refundSplit` POST `/api/v1/payments/split/{id}/refund`.

Her finansal işlem 16–128 ASCII karakterli kalıcı anahtar ister. İşlem ID, tam istek/tutar, özgün anahtar/ortam ve zamanı **ağdan önce** kalıcılaştırın. Belirsiz/bekleyen işlem varken farklı ID'yi engelleyin, aynı işlemi yalnız güvenli süre içinde tekrarlayın ve 23 saat sınırını uygulama kuyruğunda koyun. **Commerce oluşturma rezervasyonu/mutabakatını yönetir; kalıcı tahsilat/iade/iptal kuyruğu değildir.** 2xx iş başarısı değildir; bakiye/serbest bırakmayı kimlik doğrulamalı GET ile teyit edin, yalnız gerçekten tahsil edilmiş tutarı iade edin, limit aşmayın. Tam uygulama yaşam döngüsü için Spring örneğinin kalıcı operatör kuyruğunu izleyin.

## Form ve tarayıcı dönüşü

Varsayılan mağaza içi gömülü formdur. İsteğe bağlı `checkout.hostedUrl()`, backend `paymentPageUrl` değerini aynen döndürür; gömülü token’dan adres üretilmez. Gömülü form yalnız `https://cdn.lunixi.com/lunixi-checkout.min.js` resmî paketiyle açılır; çalışan Spring örneği gerçek nesne/token/mount sözleşmesini ve CSP'yi gösterir. Yöntemler, yemek kartı, alışveriş kredisi, taksit, sigorta ve çoklu kart Lunixi form/panelinden yönetilir. Kart numarası/CVV sunucuda toplanmaz.

Yönlendirmeli/çoklu kart/3DS dönüşü **POST** kullanır. Bilgilendirici uç, SameSite=Lax çerezi gelmeyebileceği için gövdenin tamamını yok sayıp oturum/sipariş/DB/gateway'e erişmeden sabit göreli aynı-site GET adresine 303 dönmelidir. Uygulamada ve proxy'de gövde sınırı koyun. Sonraki GET normal sahiplik/mutabakatı uygular. Yalnız bilgilendirici dönüş ve imzalı webhook'u checkout CSRF dışında tutun; ödeme/yönetici kimlik kontrolünü gevşetmeyin, tarayıcı durum/ID alanını ödeme kanıtı saymayın.


Hosted URL ve token kalıcı denemede şifreli saklanır. Eski kayıtta paymentPageUrl yoksa gömülü akış sürer; yönlendirme yeni create, anahtar değişikliği veya adres tahmini yapmadan kapalı kalır. Özgün gövde özeti, origin ayarı ve anahtar korunur.
