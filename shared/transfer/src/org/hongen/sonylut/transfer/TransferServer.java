package org.hongen.sonylut.transfer;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/** Minimal HTTP/1.1 server for the camera's Wi-Fi Direct group.
 *
 * It deliberately implements only the endpoints in {@link TransferProtocol}.
 * There is no directory listing, no path parameter and no directory traversal
 * surface: the phone sends an opaque media id, which the camera Source resolves
 * against its own receipts. Token authentication precedes any file lookup. */
public final class TransferServer {
    public interface Source {
        List<MediaEntry> photos() throws Exception;
        List<MediaEntry> videos() throws Exception;
        /** Returns the file for a known id, or null. Must validate containment. */
        File resolve(String id) throws Exception;
    }

    public interface Listener { void log(String name, String detail); }

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final int MAX_REQUEST_LINE = 2048;
    private static final int MAX_HEADER_LINE = 2048;
    private static final int MAX_HEADERS = 64;
    private static final int WORKERS = 3;

    private final String token;
    private final Source source;
    private final String bindHost;
    private final String[] allowedPrefixes;
    private final int requestedPort;
    private final Listener listener;
    private final Set<Socket> clients = Collections.synchronizedSet(new java.util.HashSet<Socket>());

    private volatile boolean running;
    private ServerSocket server;
    private ExecutorService workers;
    private Thread acceptThread;
    private int boundPort;

    public TransferServer(String token, Source source, String bindHost, String allowedPrefix,
                          int requestedPort, Listener listener) {
        if (token == null || token.length() == 0) throw new IllegalArgumentException("token required");
        if (source == null) throw new IllegalArgumentException("source required");
        this.token = token;
        this.source = source;
        this.bindHost = emptyToNull(bindHost);
        this.allowedPrefixes = prefixes(allowedPrefix);
        this.requestedPort = requestedPort < 0 ? TransferProtocol.DEFAULT_PORT : requestedPort;
        this.listener = listener;
    }

    public synchronized int start() throws IOException {
        if (running) return boundPort;
        workers = Executors.newFixedThreadPool(WORKERS, new ThreadFactory() {
            private int number;
            public synchronized Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "sonylut-http-" + (++number));
                thread.setDaemon(true);
                return thread;
            }
        });
        IOException failure = null;
        if (bindHost != null) {
            try {
                server = openSocket(bindHost);
            } catch (IOException error) {
                failure = error;
                log("bind_p2p_failed", error.getClass().getSimpleName());
            }
        }
        if (server == null) {
            try {
                server = openSocket(null);
            } catch (IOException error) {
                if (failure == null) failure = error;
                workers.shutdownNow();
                workers = null;
                throw failure;
            }
        }
        boundPort = server.getLocalPort();
        running = true;
        acceptThread = new Thread(new Runnable() { public void run() { acceptLoop(); } }, "sonylut-http-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
        log("server_started", "port=" + boundPort + " p2p=" + (bindHost == null ? "wildcard" : bindHost)
                + " source_guard=" + (allowedPrefixes.length == 0 ? "off" : "on"));
        return boundPort;
    }

    public synchronized void stop() {
        if (!running && server == null) return;
        running = false;
        ServerSocket local = server;
        server = null;
        if (local != null) try { local.close(); } catch (IOException ignored) {}
        synchronized (clients) {
            for (Socket socket : new ArrayList<Socket>(clients)) try { socket.close(); } catch (IOException ignored) {}
            clients.clear();
        }
        ExecutorService pool = workers;
        workers = null;
        if (pool != null) {
            pool.shutdownNow();
            try { pool.awaitTermination(400, TimeUnit.MILLISECONDS); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        Thread thread = acceptThread;
        acceptThread = null;
        if (thread != null && thread != Thread.currentThread()) {
            try { thread.join(400); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        log("server_stopped", "port=" + boundPort);
        boundPort = 0;
    }

    public boolean isRunning() { return running; }
    public int port() { return boundPort; }

    private ServerSocket openSocket(String host) throws IOException {
        IOException last = null;
        int count = requestedPort == 0 ? 1 : 4;
        for (int i = 0; i < count; i++) {
            int port = requestedPort == 0 ? 0 : requestedPort + i;
            ServerSocket candidate = null;
            try {
                InetAddress address = host == null ? null : InetAddress.getByName(host);
                candidate = new ServerSocket();
                candidate.setReuseAddress(true);
                candidate.bind(new InetSocketAddress(address, port), 8);
                candidate.setSoTimeout(800);
                return candidate;
            } catch (IOException error) {
                last = error;
                if (candidate != null) try { candidate.close(); } catch (IOException ignored) {}
            }
        }
        throw last == null ? new IOException("cannot bind server") : last;
    }

    private void acceptLoop() {
        while (running) {
            try {
                final Socket socket = server.accept();
                clients.add(socket);
                try {
                    socket.setSoTimeout(15000);
                    workers.execute(new Runnable() { public void run() {
                        try { handle(socket); }
                        catch (Throwable error) { log("request_failed", error.getClass().getSimpleName()); }
                        finally {
                            clients.remove(socket);
                            try { socket.close(); } catch (IOException ignored) {}
                        }
                    }});
                } catch (RejectedExecutionException error) {
                    clients.remove(socket);
                    try { socket.close(); } catch (IOException ignored) {}
                }
            } catch (SocketTimeoutException ignored) {
                // Periodic wakeup so stop() need not wait for a connection.
            } catch (Throwable error) {
                if (running) log("accept_failed", error.getClass().getSimpleName());
                if (!running) break;
            }
        }
    }

    private void handle(Socket socket) throws IOException {
        InputStream in = new BufferedInputStream(socket.getInputStream(), 8192);
        OutputStream out = new BufferedOutputStream(socket.getOutputStream(), 32768);
        String requestLine;
        Map<String,String> headers;
        try {
            requestLine = readLine(in, MAX_REQUEST_LINE);
            if (requestLine == null) return;
            String[] parts = requestLine.split(" ", 3);
            if (parts.length != 3) { sendError(out, 400, "Bad request", false); return; }
            String method = parts[0].toUpperCase(Locale.US);
            String target = parts[1];
            if (!(method.equals("GET") || method.equals("HEAD"))) {
                sendError(out, 405, "Method not allowed", false);
                return;
            }
            boolean head = method.equals("HEAD");
            headers = readHeaders(in);
            if (headers == null) { sendError(out, 400, "Bad headers", head); return; }

            if (!sourceAllowed(socket.getInetAddress().getHostAddress())) {
                sendError(out, 403, "Forbidden", head);
                return;
            }
            if (!authorized(headers.get(TransferProtocol.TOKEN_HEADER.toLowerCase(Locale.US)))) {
                sendError(out, 401, "Unauthorized", head);
                return;
            }
            dispatch(out, head, method, target, headers);
        } catch (IOException error) {
            try { sendError(out, 400, "Bad request", false); } catch (IOException ignored) {}
        }
    }

    private void dispatch(OutputStream out, boolean head, String method, String target,
                           Map<String,String> headers) throws IOException {
        String path = target;
        int query = path.indexOf('?');
        if (query >= 0) path = path.substring(0, query);
        if (path.indexOf("..") >= 0 || path.indexOf('%') >= 0 || path.indexOf('\\') >= 0) {
            sendError(out, 400, "Bad path", head);
            return;
        }

        if (path.equals(TransferProtocol.PING_PATH)) {
            sendSimple(out, 200, TransferProtocol.PLAIN_MIME,
                    "{\"protocol\":1,\"model\":\"ILCE-7RM2\"}\n", head, null);
            return;
        }
        if (path.equals(TransferProtocol.PHOTOS_PATH)) {
            sendManifestSafe(out, head, true);
            return;
        }
        if (path.equals(TransferProtocol.VIDEOS_PATH)) {
            sendManifestSafe(out, head, false);
            return;
        }
        if (path.startsWith(TransferProtocol.MEDIA_PREFIX)) {
            String id = path.substring(TransferProtocol.MEDIA_PREFIX.length());
            if (!TransferProtocol.safeId(id) || id.indexOf('/') >= 0) {
                sendError(out, 404, "Not found", head);
                return;
            }
            sendMedia(out, head, id, headers);
            return;
        }
        sendError(out, 404, "Not found", head);
    }

    private void sendManifestSafe(OutputStream out, boolean head, boolean photos) throws IOException {
        List<MediaEntry> entries;
        try {
            entries = photos ? source.photos() : source.videos();
        } catch (Exception error) {
            log(photos ? "photos_failed" : "videos_failed", error.getClass().getSimpleName());
            sendError(out, 503, "Catalog unavailable", head);
            return;
        }
        List<MediaEntry> safe = entries == null ? new ArrayList<MediaEntry>() : entries;
        byte[] body = ManifestCodec.encode(safe).getBytes(UTF8);
        Map<String,String> extra = new LinkedHashMap<String,String>();
        extra.put("Cache-Control", "no-store");
        sendSimple(out, 200, TransferProtocol.MANIFEST_MIME, body, head, extra);
    }

    private void sendMedia(OutputStream out, boolean head, String id, Map<String,String> headers) throws IOException {
        File file = null;
        try {
            file = source.resolve(id);
        } catch (Exception error) {
            log("resolve_failed", error.getClass().getSimpleName());
            sendError(out, 503, "Media unavailable", head);
            return;
        }
        if (file == null) { sendError(out, 404, "Not found", head); return; }
        try { file = file.getCanonicalFile(); } catch (IOException ignored) {}
        if (!file.isFile() || !file.canRead()) { sendError(out, 404, "Not found", head); return; }
        long total = file.length();
        String etag = TransferProtocol.etag(file.lastModified(), total);
        long modified = file.lastModified();
        String rangeValue = headers.get("range");
        String ifRange = headers.get("if-range");
        if (rangeValue != null && ifRange != null
                && !RangeSpec.matchesIfRange(ifRange, etag, modified)) rangeValue = null;
        RangeSpec range = null;
        try {
            range = RangeSpec.parse(rangeValue, total);
        } catch (IllegalArgumentException error) {
            Map<String,String> extra = new LinkedHashMap<String,String>();
            extra.put("Content-Range", RangeSpec.unsatisfiedRange(total));
            extra.put("Accept-Ranges", "bytes");
            sendSimple(out, 416, TransferProtocol.PLAIN_MIME, "Range not satisfiable\n".getBytes(UTF8), head, extra);
            return;
        }
        long start, length;
        int status;
        Map<String,String> extra = new LinkedHashMap<String,String>();
        extra.put("Accept-Ranges", "bytes");
        extra.put("ETag", etag);
        extra.put("Last-Modified", HttpDate.format(modified));
        extra.put("Cache-Control", "no-store");
        if (range == null) {
            start = 0; length = total; status = 200;
        } else {
            start = range.start; length = range.length(); status = 206;
            extra.put("Content-Range", RangeSpec.contentRange(range.start, range.end, total));
        }
        sendHeaders(out, status, TransferProtocol.mediaMime(file.getName()), length, extra);
        if (!head && length > 0) {
            FileInputStream input = new FileInputStream(file);
            try {
                skipFully(input, start);
                byte[] buffer = new byte[65536];
                long remaining = length;
                while (remaining > 0) {
                    int read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                    if (read < 0) throw new IOException("file shortened during transfer");
                    out.write(buffer, 0, read);
                    remaining -= read;
                }
                out.flush();
            } finally {
                try { input.close(); } catch (IOException ignored) {}
            }
        }
    }

    private void sendError(OutputStream out, int status, String text, boolean head) throws IOException {
        Map<String,String> extra = new LinkedHashMap<String,String>();
        extra.put("Cache-Control", "no-store");
        sendSimple(out, status, TransferProtocol.PLAIN_MIME, (text + "\n").getBytes(UTF8), head, extra);
    }

    private void sendSimple(OutputStream out, int status, String contentType,
                            String body, boolean head, Map<String,String> extra) throws IOException {
        sendSimple(out, status, contentType, body == null ? new byte[0] : body.getBytes(UTF8), head, extra);
    }

    private void sendSimple(OutputStream out, int status, String contentType,
                            byte[] body, boolean head, Map<String,String> extra) throws IOException {
        sendHeaders(out, status, contentType, body.length, extra);
        if (!head && body.length > 0) {
            out.write(body);
            out.flush();
        }
    }

    private void sendHeaders(OutputStream out, int status, String contentType,
                             long length, Map<String,String> extra) throws IOException {
        StringBuilder head = new StringBuilder(256);
        head.append("HTTP/1.1 ").append(status).append(' ').append(reason(status)).append("\r\n");
        head.append("Date: ").append(HttpDate.format(System.currentTimeMillis())).append("\r\n");
        head.append("Server: ").append(TransferProtocol.SERVER_NAME).append("\r\n");
        head.append("Content-Length: ").append(length).append("\r\n");
        if (contentType != null) head.append("Content-Type: ").append(contentType).append("\r\n");
        if (extra != null) for (Map.Entry<String,String> entry : extra.entrySet())
            head.append(entry.getKey()).append(": ").append(entry.getValue()).append("\r\n");
        head.append("Connection: close\r\n\r\n");
        out.write(head.toString().getBytes(UTF8));
        out.flush();
    }

    private static String reason(int status) {
        switch (status) {
            case 200: return "OK";
            case 206: return "Partial Content";
            case 400: return "Bad Request";
            case 401: return "Unauthorized";
            case 403: return "Forbidden";
            case 404: return "Not Found";
            case 405: return "Method Not Allowed";
            case 416: return "Range Not Satisfiable";
            case 503: return "Service Unavailable";
            default: return "Error";
        }
    }

    private boolean authorized(String supplied) {
        if (supplied == null) return false;
        byte[] a, b;
        try {
            a = supplied.getBytes(UTF8);
            b = token.getBytes(UTF8);
        } catch (Throwable error) {
            return false;
        }
        int difference = a.length ^ b.length;
        int count = Math.max(a.length, b.length);
        for (int i = 0; i < count; i++) {
            byte av = i < a.length ? a[i] : 0;
            byte bv = i < b.length ? b[i] : 0;
            difference |= av ^ bv;
        }
        return difference == 0;
    }

    private Map<String,String> readHeaders(InputStream in) throws IOException {
        Map<String,String> result = new HashMap<String,String>();
        int total = 0;
        for (int i = 0; i < MAX_HEADERS; i++) {
            String line = readLine(in, MAX_HEADER_LINE);
            if (line == null) return null;
            if (line.length() == 0) return result;
            total += line.length() + 2;
            if (total > 8192) return null;
            int colon = line.indexOf(':');
            if (colon <= 0) return null;
            String name = line.substring(0, colon).trim().toLowerCase(Locale.US);
            String value = line.substring(colon + 1).trim();
            if (name.length() == 0) return null;
            result.put(name, value);
        }
        return null;
    }

    private static String readLine(InputStream in, int max) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream(128);
        while (true) {
            int value = in.read();
            if (value < 0) return line.size() == 0 ? null : line.toString("UTF-8");
            if (value == '\n') {
                byte[] bytes = line.toByteArray();
                int length = bytes.length;
                if (length > 0 && bytes[length - 1] == '\r') length--;
                return new String(bytes, 0, length, UTF8);
            }
            if (line.size() >= max) throw new IOException("line too long");
            line.write(value);
        }
    }

    private static void skipFully(InputStream in, long bytes) throws IOException {
        long remaining = bytes;
        while (remaining > 0) {
            long skipped = in.skip(remaining);
            if (skipped > 0) { remaining -= skipped; continue; }
            if (in.read() < 0) throw new IOException("cannot seek to range");
            remaining--;
        }
    }

    private void log(String name, String detail) {
        if (listener != null) listener.log(name, detail);
    }

    private static String emptyToNull(String value) {
        return value == null || value.length() == 0 ? null : value;
    }

    private static String[] prefixes(String value) {
        if (value == null || value.length() == 0) return new String[0];
        String[] parts = value.split(",");
        List<String> result = new ArrayList<String>();
        for (String part : parts) {
            String prefix = part == null ? "" : part.trim();
            if (prefix.length() > 0) result.add(prefix);
        }
        return result.toArray(new String[result.size()]);
    }

    private boolean sourceAllowed(String host) {
        if (allowedPrefixes.length == 0) return true;
        if (host == null) return false;
        for (String prefix : allowedPrefixes) if (host.startsWith(prefix)) return true;
        return false;
    }
}
