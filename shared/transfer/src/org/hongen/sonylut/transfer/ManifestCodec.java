package org.hongen.sonylut.transfer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** A tiny, fixed-schema manifest. It deliberately avoids JSON so the exact
 * same code runs on Android 4.1, on a modern phone, and in host tests. */
public final class ManifestCodec {
    private static final int FIELDS = 9;

    private ManifestCodec() {}

    public static String encode(List<MediaEntry> entries) {
        StringBuilder out = new StringBuilder();
        out.append(TransferProtocol.MANIFEST_HEADER).append('\n');
        if (entries == null) return out.toString();
        for (MediaEntry e : entries) {
            if (e == null) continue;
            append(out, e.id).append('\t');
            append(out, e.kind).append('\t');
            append(out, e.mode).append('\t');
            append(out, e.name).append('\t');
            append(out, e.lut).append('\t');
            out.append(e.time).append('\t');
            out.append(e.size).append('\t');
            append(out, e.etag).append('\t');
            append(out, e.mime).append('\n');
        }
        return out.toString();
    }

    public static List<MediaEntry> decode(String text) throws IOException {
        if (text == null) throw new IOException("empty manifest");
        String[] lines = text.split("\n", -1);
        if (lines.length == 0 || !TransferProtocol.MANIFEST_HEADER.equals(lines[0]))
            throw new IOException("bad manifest header");
        List<MediaEntry> entries = new ArrayList<MediaEntry>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.length() == 0) continue;
            if (entries.size() >= TransferProtocol.MAX_ENTRIES)
                throw new IOException("manifest too large");
            String[] cells = line.split("\t", -1);
            if (cells.length != FIELDS) throw new IOException("bad manifest field count");
            String id = unescape(cells[0]);
            if (!TransferProtocol.safeId(id)) throw new IOException("bad media id");
            String kind = unescape(cells[1]);
            String mode = unescape(cells[2]);
            boolean photoKind = "photo".equals(kind);
            boolean videoKind = "video".equals(kind);
            if (!photoKind && !videoKind) throw new IOException("bad media kind");
            if (photoKind ? !TransferProtocol.photoMode(mode) : !TransferProtocol.videoMode(mode))
                throw new IOException("bad media mode");
            String name = unescape(cells[3]);
            String lut = unescape(cells[4]);
            if (name.length() > 255 || lut.length() > 200)
                throw new IOException("manifest text too long");
            long time, size;
            try {
                time = Long.parseLong(cells[5]);
                size = Long.parseLong(cells[6]);
            } catch (NumberFormatException e) {
                throw new IOException("bad manifest number", e);
            }
            if (time < 0 || size < 0) throw new IOException("negative manifest value");
            String etag = unescape(cells[7]);
            String mime = unescape(cells[8]);
            if (etag.length() > 128 || mime.length() > 100)
                throw new IOException("manifest header value too long");
            entries.add(new MediaEntry(id, kind, mode, name, lut, time, size, etag, mime));
        }
        return entries;
    }

    private static StringBuilder append(StringBuilder out, String value) {
        if (value == null) return out;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\') out.append("\\\\");
            else if (c == '\t') out.append("\\t");
            else if (c == '\n') out.append("\\n");
            else if (c == '\r') out.append("\\r");
            else out.append(c);
        }
        return out;
    }

    private static String unescape(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != '\\' || i + 1 >= value.length()) {
                out.append(c);
                continue;
            }
            char next = value.charAt(++i);
            if (next == '\\') out.append('\\');
            else if (next == 't') out.append('\t');
            else if (next == 'n') out.append('\n');
            else if (next == 'r') out.append('\r');
            else out.append('\\').append(next);
        }
        return out.toString();
    }
}
