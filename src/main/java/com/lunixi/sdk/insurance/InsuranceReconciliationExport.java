package com.lunixi.sdk.insurance;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The reconciliation report as CSV ({@link InsuranceClient#exportReconciliation}): UTF-8 with BOM, one line per
 * policy, amounts in minor units. A file holds at most {@link InsuranceValues#EXPORT_ORDER_CAP} orders; when it was
 * cut, {@link #truncated()} is true and {@link #nextCursor()} continues it (pass it as the {@code cursor} filter).
 * {@link #aborted()} is true when the file ends with the gateway's abort line: it is incomplete, request it again.
 */
public final class InsuranceReconciliationExport {

    private static final Pattern TRUNCATED_LINE = Pattern.compile("^# truncated=true orderCap=(\\d+) nextCursor=(\\S+)\\s*$");
    private static final Pattern ABORTED_LINE = Pattern.compile("^# export-aborted=true\\s*$");

    private final String contentType;
    private final byte[] bytes;
    private final boolean truncated;
    private final String nextCursor;
    private final boolean aborted;

    /**
     * @param contentType the answer's {@code Content-Type} ({@code text/csv; charset=utf-8}), or {@code null}
     * @param bytes       the file bytes
     */
    public InsuranceReconciliationExport(String contentType, byte[] bytes) {
        this.contentType = contentType;
        this.bytes = bytes == null ? new byte[0] : bytes.clone();
        String last = lastLine(new String(this.bytes, StandardCharsets.UTF_8));
        Matcher cut = TRUNCATED_LINE.matcher(last);
        this.truncated = cut.matches();
        this.nextCursor = truncated ? cut.group(2) : null;
        this.aborted = ABORTED_LINE.matcher(last).matches();
    }

    private static String lastLine(String text) {
        String[] lines = text.split("\\r?\\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            if (!lines[i].isEmpty()) {
                return lines[i];
            }
        }
        return "";
    }

    /** @return the {@code Content-Type} of the answer */
    public String contentType() {
        return contentType;
    }

    /** @return a copy of the CSV bytes (with the BOM) */
    public byte[] bytes() {
        return bytes.clone();
    }

    /** @return {@code true} when the file was cut at the order cap; continue with {@link #nextCursor()} */
    public boolean truncated() {
        return truncated;
    }

    /** @return the cursor that continues a cut file */
    public Optional<String> nextCursor() {
        return Optional.ofNullable(nextCursor);
    }

    /** @return {@code true} when the export stopped mid-stream; the file is incomplete */
    public boolean aborted() {
        return aborted;
    }

    @Override
    public String toString() {
        return "InsuranceReconciliationExport[contentType=" + contentType + ", size=" + bytes.length
                + ", truncated=" + truncated + ", aborted=" + aborted + "]";
    }
}
