package org.hongen.sonylut.transfer;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/** Immutable version of the transfer manifest. */
public final class MediaManifest {
    private final List<MediaEntry> entries;

    public MediaManifest(List<MediaEntry> entries) {
        this.entries = entries == null ? Collections.<MediaEntry>emptyList()
                : Collections.unmodifiableList(entries);
    }

    public List<MediaEntry> entries() { return entries; }

    public String encode() { return ManifestCodec.encode(entries); }

    public static MediaManifest decode(String text) throws IOException {
        return new MediaManifest(ManifestCodec.decode(text));
    }
}
