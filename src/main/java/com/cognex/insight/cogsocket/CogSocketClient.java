package com.cognex.insight.cogsocket;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class CogSocketClient {
    private static final long REQUEST_TIMEOUT_SECONDS = 30;

    /**
     * 请求超时调度器（守护线程，不阻止 JVM 退出）。
     * 用于替代 Java 9+ 的 CompletableFuture.orTimeout，保持 Java 8 兼容。
     */
    private static final ScheduledExecutorService TIMEOUT_SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "cogsocket-timeout");
                t.setDaemon(true);
                return t;
            });

    private final Gson gson = new Gson();
    private final AtomicInteger requestId = new AtomicInteger(0);
    private final ConcurrentHashMap<Integer, CompletableFuture<JsonElement>> pendingRequests = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Consumer<JsonElement[]>>> listeners = new ConcurrentHashMap<>();

    private WebSocketClient wsClient;
    private final String url;
    private volatile boolean connected = false;

    public Consumer<String> onPreviewMessage;
    public Runnable onClosed;
    public Consumer<Exception> onError;

    public CogSocketClient(String url) {
        this.url = url;
    }

    public CompletableFuture<Void> connect() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        try {
            wsClient = new WebSocketClient(new URI(url)) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    connected = true;
                    future.complete(null);
                }

                @Override
                public void onMessage(String message) {
                    if (onPreviewMessage != null) {
                        onPreviewMessage.accept(message);
                    }
                    handleMessage(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    connected = false;
                    pendingRequests.forEach((id, f) -> f.completeExceptionally(new Exception("WebSocket closed")));
                    pendingRequests.clear();
                    if (onClosed != null) {
                        onClosed.run();
                    }
                }

                @Override
                public void onError(Exception ex) {
                    if (!future.isDone()) {
                        future.completeExceptionally(ex);
                    }
                    if (onError != null) {
                        onError.accept(ex);
                    }
                }
            };
            wsClient.connect();
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    public void disconnect() {
        if (wsClient != null) {
            wsClient.close();
        }
        connected = false;
    }

    public boolean isConnected() {
        return connected;
    }

    private void handleMessage(String message) {
        try {
            JsonObject obj = JsonParser.parseString(message).getAsJsonObject();
            String type = obj.has("$type") ? obj.get("$type").getAsString() : "";
            int id = obj.has("id") ? obj.get("id").getAsInt() : 0;
            int error = obj.has("error") ? obj.get("error").getAsInt() : 0;
            String path = obj.has("path") && !obj.get("path").isJsonNull() ? obj.get("path").getAsString() : null;
            JsonElement body = obj.has("body") ? obj.get("body") : null;

            if ("event".equals(type)) {
                handleEvent(path, body);
            } else if ("resp".equals(type)) {
                handleResponse(id, error, body);
            }
        } catch (Exception e) {
            System.err.println("Error handling message: " + e.getMessage());
        }
    }

    private void handleEvent(String path, JsonElement body) {
        CopyOnWriteArrayList<Consumer<JsonElement[]>> list = listeners.get(path);
        if (list != null) {
            JsonElement[] args = body != null ? new JsonElement[]{body} : new JsonElement[0];
            for (Consumer<JsonElement[]> handler : list) {
                try {
                    handler.accept(args);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void handleResponse(int id, int error, JsonElement body) {
        CompletableFuture<JsonElement> future = pendingRequests.remove(id);
        if (future != null) {
            if (error != 0) {
                future.completeExceptionally(new CogSocketException(error, body != null ? body.toString() : "Unknown error"));
            } else {
                future.complete(body);
            }
        }
    }

    private int nextId() {
        int id = requestId.incrementAndGet();
        if (id > 0x7FFFFFFF) {
            requestId.set(1);
            id = 1;
        }
        return id;
    }

    private CompletableFuture<JsonElement> sendRequest(String type, String path, JsonElement body) {
        int id = nextId();
        JsonObject msg = new JsonObject();
        msg.addProperty("$type", type);
        msg.addProperty("id", id);
        msg.addProperty("path", path);
        if (body != null) {
            msg.add("body", body);
        }

        CompletableFuture<JsonElement> future = new CompletableFuture<>();
        pendingRequests.put(id, future);

        // 30s 超时（Java 8 兼容实现，等价于 future.orTimeout(30, SECONDS)）
        TIMEOUT_SCHEDULER.schedule(() -> {
            CompletableFuture<JsonElement> pending = pendingRequests.remove(id);
            if (pending != null) {
                pending.completeExceptionally(new Exception("请求超时 (" + REQUEST_TIMEOUT_SECONDS + "s): " + path));
            }
        }, REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        String json = gson.toJson(msg);
        if (onPreviewMessage != null) {
            onPreviewMessage.accept(json);
        }
        wsClient.send(json);
        return future;
    }

    public CompletableFuture<JsonElement> getAsync(String path) {
        return sendRequest("get", path, null);
    }

    public CompletableFuture<JsonElement> putAsync(String path, Object value) {
        return sendRequest("put", path, gson.toJsonTree(value));
    }

    public CompletableFuture<JsonElement> postAsync(String path, Object[] args) {
        return sendRequest("post", path, gson.toJsonTree(args));
    }

    public CompletableFuture<JsonElement> addListenerAsync(String eventPath, Consumer<JsonElement[]> handler) {
        listeners.computeIfAbsent(eventPath, k -> new CopyOnWriteArrayList<>()).add(handler);
        return sendRequest("listen", eventPath, null);
    }

    public CompletableFuture<JsonElement> removeListenerAsync(String eventPath, Consumer<JsonElement[]> handler) {
        CopyOnWriteArrayList<Consumer<JsonElement[]>> list = listeners.get(eventPath);
        if (list != null) {
            list.remove(handler);
            if (list.isEmpty()) {
                listeners.remove(eventPath);
                return sendRequest("unlisten", eventPath, null);
            }
        }
        CompletableFuture<JsonElement> future = new CompletableFuture<>();
        future.complete(null);
        return future;
    }

    public static class CogSocketException extends Exception {
        public final int errorCode;

        public CogSocketException(int errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }
    }
}
