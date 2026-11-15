package com.lunixi.sdk;

import com.lunixi.sdk.auth.Ed25519Signer;
import com.lunixi.sdk.auth.TokenManager;
import com.lunixi.sdk.bankconnect.BankConnectClient;
import com.lunixi.sdk.fraud.FraudClient;
import com.lunixi.sdk.identity.IdentityClient;
import com.lunixi.sdk.insurance.InsuranceClient;
import com.lunixi.sdk.internal.ApiClient;
import com.lunixi.sdk.kyc.KycClient;
import com.lunixi.sdk.marketplace.MarketplaceClient;
import com.lunixi.sdk.payment.PaymentsClient;
import com.lunixi.sdk.paymentlink.PaymentLinksClient;
import com.lunixi.sdk.physicalpos.PhysicalPosClient;
import com.lunixi.sdk.physicalpos.PhysicalPosManagementClient;
import com.lunixi.sdk.reference.ReferenceClient;
import com.lunixi.sdk.sanction.SanctionClient;
import com.lunixi.sdk.subscription.SubscriptionsClient;
import com.lunixi.sdk.wallet.WalletClient;
import com.lunixi.sdk.webhook.WebhookVerifier;

/**
 * The entry point of the SDK.
 *
 * <pre>
 * LunixiClient client = LunixiClient.create(LunixiConfig.builder()
 *         .baseUrl("https://api.example.com")
 *         .keyId(System.getenv("LUNIXI_KEY_ID"))
 *         .privateKey(System.getenv("LUNIXI_PRIVATE_KEY"))
 *         .build());
 *
 * FraudDecision decision = client.fraud().evaluate(EvaluateRequest.create()
 *         .channel(FraudTaxonomy.Channel.STANDALONE_API)
 *         .transactionRef("order-2026-0001")
 *         .payment(Map.of("amount", 25000, "currency", "TRY")));
 * </pre>
 *
 * <p>Build one client and keep it. It is immutable, safe to share between
 * threads, and holds the access token cache: a client created per request would
 * sign and fetch a fresh token every time, which is an extra round trip on every
 * call and needless signing traffic.
 *
 * <p>This release covers authentication, request signing, the response
 * envelope, paging, errors, webhook verification and the fraud surface. Other
 * product surfaces are not in it yet; the request layer is the same for all of
 * them, so adding one does not change what is here.
 */
public final class LunixiClient {

    private final LunixiConfig config;
    private final TokenManager tokens;
    private final FraudClient fraud;
    private final WebhookVerifier webhooks;
    private final PaymentsClient payments;
    private final PaymentLinksClient paymentLinks;
    private final PhysicalPosClient physicalPos;
    private final PhysicalPosManagementClient physicalPosManagement;
    private final ReferenceClient reference;
    private final WalletClient wallet;
    private final BankConnectClient bankConnect;
    private final SanctionClient sanction;
    private final InsuranceClient insurance;
    private final SubscriptionsClient subscriptions;
    private final KycClient kyc;
    private final IdentityClient identity;
    private final MarketplaceClient marketplace;

    private LunixiClient(LunixiConfig config) {
        this.config = config;
        Ed25519Signer signer = new Ed25519Signer(config.privateKey());
        this.tokens = new TokenManager(config, signer);
        ApiClient api = new ApiClient(config, signer, tokens);
        this.fraud = new FraudClient(api);
        this.payments = new PaymentsClient(api);
        this.paymentLinks = new PaymentLinksClient(api, config.transport(), config.timeout());
        this.physicalPos = new PhysicalPosClient(api);
        this.physicalPosManagement = new PhysicalPosManagementClient(api);
        this.reference = new ReferenceClient(api);
        this.wallet = new WalletClient(api);
        this.bankConnect = new BankConnectClient(api);
        this.sanction = new SanctionClient(api);
        this.insurance = new InsuranceClient(api);
        this.subscriptions = new SubscriptionsClient(api);
        this.kyc = new KycClient(api);
        this.identity = new IdentityClient(api);
        this.marketplace = new MarketplaceClient(api);
        this.webhooks = new WebhookVerifier();
    }

    /**
     * Creates a client.
     *
     * @param config the configuration
     * @return the client
     * @throws com.lunixi.sdk.exception.SignatureException when the private key
     *                                                     cannot be read — raised
     *                                                     here rather than on the
     *                                                     first call
     */
    public static LunixiClient create(LunixiConfig config) {
        return new LunixiClient(config);
    }

    /** @return the fraud surface */
    public FraudClient fraud() {
        return fraud;
    }

    /**
     * @return checkout intents, marketplace intents, payment methods and direct payments, actions, promissory notes,
     *         split payments, bank transfer exceptions, BIN and installment lookups, stored cards, analytics,
     *         authenticated settlement reads and payment mutations
     */
    public PaymentsClient payments() { return payments; }

    /**
     * @return payment links: create, list, read, update, pause / resume / deactivate, send, QR code, attempts,
     *         payments and link images
     */
    public PaymentLinksClient paymentLinks() { return paymentLinks; }

    /** @return reference data: the Türkiye address dictionary (provinces, districts, neighborhoods) */
    public ReferenceClient reference() {
        return reference;
    }

    /** @return physical POS payments, transactions, reports and end-of-day results */
    public PhysicalPosClient physicalPos() {
        return physicalPos;
    }

    /** @return physical POS management: overview, catalog, stores, registers, terminals and devices */
    public PhysicalPosManagementClient physicalPosManagement() {
        return physicalPosManagement;
    }

    /**
     * @return the wallet: programs, end users, wallets and accounts, deposits, corporate accounts, fees, transfers,
     *         withdrawals, operations, top-ups, payments, QR codes and bulk payouts
     */
    public WalletClient wallet() {
        return wallet;
    }

    /**
     * @return bank accounts: banks, connections, accounts, balances and transactions, with outgoing transfers
     *         ({@code transfers()}) and currency exchange ({@code fx()})
     */
    public BankConnectClient bankConnect() {
        return bankConnect;
    }

    /** @return sanction screening, ongoing monitoring, entity intelligence and the deny list (read) */
    public SanctionClient sanction() {
        return sanction;
    }

    /**
     * @return insurance: products, reference codes, quotes, orders, premium collection and refunds, policy files,
     *         reconciliation and leads
     */
    public InsuranceClient insurance() {
        return insurance;
    }

    /**
     * @return subscriptions, with plans, customers, saved cards, invoices, the customer portal, catalog products,
     *         features, add-ons and metered usage
     */
    public SubscriptionsClient subscriptions() {
        return subscriptions;
    }

    /** @return KYC / KYB / evidence sessions, the merchant KYC policy, KYC webhook deliveries and erasure requests */
    public KycClient kyc() {
        return kyc;
    }

    /** @return identity signals: live-session analysis, linked accounts, sessions and risky sources */
    public IdentityClient identity() {
        return identity;
    }

    /** @return marketplace split charges, sub-dealers, commissions, payouts and the audit log */
    public MarketplaceClient marketplace() {
        return marketplace;
    }

    /**
     * The webhook verifier.
     *
     * <p>Verification needs only the endpoint's secret, so it does not depend on
     * this client's credentials; {@code new WebhookVerifier()} works just as
     * well in a process that receives webhooks and makes no calls.
     *
     * @return the verifier
     */
    public WebhookVerifier webhooks() {
        return webhooks;
    }

    /**
     * The access token manager.
     *
     * <p>Exposed because a deployment sometimes needs to warm the token cache at
     * start-up, or drop it after rotating a key.
     *
     * @return the token manager
     */
    public TokenManager tokens() {
        return tokens;
    }

    /** @return the configuration this client was built with */
    public LunixiConfig config() {
        return config;
    }
}
