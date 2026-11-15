package com.lunixi.sdk.payment;

import java.math.BigInteger;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.lunixi.sdk.exception.ConfigurationException;

/**
 * Plan rules of Senetle Ödeme ({@link PaymentMethod#PROMISSORY_NOTE}), checked before sending.
 *
 * <p>The plan is either a schedule ({@code [{amount, dueDate}]}) or a note count ({@code noteCount}), never both.
 * Checked here: positive canonical minor-unit amounts adding up to the payment amount, {@code YYYY-MM-DD}
 * calendar dates in non-descending order and at most 24 rows. That a due date lies far enough in the future and
 * that the note count is within the limits of your provider connection is checked by the gateway. SDK internals.
 */
final class PromissoryNotePlan {

    private static final Pattern DUE_DATE = Pattern.compile("([0-9]{4})-([0-9]{2})-([0-9]{2})");
    private static final Pattern NOTE_COUNT = Pattern.compile("[1-9][0-9]?");
    private static final Pattern SERIAL_NUMBER = Pattern.compile("[A-Za-z0-9-]{4,64}");
    private static final int MAX = PromissoryNoteValues.MAX_SCHEDULE_ITEMS;

    private PromissoryNotePlan() {
    }

    static boolean isCalendarDate(String value) {
        Matcher match = DUE_DATE.matcher(value);
        if (!match.matches()) {
            return false;
        }
        try {
            LocalDate.of(Integer.parseInt(match.group(1)), Integer.parseInt(match.group(2)),
                    Integer.parseInt(match.group(3)));
            return true;
        } catch (DateTimeException notADay) {
            return false;
        }
    }

    /** A schedule: 1–24 rows {@code {amount, dueDate}} in non-descending date order. Returns a copy. */
    static List<Map<String, Object>> requireSchedule(Object value, String path) {
        if (!(value instanceof List<?> rows) || rows.isEmpty()) {
            throw new ConfigurationException(path + " must be a non-empty list of {amount, dueDate} rows.");
        }
        if (rows.size() > MAX) {
            throw new ConfigurationException(path + " can have at most " + MAX + " rows; got " + rows.size() + ".");
        }
        List<Map<String, Object>> copy = new ArrayList<>(rows.size());
        String previousDueDate = null;
        for (int index = 0; index < rows.size(); index++) {
            String rowPath = path + "[" + index + "]";
            if (!(rows.get(index) instanceof Map<?, ?> row)) {
                throw new ConfigurationException(rowPath + " must be a map {amount, dueDate}.");
            }
            List<String> unknown = new ArrayList<>();
            for (Object key : row.keySet()) {
                if (!"amount".equals(key) && !"dueDate".equals(key)) {
                    unknown.add(String.valueOf(key));
                }
            }
            if (!unknown.isEmpty()) {
                throw new ConfigurationException(rowPath + " has unknown field(s): " + String.join(", ", unknown)
                        + ". A row is {amount, dueDate}.");
            }
            String amount = PaymentRules.positiveMinor(row.get("amount"), rowPath + ".amount");
            if (!(row.get("dueDate") instanceof String dueDate) || !isCalendarDate(dueDate)) {
                throw new ConfigurationException(rowPath + ".dueDate must be a calendar date in YYYY-MM-DD form; got "
                        + PaymentMethod.quote(row.get("dueDate")) + ".");
            }
            if (previousDueDate != null && dueDate.compareTo(previousDueDate) < 0) {
                throw new ConfigurationException(rowPath + ".dueDate must not be earlier than the previous row ("
                        + previousDueDate + "); list the notes in due-date order.");
            }
            previousDueDate = dueDate;
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("amount", amount);
            entry.put("dueDate", dueDate);
            copy.add(entry);
        }
        return copy;
    }

    /** The rows of a schedule must add up to the payment amount. */
    static void assertMatchesAmount(List<Map<String, Object>> schedule, Object amount, String path) {
        BigInteger total = BigInteger.ZERO;
        for (Map<String, Object> row : schedule) {
            total = total.add(new BigInteger((String) row.get("amount")));
        }
        BigInteger expected = new BigInteger(PaymentRules.positiveMinor(amount, "amount"));
        if (!total.equals(expected)) {
            throw new ConfigurationException(path + " adds up to " + total + "; it must equal amount (" + expected
                    + ").");
        }
    }

    /** Number of notes: an integer 1–24 or its digit string; returned as the string that is sent. */
    static String requireNoteCount(Object value, String path) {
        String text = value instanceof Integer || value instanceof Long ? String.valueOf(value)
                : value instanceof String string ? string : null;
        if (text == null || !NOTE_COUNT.matcher(text).matches() || Integer.parseInt(text) > MAX) {
            throw new ConfigurationException(path + " must be a number of notes from 1 to " + MAX + "; got "
                    + PaymentMethod.quote(value) + ".");
        }
        return text;
    }

    /** The plan of a {@code promissoryNote} block: exactly one of {@code schedule} or {@code noteCount}. */
    static void assertBlock(Map<?, ?> block, Object amount, String path) {
        boolean hasSchedule = block.get("schedule") != null;
        boolean hasNoteCount = block.get("noteCount") != null;
        if (hasSchedule && hasNoteCount) {
            throw new ConfigurationException(path + ": send either schedule or noteCount, not both.");
        }
        if (!hasSchedule && !hasNoteCount) {
            throw new ConfigurationException(path + " needs the plan: a schedule ([{amount, dueDate}]) or a "
                    + "noteCount.");
        }
        if (hasNoteCount) {
            requireNoteCount(block.get("noteCount"), path + ".noteCount");
            return;
        }
        List<Map<String, Object>> schedule = requireSchedule(block.get("schedule"), path + ".schedule");
        if (amount != null) {
            assertMatchesAmount(schedule, amount, path + ".schedule");
        }
    }

    /** Serial numbers to cancel: 1–24 note serial numbers. */
    static List<String> requireSerialNumbers(List<String> values) {
        if (values == null || values.isEmpty() || values.size() > MAX) {
            throw new ConfigurationException("serialNumbers must be a list of 1 to " + MAX + " note serial numbers "
                    + "(from payments().getPromissoryNotes()); leave it out to cancel every open note.");
        }
        for (String value : values) {
            if (value == null || !SERIAL_NUMBER.matcher(value).matches()) {
                throw new ConfigurationException("serialNumbers contains an invalid serial number "
                        + PaymentMethod.quote(value) + " (letters, digits and -, 4 to 64 characters).");
            }
        }
        return List.copyOf(values);
    }

    static String requireDocumentLocale(String value) {
        if (value == null || !PromissoryNoteValues.DocumentLocale.ALL.contains(value)) {
            throw new ConfigurationException("locale must be one of "
                    + String.join(", ", PromissoryNoteValues.DocumentLocale.ALL) + "; got " + PaymentMethod.quote(value)
                    + ".");
        }
        return value;
    }
}
