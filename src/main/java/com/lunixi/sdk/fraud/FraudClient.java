package com.lunixi.sdk.fraud;

import java.util.Map;

import com.lunixi.sdk.exception.ConfigurationException;
import com.lunixi.sdk.http.Envelope;
import com.lunixi.sdk.internal.ApiClient;
import com.lunixi.sdk.internal.RequestOptions;

/**
 * The fraud surface.
 *
 * <p>Three calls make decisions and read them back:
 *
 * <pre>
 * evaluate                     POST /api/v1/fraud/decisions/evaluate
 * decisions().list(…)          GET  /api/v1/fraud/decision-logs
 * decisions().get(traceId)     GET  /api/v1/fraud/decision-logs/{traceId}
 * </pre>
 *
 * <p>and {@link #events()} submits transaction history:
 *
 * <pre>
 * events().record(…)           POST /api/v1/fraud/events/transactions
 * events().recordBatch(…)      POST /api/v1/fraud/events/transactions/batch
 * </pre>
 *
 * <h2>What this surface is, and is not</h2>
 * These are the calls the platform documents for machine callers. The console's
 * own routes — rule editing, flow administration, case queues, analytics — are
 * not part of it and are not reachable with an API credential: they answer
 * {@code 403} for one, by design rather than by omission. They are therefore
 * absent from this SDK, and will stay absent.
 *
 * <h2>Evaluate, or read a decision back?</h2>
 * For a payment taken through the platform, a decision is already made inside
 * authorisation — read it with
 * {@link FraudDecisionsClient#latestForPayment(String)} rather than asking a
 * second time. {@link #evaluate(EvaluateRequest)} is for screening events of
 * your own: a sign-up, a login, a withdrawal, a form of yours, or a card
 * payment you authorise through your own provider.
 *
 * <h2>The path prefix is part of the path</h2>
 * {@code /api/v1} is not optional. The gateway applies it globally and fraud is
 * not exempt from it, so a path written without the prefix answers {@code 404}
 * for the whole surface rather than for one call.
 */
public final class FraudClient {

    /** The base path of the fraud surface, global prefix included. */
    static final String BASE_PATH = "/api/v1/fraud";

    private final ApiClient api;
    private final FraudEventsClient events;
    private final FraudDecisionsClient decisions;

    public FraudClient(ApiClient api) {
        this.api = api;
        this.events = new FraudEventsClient(api, BASE_PATH);
        this.decisions = new FraudDecisionsClient(api, BASE_PATH);
    }

    /**
     * Evaluates a transaction and returns the decision.
     *
     * <p>The call is authenticated by the access token alone; it carries no
     * per-request signature. Event submission does — see {@link #events()}.
     *
     * @param request the request
     * @return the decision
     * @throws ConfigurationException when the request holds a field the surface
     *                                does not accept
     */
    public FraudDecision evaluate(EvaluateRequest request) {
        if (request == null) {
            throw new ConfigurationException("Değerlendirme isteği boş olamaz / the evaluate request is required.");
        }
        Map<String, Object> response = api.request(
                "POST", BASE_PATH + "/decisions/evaluate", request.toMap(), RequestOptions.none());
        return new FraudDecision(Envelope.data(response));
    }

    /** @return the event submission client */
    public FraudEventsClient events() {
        return events;
    }

    /** @return the decision record client */
    public FraudDecisionsClient decisions() {
        return decisions;
    }
}
