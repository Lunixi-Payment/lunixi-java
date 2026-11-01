<p align="center"><a href="https://lunixi.com"><img src="https://raw.githubusercontent.com/Lunixi-Payment/lunixi-java/main/.github/assets/lunixi-logo.svg" alt="Lunixi" height="64"></a></p>

# Lunixi Java SDK

**1.0.0 is prepared and unreleased.** The examples below use the breaking canonical money-string API. Use the reviewed local source for this version; historical 0.x registry artifacts do not implement it. See [migration](MIGRATION-1.0.md).

[![CI](https://github.com/Lunixi-Payment/lunixi-java/actions/workflows/ci.yml/badge.svg)](https://github.com/Lunixi-Payment/lunixi-java/actions/workflows/ci.yml) · [Türkçe](README.tr.md) · [Documentation](https://docs.lunixi.com/sdks/java) · [Changelog](CHANGELOG.md) · [Security](SECURITY.md)

**1.0.0** adds payment intents, capture/refund/void, split payment reads/refunds and durable checkout reconciliation to the existing fraud SDK. Requires **JDK 17+**; **zero external runtime dependencies**. MIT license.

| Surface | Included |
|---|---|
| Authentication/transport | Ed25519 PKCS#8/seed keys, canonical JSON signing, cached bearer tokens, bounded HTTPS transport, no redirects, typed errors |
| Payments | `client.payments().createIntent/get/capture/refund/voidPayment/getSplit/refundSplit` |
| Durable checkout | `CommerceService`, native `LedgerStore` SPI, exact `Money`, immutable `OrderSnapshot`, authenticated `PaymentEvidence`, AES-GCM `SecretBox` |
| Fraud | Typed evaluation, decision records/cursor paging, event recording/batches |
| Webhooks | v2 HMAC, freshness, signed envelope binding; `verifyMerchant` for payment reconciliation |

Subscription, identity, sanctions and KYC do not have complete typed clients in this release. Follow their documented REST contracts separately. This SDK is a server package; it does not collect PAN/CVV or implement a storefront/admin application.



Historical release record: **Java SDK 0.2.1 is published on [Maven Central](https://repo.maven.apache.org/maven2/com/lunixi/lunixi-java-sdk/0.2.1/lunixi-java-sdk-0.2.1.pom).** Its release files were verified byte-for-byte against the signed bundle on 2026-10-31.

## Use the unreleased source

Use the reviewed Java source as a Gradle composite build with JDK 17+ and Gradle 9.7.1:

```groovy
// settings.gradle
includeBuild('/absolute/path/to/reviewed/entegrasyon/java')
```

```groovy
// build.gradle
repositories { mavenCentral() }
dependencies { implementation 'com.lunixi:lunixi-java-sdk:1.0.0' }
```

Run `gradle test` in that source checkout for local verification. Publication to Maven Central requires a separate authorized release.

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

The prepared 1.0.0 release is unreleased. See [the money-string migration](MIGRATION-1.0.md).
