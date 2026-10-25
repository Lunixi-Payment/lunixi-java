package com.lunixi.sdk.fraud;

import java.util.Optional;

/**
 * The outcomes a decision can carry.
 *
 * <p>For a payment taken through the platform the outcome is applied for you:
 * {@code ALLOW} proceeds, {@code FORCE_3D} forces cardholder authentication,
 * {@code REVIEW} holds the transaction for a step-up or a human look, and
 * {@code BLOCK} declines it. When you ask for a decision about your own flow,
 * applying the outcome is yours to do.
 */
public enum FraudDecisionValue {

    /** Proceed. */
    ALLOW("allow"),
    /** Hold for a step-up or a human look. */
    REVIEW("review"),
    /** Decline. */
    BLOCK("block"),
    /** Proceed, but require cardholder authentication. */
    FORCE_3D("force_3d");

    private final String wireValue;

    FraudDecisionValue(String wireValue) {
        this.wireValue = wireValue;
    }

    /** @return the value as it appears on the wire */
    public String wireValue() {
        return wireValue;
    }

    /**
     * Reads a wire value.
     *
     * @param value the value from the response
     * @return the matching outcome, or empty for a value this SDK release does
     *         not know. An unrecognised outcome is reported as empty rather than
     *         guessed at: treating an unknown outcome as "allow" would let a
     *         future, stricter outcome through.
     */
    public static Optional<FraudDecisionValue> fromWire(String value) {
        if (value == null) {
            return Optional.empty();
        }
        for (FraudDecisionValue candidate : values()) {
            if (candidate.wireValue.equals(value)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }
}
