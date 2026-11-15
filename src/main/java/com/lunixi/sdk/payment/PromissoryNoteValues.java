package com.lunixi.sdk.payment;

import java.util.List;

/**
 * Value sets of Senetle Ödeme ({@link PaymentMethod#PROMISSORY_NOTE}): note status, document type, cancel
 * results and the languages of the printable document. Webhook types are in {@link PromissoryNoteEvents}.
 */
public final class PromissoryNoteValues {

    /** Most rows of a schedule (and most notes of a payment). */
    public static final int MAX_SCHEDULE_ITEMS = 24;

    private PromissoryNoteValues() {
    }

    /** Status of one note ({@code notes[].status}). */
    public static final class Status {
        public static final String ISSUED = "ISSUED";
        public static final String PAID = "PAID";
        public static final String PROTESTED = "PROTESTED";
        public static final String CANCELLED = "CANCELLED";
        public static final List<String> ALL = List.of(ISSUED, PAID, PROTESTED, CANCELLED);

        private Status() {
        }
    }

    /** Document type of the issued notes ({@code documentType}), from your provider connection settings. */
    public static final class DocumentType {
        public static final String PROMISSORY_NOTE = "PROMISSORY_NOTE";
        public static final String PAYMENT_ORDER = "PAYMENT_ORDER";
        public static final List<String> ALL = List.of(PROMISSORY_NOTE, PAYMENT_ORDER);

        private DocumentType() {
        }
    }

    /** Per-note result of {@link PaymentsClient#cancelPromissoryNotes} ({@code cancelResults[].result}). */
    public static final class CancelResult {
        public static final String CANCELLED = "CANCELLED";
        /** The note was already cancelled. */
        public static final String ALREADY_CLOSED = "ALREADY_CLOSED";
        /** The note was collected or protested; it cannot be cancelled. */
        public static final String NOT_CANCELLABLE = "NOT_CANCELLABLE";
        /** The provider did not cancel the note; {@code errorCode} tells whether to try again. */
        public static final String FAILED = "FAILED";
        public static final List<String> ALL = List.of(CANCELLED, ALREADY_CLOSED, NOT_CANCELLABLE, FAILED);

        private CancelResult() {
        }
    }

    /**
     * {@code cancelResults[].errorCode}: a Lunixi code, never the provider's; {@code null} for {@code CANCELLED}
     * and {@code ALREADY_CLOSED}.
     */
    public static final class CancelErrorCode {
        /** The note was collected or the provider does not cancel it ({@code NOT_CANCELLABLE}). */
        public static final String NOT_CANCELLABLE = "payment.promissory_note.not_cancellable";
        /** Temporary: the provider could not be reached; cancel again later ({@code FAILED}). */
        public static final String PROVIDER_UNAVAILABLE = "payment.promissory_note.provider_unavailable";
        /** Permanent: the provider refused the cancellation ({@code FAILED}). */
        public static final String CANCEL_REJECTED = "payment.promissory_note.cancel_rejected";
        public static final List<String> ALL = List.of(NOT_CANCELLABLE, PROVIDER_UNAVAILABLE, CANCEL_REJECTED);

        private CancelErrorCode() {
        }
    }

    /** Language of the printable notes ({@link PaymentsClient#downloadPromissoryNoteDocument}). */
    public static final class DocumentLocale {
        public static final String TR = "tr-TR";
        public static final String EN = "en-GB";
        public static final List<String> ALL = List.of(TR, EN);

        private DocumentLocale() {
        }
    }
}
