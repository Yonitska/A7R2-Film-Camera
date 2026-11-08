package org.hongen.sonylut.transfer;

/** Immutable description of one transferable file. The camera never exposes
 * a filesystem path to the phone; the id is resolved against the camera's
 * current receipts/selection on every request. */
public final class MediaEntry {
    public final String id;
    public final String kind;
    public final String mode;
    public final String name;
    public final String lut;
    public final long time;
    public final long size;
    public final String etag;
    public final String mime;

    public MediaEntry(String id, String kind, String mode, String name, String lut,
                      long time, long size, String etag, String mime) {
        this.id = id == null ? "" : id;
        this.kind = kind == null ? "" : kind;
        this.mode = mode == null ? "" : mode;
        this.name = name == null ? "" : name;
        this.lut = lut == null ? "" : lut;
        this.time = time;
        this.size = size;
        this.etag = etag == null ? "" : etag;
        this.mime = mime == null ? "" : mime;
    }
}
