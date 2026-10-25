package com.lunixi.sdk.fraud;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.lunixi.sdk.exception.ConfigurationException;
import com.lunixi.sdk.http.Envelope;
import com.lunixi.sdk.internal.ApiClient;
import com.lunixi.sdk.internal.RequestOptions;

/**
 * Transaction event submission — the history that counting rules read.
 *
 * <h2>These two routes are signed per request</h2>
 * Unlike the decision surface, which the bearer token alone authenticates,
 * every event call carries its own Ed25519 signature ({@code X-Key-Id} /
 * {@code X-Date} / {@code X-Nonce} / {@code Digest} / {@code X-Signature}).
 * The signature is what binds an event to your account, so no value in the body
 * can attribute it to someone else: the account is taken from the authenticated
 * key and the body is never consulted for it.
 *
 * <h2>Why the batch limit is also checked locally</h2>
 * An oversized batch is rejected <i>in full</i> — not trimmed. Checking the
 * size before the call means an over-long feed costs you an immediate,
 * readable error instead of a round trip that silently drops every event in it.
 */
public final class FraudEventsClient {

    /**
     * The hard upper bound on events per batch call.
     *
     * <p>Split a longer feed into chunks of at most this many.
     */
    public static final int BATCH_LIMIT = 500;

    private final ApiClient api;
    private final String basePath;

    FraudEventsClient(ApiClient api, String basePath) {
        this.api = api;
        this.basePath = basePath;
    }

    /**
     * Records one event.
     *
     * <p>Required keys: {@code eventId}, {@code eventType},
     * {@code subjectType}, {@code occurredAt}. An {@code eventType} outside the
     * known vocabulary is a {@code 400} rather than a row nothing will ever
     * read.
     *
     * <p>Resending the same {@code eventId} is safe: the second call is
     * recognised as a duplicate and reports
     * {@link FraudEventResult#isDeduplicated()}.
     *
     * @param event the event
     * @return the result
     */
    public FraudEventResult record(Map<String, Object> event) {
        return record(event, null);
    }

    /**
     * Records one event.
     *
     * @param event          the event
     * @param idempotencyKey an idempotency key, which also makes the call
     *                       eligible for an automatic retry on a transport
     *                       failure
     * @return the result
     */
    public FraudEventResult record(Map<String, Object> event, String idempotencyKey) {
        if (event == null || event.isEmpty()) {
            throw new ConfigurationException("Olay boş olamaz / the event must not be empty.");
        }
        Map<String, Object> response = api.request("POST", basePath + "/events/transactions", event,
                RequestOptions.builder().stepUp(true).idempotencyKey(idempotencyKey).build());
        return new FraudEventResult(Envelope.data(response));
    }

    /**
     * Records up to {@value #BATCH_LIMIT} events in one signed call.
     *
     * <p>The batch is processed event by event and <b>can succeed partially</b>
     * — read {@link FraudEventBatchResult#failures()} rather than trusting the
     * status code.
     *
     * @param events the events
     * @return the batch result
     * @throws ConfigurationException when the list is empty or longer than
     *                                {@value #BATCH_LIMIT}
     */
    public FraudEventBatchResult recordBatch(List<Map<String, Object>> events) {
        return recordBatch(events, null);
    }

    /**
     * Records up to {@value #BATCH_LIMIT} events in one signed call.
     *
     * @param events         the events
     * @param idempotencyKey an idempotency key for the call
     * @return the batch result
     * @throws ConfigurationException when the list is empty or longer than
     *                                {@value #BATCH_LIMIT}
     */
    public FraudEventBatchResult recordBatch(List<Map<String, Object>> events, String idempotencyKey) {
        if (events == null || events.isEmpty()) {
            throw new ConfigurationException("Olay partisi boş olamaz / the event batch must not be empty.");
        }
        if (events.size() > BATCH_LIMIT) {
            throw new ConfigurationException(
                    "Parti " + events.size() + " olay taşıyor; istek başına sınır " + BATCH_LIMIT
                            + ". Besleme parçalara bölünmelidir — sınırın üstündeki parti TAMAMEN reddedilir. "
                            + "/ The batch holds " + events.size() + " events; the per-request limit is "
                            + BATCH_LIMIT + ". Split the feed: an oversized batch is rejected in full.");
        }
        List<Map<String, Object>> body = new ArrayList<>(events);
        Map<String, Object> response = api.request("POST", basePath + "/events/transactions/batch", body,
                RequestOptions.builder().stepUp(true).idempotencyKey(idempotencyKey).build());
        return new FraudEventBatchResult(Envelope.data(response));
    }
}
