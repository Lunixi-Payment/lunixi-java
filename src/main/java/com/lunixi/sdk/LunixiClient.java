package com.lunixi.sdk;

import com.lunixi.sdk.auth.Ed25519Signer;
import com.lunixi.sdk.auth.TokenManager;
import com.lunixi.sdk.fraud.FraudClient;
import com.lunixi.sdk.internal.ApiClient;
import com.lunixi.sdk.payment.PaymentsClient;
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

    private LunixiClient(LunixiConfig config) {
        this.config = config;
        Ed25519Signer signer = new Ed25519Signer(config.privateKey());
        this.tokens = new TokenManager(config, signer);
        ApiClient api = new ApiClient(config, signer, tokens);
        this.fraud = new FraudClient(api);
        this.payments = new PaymentsClient(api);
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

    /** @return checkout intents, authenticated settlement reads and payment mutations */
    public PaymentsClient payments() { return payments; }

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
