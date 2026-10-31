# Payments and durable native checkout

Java SDK 0.2.0 exposes authenticated payment APIs plus checkout reservation/reconciliation. JDK 17+; no external runtime libraries. [Install/configure](../README.md). [Türkçe](commerce.tr.md). For a real transaction-capable implementation use the [Spring Boot Starter sample](https://github.com/Lunixi-Payment/lunixi-spring-boot-starter/tree/main/samples/spring-boot) and [Spring guide](https://docs.lunixi.com/sdks/spring-boot).

## Server order, exact money and reservation

Read merchant identity, order ID, amount and currency from the application's authenticated persisted order. Never accept them from browser form fields. `Money.minor("19.90", Money.exponent("TRY"))` returns 1990; floats, negative values, excess fractional precision and values above 9007199254740991 fail before a request. JPY has 0 decimals, KWD 3; documented supported remaining currencies use 2. Deprecated top-level `price/paidPrice` are rejected.

```java
import com.lunixi.sdk.payment.CheckoutSession;
import com.lunixi.sdk.payment.CommerceService;
import com.lunixi.sdk.payment.LedgerStore;
import com.lunixi.sdk.payment.Money;
import com.lunixi.sdk.payment.OrderSnapshot;
import com.lunixi.sdk.payment.SecretBox;
import java.util.Base64;

// client and nativeStore are dependency-injected server objects.
SecretBox box = new SecretBox(Base64.getDecoder().decode(System.getenv("LUNIXI_STORAGE_KEY")));
CommerceService commerce = new CommerceService(client, nativeStore, box);
OrderSnapshot order = new OrderSnapshot(merchantId, storedOrderId,
        Money.minor(storedDecimalTotal, Money.exponent(storedCurrency)), storedCurrency);
CheckoutSession checkout = commerce.start(durableOrderKey, order,
        "https://shop.example/return/owned-order", true, false, "en");
// hosted=true; preauth=false requests immediate capture. Return only to the owner.
String redirect = checkout.hostedUrl();
```

`durableOrderKey` is stable, namespaced by tenant/store/order; never a random browser input. Before calling `start`, enforce native session/customer/order ownership, CSRF and unchanged native totals. The reservation is committed before the financial POST. Descriptor, snapshot, gateway/environment context, original key and creation time are retained; retries after a process restart preserve request byte order and key. Tokens are encrypted with a separate 32-byte server key and cached conservatively for 840 seconds. Keep the key outside DB/web root, back it up separately and do not log checkout objects/URLs.

After 23 hours, an unconfirmed create cannot issue another financial POST because gateway idempotency retention is bounded. Clock rollback also fails closed. Token expiry, timeout, changed settings/snapshot or `FAILED` alone never authorizes another key. Authenticated GET reconciliation remains available. Recover a lost create response only from a verified payment event and authenticated payment GET proving the original opaque attempt hash and order fingerprint; a bound payment ID cannot be replaced. Keep original gateway/environment/credential context available for retained attempts.

## Native durable ledger and atomic application

`LedgerStore.transaction(orderKey, operation)` must acquire an exclusive cross-process order lock and use the **same native transaction/connection** for attempt state, unique event inbox, stock/order/history and notification outbox. `load/save` store the entire record. `claimEvent(merchantId,eventId,bodyHash)` inserts once, returns false for an identical replay and rejects reused IDs with different authenticated bytes. Exceptions must roll back inbox and native changes together. Reject ambient transaction patterns that would postpone reservation commit until after the network call. No process-memory set or SDK test-only file ledger is sufficient production storage.

Implement the native callback so repeated unchanged evidence has no duplicate side effects; scheduled GET reconciliation can run without an event and therefore does not itself provide an inbox event ID. Preserve manual/fraud fulfillment holds, avoid stale balance downgrades, and queue notifications transactionally for delivery only after commit. Never send mail or invoke shipping inside the payment transaction.

The Spring starter supplies a JDBC ledger for its documented supported databases, explicit schema install and transaction integration. Its sample has actual auth accounts, CSRF, native order/stock tables and durable operations; adapt those lifecycle rules rather than copying the SDK test fixture.

## Strict merchant webhooks and reconciliation

Capture a bounded raw body before JSON parsing. Enforce at most 1MiB, reject invalid UTF-8 and duplicate signature headers before constructing a `Map`; the Spring `MerchantWebhooks` adapter performs those byte/header checks. Preserve exact bytes through the proxy.

```java
import com.lunixi.sdk.webhook.WebhookEvent;

WebhookEvent event = client.webhooks().verifyMerchant(rawBody, headers, endpointSecret);
// Locate a reserved order only from trusted persisted binding or a strictly
// parsed signed order candidate. Never locate from unauthenticated JSON.
commerce.reconcile(durableOrderKey, event, rawBody, decision -> {
    nativeOrders.applyEvidenceInCurrentTransaction(decision);
});
```

The callback object above is application-owned native code. Verification checks v2 HMAC-SHA256 in constant time, freshness (default five minutes), signed body ID/type/data and matching delivery headers. The verifier accepts multiple v2 signatures during sender secret rotation. `CommerceService` additionally requires a strict `payment.*` merchant event and its exact authenticated raw-body hash before any payment GET/inbox access. Legacy generic `verify` remains available for compatible fraud payloads and is deliberately insufficient for commerce reconciliation. Do not disable timestamp checks. Verification alone does not consume an event; the persistent native inbox does.

Only acknowledge delivery after native commit. Return a retryable non-2xx on transactional failure, without leaking raw bodies, signing material or tokens. For scheduled reconciliation call `reconcile(key,null,null,nativeApply)` with no browser-supplied payment ID. Authenticated payment/split reads verify merchant, order fingerprint, currency, exact basket/fee and aggregate settlement. Preserve the full response envelope: financial balances may be in `details` beside `data`.

Full exact capture with valid evidence may be paid; preauthorization, partial capture, incomplete/compensating multi-card parents and unknown/FAILED outcomes remain on hold/review. `isPaid()` is true only for `paid`; native holds still prevent fulfillment. Refund state does not prove a new sale. A fee is recorded separately, never silently added to the persisted basket twice.

## Direct payment APIs and financial operations

| Method | Route |
|---|---|
| `createIntent(request,key)` | POST `/api/v1/payments/intents` |
| `get(paymentId)` | GET `/api/v1/payments/{id}` |
| `capture(id,amount,key)` | POST `/api/v1/payments/{id}/capture` |
| `refund(id,amount,reason,key)` | POST `/api/v1/payments/{id}/refund` |
| `voidPayment(id,key)` | POST `/api/v1/payments/{id}/void` |
| `getSplit(splitId)` | GET `/api/v1/payments/split/{id}` |
| `refundSplit(splitId,amount,reason,key)` | POST `/api/v1/payments/split/{id}/refund` |

These low-level methods require stable 16–128-character ASCII operation keys for every financial mutation. Persist each operation ID, exact body/amount, original key/context and timestamp **before** network dispatch. Retry the same operation only while safe; block fresh IDs while another outcome is pending/uncertain and impose a conservative 23-hour replay fence yourself. `CommerceService` manages create reservations and reconciliation; it is **not** a durable capture/refund/void operation queue. A 2xx API response is not business success: confirm requested settlement/release with authenticated GET, refund only actually captured balances, and never overcapture/refund. Follow the Spring sample's persistent operator queue for a full application lifecycle.

## Hosted/embedded browser and return routes

The hosted form is `checkout.hostedUrl()`. The official embedded bundle is `https://cdn.lunixi.com/lunixi-checkout.min.js`; the runnable Spring sample demonstrates its actual object/token/mount contract with CSP and no raw cards. Available meal cards, shopping credit, installments, insurance and multi-card methods stay controlled by Lunixi merchant settings; do not duplicate filters in the store unless explicitly supported by a documented API.

Hosted/split/3DS callbacks use browser **POST**. That informational route must ignore the complete body and return 303 to a fixed relative same-origin GET without session/order/DB/gateway access, because cross-site POST may lack SameSite=Lax cookies. Bound declared bodies and enforce a matching proxy wire limit. The GET restores normal native ownership checks and authenticated reconciliation. Exempt only the informational return and signed webhook from checkout CSRF; never trust browser status/IDs or relax checkout/admin authentication.
