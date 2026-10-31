package com.lunixi.sdk.webhook;

import java.util.Map;
import java.util.Optional;

import com.lunixi.sdk.internal.ResponseMaps;

/** A webhook whose signature has been verified. */
public final class WebhookEvent {

    private final String id;
    private final String type;
    private final String timestamp;
    private final Map<String, Object> data;
    private final Map<String, Object> envelope;
    private final String rawBodyHash;
    private final boolean merchantEnvelope;

    WebhookEvent(String id, String type, String timestamp,
                 Map<String, Object> data, Map<String, Object> envelope) {
        this(id, type, timestamp, data, envelope, "", false);
    }

    WebhookEvent(String id, String type, String timestamp,
                 Map<String, Object> data, Map<String, Object> envelope, String rawBodyHash, boolean merchantEnvelope) {
        this.id = id;
        this.type = type;
        this.timestamp = timestamp;
        this.data = ResponseMaps.immutableCopy(data);
        this.envelope = ResponseMaps.immutableCopy(envelope);
        this.rawBodyHash = rawBodyHash;
        this.merchantEnvelope = merchantEnvelope;
    }

    /**
     * The event id. Keep it: deliveries can repeat, and the id is what lets you
     * recognise a repeat instead of acting twice.
     *
     * @return the event id
     */
    public String id() {
        return id;
    }

    /**
     * The event type, for example one of the constants on
     * {@link com.lunixi.sdk.fraud.FraudEvents}.
     *
     * @return the event type
     */
    public String type() {
        return type;
    }

    /** @return the signature timestamp */
    public String timestamp() {
        return timestamp;
    }

    /** @return the event payload */
    public Map<String, Object> data() {
        return data;
    }

    /** @return the whole delivery envelope */
    public Map<String, Object> envelope() {
        return envelope;
    }

    /** @return SHA-256 of the exact HMAC-authenticated UTF-8 body */
    public String rawBodyHash() { return rawBodyHash; }

    /** @return whether strict current merchant envelope checks were performed */
    public boolean isMerchantEnvelope() { return merchantEnvelope; }

    /**
     * Reads a field of the payload.
     *
     * @param key the field name
     * @return the value, when present
     */
    public Optional<Object> get(String key) {
        return Optional.ofNullable(data.get(key));
    }
}
