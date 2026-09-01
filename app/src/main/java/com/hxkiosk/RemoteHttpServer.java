package com.hxkiosk;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class RemoteHttpServer {

    private static final String TAG = "HxKioskRemote";
    private static final String SESSION_COOKIE = "hxkiosk_session";

    private final Context context;
    private final int port;
    private final PreferenceManager preferenceManager;
    private ExecutorService executor = Executors.newCachedThreadPool();
    private final Map<String, Long> sessions = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ServerSocket serverSocket;

    public RemoteHttpServer(Context context, int port) {
        this.context = context.getApplicationContext();
        this.port = port;
        this.preferenceManager = new PreferenceManager(this.context);
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        if (executor == null || executor.isShutdown() || executor.isTerminated()) {
            executor = Executors.newCachedThreadPool();
        }
        executor.execute(this::acceptLoop);
    }

    public void stop() {
        running.set(false);
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
            serverSocket = null;
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private void acceptLoop() {
        ServerSocket socket = null;
        try {
            socket = new ServerSocket();
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(port));
            serverSocket = socket;
            Log.i(TAG, "Console remoto escutando em " + socket.getLocalSocketAddress());
            while (running.get()) {
                try {
                    Socket client = socket.accept();
                    executor.execute(() -> handleClient(client));
                } catch (SocketException closed) {
                    if (!running.get()) {
                        break;
                    }
                }
            }
        } catch (IOException exception) {
            Log.e(TAG, "Falha ao iniciar o console remoto", exception);
            running.set(false);
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
            }
            if (serverSocket == socket) {
                serverSocket = null;
            }
        }
    }

    private void handleClient(Socket client) {
        try (Socket ignored = client;
             InputStream inputStream = client.getInputStream();
             OutputStream outputStream = client.getOutputStream()) {
            client.setTcpNoDelay(true);
            client.setSoTimeout(8000);
            HttpRequest request = HttpRequest.parse(inputStream);
            if (request == null) {
                return;
            }
            if ("GET".equals(request.method) && "/api/stream".equals(request.path)) {
                client.setSoTimeout(0);
            }
            dispatch(request, outputStream);
        } catch (Exception exception) {
            Log.w(TAG, "Conexao remote encerrada", exception);
        }
    }

    private void dispatch(HttpRequest request, OutputStream outputStream) throws Exception {
        if ("OPTIONS".equals(request.method)) {
            writeRaw(outputStream, 204, "text/plain", new byte[0], corsHeaders());
            return;
        }
        String path = request.path;
        if ("GET".equals(request.method) && "/".equals(path)) {
            writeAsset(outputStream, "remote/index.html", "text/html; charset=utf-8");
            return;
        }
        if ("GET".equals(request.method) && "/api/status".equals(path)) {
            RemoteSession.ping();
            writeJson(outputStream, 200, buildStatus(request).toString());
            return;
        }
        if ("GET".equals(request.method) && "/api/screenshot".equals(path)) {
            RemoteSession.ping();
            byte[] jpeg = ScreenCaptureHelper.captureJpeg(
                    HxKioskApp.get() == null ? null : HxKioskApp.get().getResumedActivity()
            );
            if (jpeg == null) {
                writeJson(outputStream, 503, "{\"ok\":false,\"error\":\"no_frame\"}");
                return;
            }
            writeBytes(outputStream, 200, "image/jpeg", jpeg, false);
            return;
        }
        if ("GET".equals(request.method) && "/api/stream".equals(path)) {
            writeMjpeg(outputStream);
            return;
        }
        if ("POST".equals(request.method) && "/api/tap".equals(path)) {
            handleTap(request, outputStream);
            return;
        }
        if ("POST".equals(request.method) && "/api/input".equals(path)) {
            handleInput(request, outputStream);
            return;
        }
        if ("POST".equals(request.method) && "/api/login".equals(path)) {
            handleLogin(request, outputStream);
            return;
        }
        if ("POST".equals(request.method) && "/api/logout".equals(path)) {
            sessions.remove(request.sessionToken);
            writeJson(outputStream, 200, "{\"ok\":true}");
            return;
        }
        if (!isAuthenticated(request)) {
            writeJson(outputStream, 401, "{\"ok\":false,\"error\":\"unauthorized\"}");
            return;
        }
        if ("POST".equals(request.method) && "/api/command".equals(path)) {
            handleCommand(request, outputStream);
            return;
        }
        writeJson(outputStream, 404, "{\"ok\":false,\"error\":\"not_found\"}");
    }

    private void handleTap(HttpRequest request, OutputStream outputStream) throws Exception {
        RemoteSession.ping();
        JSONObject body = request.jsonBody();
        float x = (float) body.optDouble("x", -1);
        float y = (float) body.optDouble("y", -1);
        if (x < 0f || x > 1f || y < 0f || y > 1f) {
            writeJson(outputStream, 400, "{\"ok\":false,\"error\":\"invalid_tap\"}");
            return;
        }
        boolean tapped = KioskAccessibilityService.tapNormalized(x, y);
        writeJson(outputStream, tapped ? 200 : 503, "{\"ok\":" + tapped + "}");
    }

    private void handleInput(HttpRequest request, OutputStream outputStream) throws Exception {
        RemoteSession.ping();
        JSONObject body = request.jsonBody();
        String type = body.optString("type", "tap");
        boolean ok;
        if ("nav".equals(type)) {
            ok = KioskAccessibilityService.performNav(body.optString("action"));
            writeJson(outputStream, ok ? 200 : 503, "{\"ok\":" + ok + "}");
            return;
        }
        if ("key".equals(type) || "text".equals(type)) {
            ok = RemoteInput.inject(body);
            writeJson(outputStream, ok ? 200 : 503, "{\"ok\":" + ok + "}");
            return;
        }
        float x = (float) body.optDouble("x", -1);
        float y = (float) body.optDouble("y", -1);
        if (x < 0f || x > 1f || y < 0f || y > 1f) {
            writeJson(outputStream, 400, "{\"ok\":false,\"error\":\"invalid_input\"}");
            return;
        }
        ok = RemoteInput.injectPointer(body);
        writeJson(outputStream, ok ? 200 : 503, "{\"ok\":" + ok + "}");
    }

    private void handleLogin(HttpRequest request, OutputStream outputStream) throws Exception {
        JSONObject body = request.jsonBody();
        String password = body.optString("password", "");
        if (!preferenceManager.validateAdminPassword(password)) {
            writeJson(outputStream, 401, "{\"ok\":false,\"error\":\"invalid_password\"}");
            return;
        }
        String token = UUID.randomUUID().toString() + Long.toHexString(secureRandom.nextLong());
        sessions.put(token, System.currentTimeMillis());
        String extraHeader = "Set-Cookie: " + SESSION_COOKIE + "=" + token + "; Path=/; HttpOnly\r\n";
        writeRaw(outputStream, 200, "application/json; charset=utf-8",
                "{\"ok\":true}".getBytes(StandardCharsets.UTF_8), extraHeader);
    }

    private void handleCommand(HttpRequest request, OutputStream outputStream) throws Exception {
        JSONObject body = request.jsonBody();
        String command = body.optString("command", "");
        switch (command) {
            case "bring_kiosk":
                KioskPolicyManager.bringKioskToFront(context);
                break;
            case "reload_url":
                if (body.has("url")) {
                    preferenceManager.setAllowedUrl(body.optString("url"));
                }
                KioskPolicyManager.bringKioskToFront(context);
                break;
            case "set_remote":
                preferenceManager.setBooleanConfig(
                        PreferenceManager.KEY_REMOTE_ACCESS,
                        body.optBoolean("enabled", true)
                );
                if (HxKioskApp.get() != null) {
                    HxKioskApp.get().syncRemoteAccessService();
                }
                break;
            case "release_access":
                KioskPolicyManager.releaseManagedAccess(context);
                preferenceManager.setKioskSessionActive(false);
                break;
            case "exit_kiosk":
                preferenceManager.setKioskSessionActive(false);
                KioskPolicyManager.stopLockTaskIfNeeded(
                        HxKioskApp.get() == null ? null : HxKioskApp.get().getResumedActivity()
                );
                break;
            default:
                writeJson(outputStream, 400, "{\"ok\":false,\"error\":\"unknown_command\"}");
                return;
        }
        writeJson(outputStream, 200, "{\"ok\":true}");
    }

    private JSONObject buildStatus(HttpRequest request) throws Exception {
        JSONObject json = new JSONObject();
        json.put("ok", true);
        json.put("authenticated", isAuthenticated(request));
        json.put("app", "hxkiosk");
        json.put("mode", preferenceManager.getKioskMode());
        json.put("url", preferenceManager.getAllowedUrl());
        json.put("kioskActive", preferenceManager.isKioskSessionActive());
        json.put("remoteEnabled", preferenceManager.getBooleanConfig(PreferenceManager.KEY_REMOTE_ACCESS, true));
        json.put("ip", LanAddressHelper.getIpv4Address(context));
        json.put("port", port);
        json.put("accessibility", KioskPolicyManager.isAccessibilityEnabled(context));
        json.put("notificationListener", KioskPolicyManager.isNotificationListenerEnabled(context));
        json.put("launcher", KioskPolicyManager.isHomeApp(context));
        json.put("deviceAdmin", KioskPolicyManager.isDeviceAdminActive(context));
        json.put("deviceOwner", KioskPolicyManager.isDeviceOwner(context));
        json.put("fullScreen", ScreenMirrorService.isCapturing());
        return json;
    }

    private boolean isAuthenticated(HttpRequest request) {
        if (TextUtils.isEmpty(request.sessionToken)) {
            return false;
        }
        Long issuedAt = sessions.get(request.sessionToken);
        if (issuedAt == null) {
            return false;
        }
        if (System.currentTimeMillis() - issuedAt > 12 * 60 * 60 * 1000L) {
            sessions.remove(request.sessionToken);
            return false;
        }
        return true;
    }

    private void writeAsset(OutputStream outputStream, String assetPath, String contentType) throws IOException {
        try (InputStream inputStream = context.getAssets().open(assetPath)) {
            byte[] data = readAll(inputStream);
            writeBytes(outputStream, 200, contentType, data, false);
        }
    }

    private void writeMjpeg(OutputStream outputStream) throws IOException, InterruptedException {
        RemoteSession.ping();
        String boundary = "hxkioskframe";
        String header = "HTTP/1.1 200 OK\r\n"
                + "Content-Type: multipart/x-mixed-replace; boundary=" + boundary + "\r\n"
                + corsHeaders()
                + "Connection: keep-alive\r\n\r\n";
        outputStream.write(header.getBytes(StandardCharsets.US_ASCII));
        outputStream.flush();
        byte[] lastSent = null;
        while (running.get()) {
            RemoteSession.ping();
            byte[] jpeg = ScreenCaptureHelper.captureJpeg(
                    HxKioskApp.get() == null ? null : HxKioskApp.get().getResumedActivity()
            );
            if (jpeg != null && jpeg != lastSent) {
                lastSent = jpeg;
                String part = "--" + boundary + "\r\n"
                        + "Content-Type: image/jpeg\r\n"
                        + "Content-Length: " + jpeg.length + "\r\n\r\n";
                outputStream.write(part.getBytes(StandardCharsets.US_ASCII));
                outputStream.write(jpeg);
                outputStream.write("\r\n".getBytes(StandardCharsets.US_ASCII));
                outputStream.flush();
            }
            Thread.sleep(50);
        }
    }

    private void writeJson(OutputStream outputStream, int status, String json) throws IOException {
        writeBytes(outputStream, status, "application/json; charset=utf-8",
                json.getBytes(StandardCharsets.UTF_8), false);
    }

    private void writeBytes(
            OutputStream outputStream,
            int status,
            String contentType,
            byte[] body,
            boolean ignored
    ) throws IOException {
        writeRaw(outputStream, status, contentType, body, "");
    }

    private void writeRaw(
            OutputStream outputStream,
            int status,
            String contentType,
            byte[] body,
            String extraHeaders
    ) throws IOException {
        String reason = status == 200 ? "OK" : (status == 204 ? "No Content" : (status == 401 ? "Unauthorized" : "Error"));
        String header = "HTTP/1.1 " + status + " " + reason + "\r\n"
                + "Content-Type: " + contentType + "\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + corsHeaders()
                + extraHeaders
                + "Connection: close\r\n\r\n";
        outputStream.write(header.getBytes(StandardCharsets.US_ASCII));
        outputStream.write(body);
        outputStream.flush();
    }

    private static String corsHeaders() {
        return "Access-Control-Allow-Origin: *\r\n"
                + "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n"
                + "Access-Control-Allow-Headers: Content-Type\r\n"
                + "Cache-Control: no-store\r\n";
    }

    private static byte[] readAll(InputStream inputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, read);
        }
        return outputStream.toByteArray();
    }

    private static final class HttpRequest {
        private final String method;
        private final String path;
        private final String sessionToken;
        private final String body;

        private HttpRequest(String method, String path, String sessionToken, String body) {
            this.method = method;
            this.path = path;
            this.sessionToken = sessionToken;
            this.body = body;
        }

        private JSONObject jsonBody() {
            if (TextUtils.isEmpty(body)) {
                return new JSONObject();
            }
            try {
                return new JSONObject(body);
            } catch (Exception ignored) {
                return new JSONObject();
            }
        }

        @Nullable
        private static HttpRequest parse(InputStream inputStream) throws IOException {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            String startLine = reader.readLine();
            if (startLine == null || startLine.isEmpty()) {
                return null;
            }
            String[] parts = startLine.split(" ");
            if (parts.length < 2) {
                return null;
            }
            String method = parts[0].toUpperCase(Locale.ROOT);
            String path = parts[1];
            int queryIndex = path.indexOf('?');
            if (queryIndex >= 0) {
                path = path.substring(0, queryIndex);
            }

            Map<String, String> headers = new HashMap<>();
            String headerLine;
            while ((headerLine = reader.readLine()) != null && !headerLine.isEmpty()) {
                int colon = headerLine.indexOf(':');
                if (colon > 0) {
                    headers.put(
                            headerLine.substring(0, colon).trim().toLowerCase(Locale.ROOT),
                            headerLine.substring(colon + 1).trim()
                    );
                }
            }

            int contentLength = 0;
            try {
                contentLength = Integer.parseInt(headers.getOrDefault("content-length", "0"));
            } catch (NumberFormatException ignored) {
            }
            char[] bodyChars = new char[Math.max(contentLength, 0)];
            int read = 0;
            while (read < contentLength) {
                int current = reader.read(bodyChars, read, contentLength - read);
                if (current < 0) {
                    break;
                }
                read += current;
            }
            String body = new String(bodyChars, 0, read);

            String cookieHeader = headers.getOrDefault("cookie", "");
            String sessionToken = "";
            for (String cookie : cookieHeader.split(";")) {
                String trimmed = cookie.trim();
                if (trimmed.startsWith(SESSION_COOKIE + "=")) {
                    sessionToken = URLDecoder.decode(
                            trimmed.substring((SESSION_COOKIE + "=").length()),
                            StandardCharsets.UTF_8.name()
                    );
                }
            }
            return new HttpRequest(method, path, sessionToken, body);
        }
    }
}
