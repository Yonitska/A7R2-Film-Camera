package org.hongen.sonylut.transfer;

import java.util.Locale;

/** Single-range HTTP byte ranges. Multiple ranges are rejected by the server
 * with 416 because the companion only ever resumes one file at a time. */
public final class RangeSpec {
    public final long start;
    public final long end;

    private RangeSpec(long start, long end) { this.start = start; this.end = end; }

    public long length() { return end - start + 1L; }

    /** Returns null when no bytes range was requested. Throws when a bytes
     * range is malformed or unsatisfiable, which the server maps to 416. */
    public static RangeSpec parse(String header, long total) {
        if (header == null) return null;
        String value = header.trim();
        String prefix = "bytes=";
        if (value.length() < prefix.length()
                || !value.substring(0, prefix.length()).equalsIgnoreCase(prefix)) return null;
        String spec = value.substring(prefix.length()).trim();
        if (spec.length() == 0 || spec.indexOf(',') >= 0)
            throw new IllegalArgumentException("only one byte range is supported");
        int dash = spec.indexOf('-');
        if (dash < 0 || spec.indexOf('-', dash + 1) >= 0)
            throw new IllegalArgumentException("malformed byte range");
        String left = spec.substring(0, dash).trim();
        String right = spec.substring(dash + 1).trim();
        if (total <= 0) throw new IllegalArgumentException("empty file");
        long start, end;
        try {
            if (left.length() == 0) {
                long suffix = Long.parseLong(right);
                if (suffix <= 0) throw new IllegalArgumentException("bad suffix length");
                start = suffix >= total ? 0 : total - suffix;
                end = total - 1;
            } else {
                start = Long.parseLong(left);
                if (start < 0 || start >= total)
                    throw new IllegalArgumentException("range starts past the file");
                if (right.length() == 0) end = total - 1;
                else {
                    end = Long.parseLong(right);
                    if (end < start) throw new IllegalArgumentException("range end before start");
                    if (end >= total) end = total - 1;
                }
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("bad byte range number", e);
        }
        return new RangeSpec(start, end);
    }

    /** If-Range uses a strong validator. An ETag must match exactly; a date
     * is accepted only when the file has not changed since that date. */
    public static boolean matchesIfRange(String value, String etag, long lastModified) {
        if (value == null) return true;
        String validator = value.trim();
        if (validator.length() == 0) return false;
        if (validator.charAt(0) == '"') return validator.equals(etag);
        long date = HttpDate.parse(validator);
        if (date < 0) return false;
        return lastModified / 1000L <= date / 1000L;
    }

    public static String contentRange(long start, long end, long total) {
        return String.format(Locale.US, "bytes %d-%d/%d", start, end, total);
    }

    public static String unsatisfiedRange(long total) {
        return String.format(Locale.US, "bytes */%d", total);
    }
}
