package org.hongen.sonylut.transfer;

import java.util.Locale;

/** Wire contract shared by the camera server and the phone client. */
public final class TransferProtocol {
    public static final int VERSION = 1;
    public static final String TOKEN_HEADER = "X-SonyLut-Token";
    public static final String SERVER_NAME = "SonyLut/1";
    public static final String PING_PATH = "/v1/ping";
    public static final String PHOTOS_PATH = "/v1/photos";
    public static final String VIDEOS_PATH = "/v1/videos";
    public static final String MEDIA_PREFIX = "/v1/media/";
    public static final String MANIFEST_HEADER = "SONYLUT-MANIFEST 1";
    public static final String MANIFEST_MIME = "text/plain; charset=utf-8";
    public static final String PLAIN_MIME = "text/plain; charset=utf-8";
    public static final int DEFAULT_PORT = 8080;
    public static final int MAX_ENTRIES = 10000;
    public static final int MAX_ID_LENGTH = 64;

    private TransferProtocol() {}

    public static boolean safeId(String id) {
        if (id == null || id.length() == 0 || id.length() > MAX_ID_LENGTH) return false;
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '_' || c == '-') continue;
            return false;
        }
        return true;
    }

    public static String photoId(String receiptId) {
        return "photo-" + receiptId;
    }

    /** Strong validator for one fixed file revision. The server also uses it
     * for If-Range; the phone compares the manifest copy before resuming. */
    public static String etag(long lastModified, long size) {
        return "\"" + Long.toHexString(lastModified) + "-" + Long.toHexString(size) + "\"";
    }

    public static String mediaMime(String name) {
        String lower = name == null ? "" : name.toLowerCase(Locale.US);
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".mp4")) return "video/mp4";
        if (lower.endsWith(".mts") || lower.endsWith(".m2ts")) return "video/mp2t";
        return "application/octet-stream";
    }

    public static boolean photoMode(String mode) {
        return "lut".equals(mode) || "recipe".equals(mode);
    }

    public static boolean videoMode(String mode) {
        return "video".equals(mode);
    }
}
