package org.hongen.sonylut.transfer;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** RFC 1123 dates for Last-Modified / If-Range. */
public final class HttpDate {
    private static final String PATTERN = "EEE, dd MMM yyyy HH:mm:ss zzz";

    private HttpDate() {}

    public static String format(long millis) {
        SimpleDateFormat format = formatter();
        return format.format(new Date(millis));
    }

    /** Returns epoch millis, or -1 when the value is not an HTTP date. */
    public static long parse(String value) {
        if (value == null || value.length() == 0) return -1L;
        try {
            return formatter().parse(value.trim()).getTime();
        } catch (ParseException e) {
            return -1L;
        }
    }

    private static SimpleDateFormat formatter() {
        SimpleDateFormat format = new SimpleDateFormat(PATTERN, Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("GMT"));
        format.setLenient(false);
        return format;
    }
}
