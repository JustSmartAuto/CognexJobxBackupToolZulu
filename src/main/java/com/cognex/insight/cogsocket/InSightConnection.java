package com.cognex.insight.cogsocket;

import com.cognex.insight.model.CameraInfo;
import com.cognex.insight.model.CellResult;
import com.cognex.insight.model.HmiSessionInfo;
import com.cognex.insight.model.HmiState;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class InSightConnection {
    private CogSocketClient cogSocket;
    private String ipAddress = "127.0.0.1:8087";
    private String username = "admin";
    private String password = "";
    private String rootPath = "/cam0/hmi/";
    private String sessionId;
    private String sessionIdPath;
    private CameraInfo cameraInfo;
    private HmiState state = new HmiState();
    private boolean connected = false;
    private boolean connecting = false;
    private boolean jobLoading = false;
    private boolean editorAttached = false;
    private String accessLevel = "";
    private JsonObject lastResults;
    private String jobName = "";

    // Events
    public Runnable onConnectedChanged;
    public Runnable onConnectingChanged;
    public Runnable onStateChanged;
    public Runnable onLiveModeChanged;
    public Runnable onJobInfoChanged;
    public Runnable onJobLoadingChanged;
    public Runnable onEditorAttachedChanged;
    public Runnable onResultsChanged;
    public Consumer<String> onPreviewMessage;
    public Consumer<Exception> onError;

    private static final String HMI_ROOT_QUERY = "/system/paths/hmi";
    private static final String OPEN_SESSION = "openSession";
    private static final String LOGIN = "login";
    private static final String READY = "ready";
    private static final String DISPOSE = "dispose";
    private static final String INFO = "info";
    private static final String STATE = "state";
    private static final String JOB_NAME = "job/name";
    private static final String EDITOR_ATTACHED = "editorAttached";
    private static final String JOB_LOADING = "jobLoading";
    private static final String MANUAL_TRIGGER = "manualTrigger";
    private static final String SOFT_ONLINE = "softOnline";
    private static final String LIVE_MODE = "liveMode";
    private static final String SET_CELL_VALUE = "setCellValue";
    private static final String SET_CELL_EXPRESSION = "setCellExpression";
    private static final String GET_CELL_EXPRESSION = "getCellExpression";
    private static final String GET_LATEST_RESULT = "getLatestResult";
    private static final String QUERY_CELL_RESULTS = "queryCellResults";

    public boolean isConnected() { return connected; }
    public boolean isConnecting() { return connecting; }
    public boolean isOnline() { return state.online; }
    public boolean isSoftOnline() { return state.softOnline; }
    public boolean isLiveMode() { return state.liveMode; }
    public boolean isJobLoading() { return jobLoading; }
    public boolean isEditorAttached() { return editorAttached; }
    public String getAccessLevel() { return accessLevel; }
    public CameraInfo getCameraInfo() { return cameraInfo; }
    public JsonObject getLastResults() { return lastResults; }
    public String getJobName() { return jobName; }
    public String getIpAddress() { return ipAddress; }

    public CompletableFuture<Void> connect(String address, String user, String pass, HmiSessionInfo sessionInfo) {
        if (connected) {
            return disconnect().thenCompose(v -> connect(address, user, pass, sessionInfo));
        }

        this.ipAddress = address;
        this.username = user;
        this.password = pass;
        this.connecting = true;
        fireConnectingChanged();

        String wsUrl = "ws://" + address + "/ws";
        cogSocket = new CogSocketClient(wsUrl);
        cogSocket.onPreviewMessage = msg -> {
            if (onPreviewMessage != null) onPreviewMessage.accept(msg);
        };
        cogSocket.onClosed = () -> {
            if (connected) {
                disconnect().thenRun(() -> {
                    if (onConnectedChanged != null) SwingUtilities.invokeLater(onConnectedChanged);
                });
            }
        };
        cogSocket.onError = ex -> {
            if (onError != null) onError.accept(ex);
        };

        return cogSocket.connect()
            .thenCompose(v -> cogSocket.getAsync(HMI_ROOT_QUERY))
            .thenCompose(root -> {
                String rootStr = root != null && root.isJsonPrimitive() ? root.getAsString() : "cam0/hmi";
                rootPath = "/" + rootStr + "/";
                return cogSocket.getAsync(rootPath + INFO);
            })
            .thenCompose(info -> {
                cameraInfo = new CameraInfo(info.getAsJsonObject());
                return cogSocket.getAsync(rootPath + STATE);
            })
            .thenCompose(stateObj -> updateState(stateObj.getAsJsonObject()))
            .thenCompose(v -> cogSocket.getAsync(rootPath + EDITOR_ATTACHED))
            .thenCompose(ed -> {
                editorAttached = ed != null && ed.isJsonPrimitive() && ed.getAsBoolean();
                return cogSocket.getAsync(rootPath + JOB_LOADING);
            })
            .thenCompose(jl -> {
                jobLoading = jl != null && jl.isJsonPrimitive() && jl.getAsBoolean();
                return cogSocket.postAsync(rootPath + OPEN_SESSION, new Object[]{sessionInfo});
            })
            .thenCompose(session -> {
                sessionId = session.getAsString();
                sessionIdPath = sessionId + "/";
                return setupListeners();
            })
            .thenCompose(v -> {
                Object[] loginArgs = {username, password, false, false};
                return cogSocket.postAsync(sessionIdPath + LOGIN, loginArgs);
            })
            .thenCompose(access -> {
                accessLevel = access != null ? access.getAsString() : "";
                return cogSocket.postAsync(sessionIdPath + READY, new Object[0]);
            })
            .thenCompose(v -> cogSocket.getAsync(rootPath + JOB_NAME))
            .thenCompose(jn -> {
                jobName = jn != null && jn.isJsonPrimitive() ? jn.getAsString() : "";
                connected = true;
                connecting = false;
                fireConnectedChanged();
                fireStateChanged();
                return CompletableFuture.<Void>completedFuture(null);
            })
            .exceptionally(ex -> {
                connecting = false;
                connected = false;
                fireConnectingChanged();
                if (cogSocket != null) cogSocket.disconnect();
                if (onError != null) onError.accept(new Exception("连接失败: " + ex.getMessage()));
                return null;
            });
    }

    public CompletableFuture<Void> disconnect() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        if (cogSocket != null && cogSocket.isConnected()) {
            try {
                cogSocket.postAsync(sessionIdPath + DISPOSE, new Object[0])
                    .whenComplete((r, e) -> {
                        cogSocket.disconnect();
                        resetState();
                        future.complete(null);
                    });
            } catch (Exception e) {
                cogSocket.disconnect();
                resetState();
                future.complete(null);
            }
        } else {
            resetState();
            future.complete(null);
        }
        return future;
    }

    private void resetState() {
        connected = false;
        connecting = false;
        state = new HmiState();
        jobLoading = false;
        editorAttached = false;
        accessLevel = "";
        lastResults = null;
        sessionId = null;
        sessionIdPath = null;
    }

    private CompletableFuture<Void> setupListeners() {
        List<CompletableFuture<JsonElement>> futures = new ArrayList<>();
        futures.add(cogSocket.addListenerAsync(rootPath + "stateChanged", args -> {
            if (args.length > 0 && args[0].isJsonObject()) {
                updateState(args[0].getAsJsonObject());
                SwingUtilities.invokeLater(this::fireStateChanged);
            }
        }));
        futures.add(cogSocket.addListenerAsync(rootPath + "liveModeChanged", args -> {
            if (args.length > 0 && args[0].isJsonObject()) {
                JsonObject obj = args[0].getAsJsonObject();
                if (obj.has("liveMode")) {
                    state.liveMode = obj.get("liveMode").getAsBoolean();
                    SwingUtilities.invokeLater(this::fireLiveModeChanged);
                }
            }
        }));
        futures.add(cogSocket.addListenerAsync(rootPath + "jobChanged", args -> {
            SwingUtilities.invokeLater(this::fireJobInfoChanged);
        }));
        futures.add(cogSocket.addListenerAsync(rootPath + "jobLoadingChanged", args -> {
            if (args.length > 0 && args[0].isJsonObject()) {
                JsonObject obj = args[0].getAsJsonObject();
                if (obj.has("jobLoading")) {
                    jobLoading = obj.get("jobLoading").getAsBoolean();
                    SwingUtilities.invokeLater(this::fireJobLoadingChanged);
                }
            }
        }));
        futures.add(cogSocket.addListenerAsync(rootPath + "editorAttachedChanged", args -> {
            if (args.length > 0 && args[0].isJsonObject()) {
                JsonObject obj = args[0].getAsJsonObject();
                if (obj.has("editorAttached")) {
                    editorAttached = obj.get("editorAttached").getAsBoolean();
                    SwingUtilities.invokeLater(this::fireEditorAttachedChanged);
                }
            }
        }));
        futures.add(cogSocket.addListenerAsync(sessionIdPath + "resultChanged", args -> {
            if (args.length > 0 && args[0].isJsonObject()) {
                lastResults = args[0].getAsJsonObject();
                SwingUtilities.invokeLater(this::fireResultsChanged);
            }
        }));
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    private CompletableFuture<Void> updateState(JsonObject stateObj) {
        if (stateObj == null) return CompletableFuture.completedFuture(null);
        if (stateObj.has("online")) state.online = stateObj.get("online").getAsBoolean();
        if (stateObj.has("softOnline")) state.softOnline = stateObj.get("softOnline").getAsBoolean();
        if (stateObj.has("nativeOnline")) state.nativeOnline = stateObj.get("nativeOnline").getAsBoolean();
        if (stateObj.has("discreteOnline")) state.discreteOnline = stateObj.get("discreteOnline").getAsBoolean();
        if (stateObj.has("ffpOnline")) state.ffpOnline = stateObj.get("ffpOnline").getAsBoolean();
        if (stateObj.has("liveMode")) state.liveMode = stateObj.get("liveMode").getAsBoolean();
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 加载指定名称的 jobx 作业（对应 CogSocket&WebApi 14.1 loadJob）。
     * 相机在线时 loadJob 会失败，需先 setSoftOnline(false)。
     */
    public CompletableFuture<Void> loadJob(String jobName) {
        return cogSocket.postAsync(sessionIdPath + "loadJob", new Object[]{jobName})
            .thenApply(r -> null);
    }

    public CompletableFuture<Void> sendReady() {
        return cogSocket.postAsync(sessionIdPath + READY, new Object[0])
            .thenApply(r -> null);
    }

    public CompletableFuture<Void> manualTrigger() {
        return cogSocket.postAsync(sessionIdPath + MANUAL_TRIGGER, new Object[0])
            .thenApply(r -> null);
    }

    public CompletableFuture<Void> setSoftOnline(boolean value) {
        return cogSocket.putAsync(sessionIdPath + SOFT_ONLINE, value)
            .thenApply(r -> null);
    }

    public CompletableFuture<Void> setLiveMode(boolean value) {
        return cogSocket.putAsync(sessionIdPath + LIVE_MODE, value)
            .thenApply(r -> null);
    }

    public CompletableFuture<Void> setCellValue(String cellName, Object value) {
        if (!canEditCells()) {
            return failedFuture(
                new Exception("无法设置单元格值: 相机在线或编辑器已连接时禁止修改单元格。请先离线或断开编辑器。"));
        }
        return cogSocket.postAsync(sessionIdPath + SET_CELL_VALUE, new Object[]{cellName, value})
            .thenApply(r -> null);
    }

    public CompletableFuture<Void> setCellExpression(String cellName, String expression) {
        if (!canEditCells()) {
            return failedFuture(
                new Exception("无法设置单元格表达式: 相机在线或编辑器已连接时禁止修改单元格。请先离线或断开编辑器。"));
        }
        return cogSocket.postAsync(sessionIdPath + SET_CELL_EXPRESSION, new Object[]{cellName, expression})
            .thenApply(r -> null);
    }

    /** Java 8 兼容的异常完成 Future（CompletableFuture.failedFuture 是 Java 9+ API）。 */
    private static CompletableFuture<Void> failedFuture(Throwable t) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        future.completeExceptionally(t);
        return future;
    }

    /**
     * Checks if cell editing is allowed.
     * Cell editing is blocked when the camera is online or an editor is attached.
     */
    public boolean canEditCells() {
        return connected && !state.online && !jobLoading && !editorAttached;
    }

    /**
     * Checks if the user has full access level.
     */
    public boolean isFullAccess() {
        return "full".equalsIgnoreCase(accessLevel);
    }

    public CompletableFuture<String> getCellExpression(String cellName) {
        return cogSocket.postAsync(sessionIdPath + GET_CELL_EXPRESSION, new Object[]{cellName})
            .thenApply(r -> {
                if (r == null) return "";
                if (r.isJsonPrimitive()) return r.getAsString();
                if (r.isJsonObject()) {
                    JsonObject obj = r.getAsJsonObject();
                    if (obj.has("expression") && !obj.get("expression").isJsonNull()) {
                        return obj.get("expression").getAsString();
                    }
                }
                return "";
            });
    }

    public CompletableFuture<JsonObject> getLatestResult() {
        return cogSocket.postAsync(sessionIdPath + GET_LATEST_RESULT, new Object[0])
            .thenApply(r -> r != null && r.isJsonObject() ? r.getAsJsonObject() : null);
    }

    /**
     * 批量获取多个单元格的表达式。
     * 使用串行方式逐个获取，避免对相机造成过大压力。
     *
     * @param cellLocations 单元格位置列表
     * @return Map<cellLocation, expression>
     */
    public CompletableFuture<Map<String, String>> fetchAllExpressions(List<String> cellLocations) {
        Map<String, String> expressions = new HashMap<>();
        return fetchExpressionsRecursive(cellLocations, 0, expressions);
    }

    private CompletableFuture<Map<String, String>> fetchExpressionsRecursive(
            List<String> cellLocations, int index, Map<String, String> expressions) {
        if (index >= cellLocations.size()) {
            return CompletableFuture.completedFuture(expressions);
        }
        String loc = cellLocations.get(index);
        return getCellExpression(loc)
            .thenCompose(expr -> {
                if (expr != null && !expr.isEmpty()) {
                    expressions.put(loc, expr);
                }
                // 小延迟避免请求过快
                if (index % 50 == 49) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException ignored) {
                    }
                }
                return fetchExpressionsRecursive(cellLocations, index + 1, expressions);
            })
            .exceptionally(ex -> {
                // 单个单元格获取失败，继续下一个
                return fetchExpressionsRecursiveSync(cellLocations, index + 1, expressions);
            });
    }

    private Map<String, String> fetchExpressionsRecursiveSync(
            List<String> cellLocations, int index, Map<String, String> expressions) {
        // exceptionally 中需要返回 Map，这里用同步递归简化处理
        for (int i = index; i < cellLocations.size(); i++) {
            String loc = cellLocations.get(i);
            try {
                String expr = getCellExpression(loc).get();
                if (expr != null && !expr.isEmpty()) {
                    expressions.put(loc, expr);
                }
            } catch (Exception ignored) {
            }
            if (i % 50 == 49) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ignored) {
                }
            }
        }
        return expressions;
    }

    public CompletableFuture<List<CellResult>> queryCellResults(String[] cells) {
        return cogSocket.postAsync(sessionIdPath + QUERY_CELL_RESULTS, new Object[]{Arrays.asList(cells)})
            .thenApply(r -> {
                List<CellResult> results = new ArrayList<>();
                if (r == null) {
                    return results;
                }
                // 处理直接返回数组的情况
                JsonArray arr = null;
                if (r.isJsonArray()) {
                    arr = r.getAsJsonArray();
                } else if (r.isJsonObject()) {
                    // 某些版本可能包装在对象中
                    JsonObject obj = r.getAsJsonObject();
                    if (obj.has("cells") && obj.get("cells").isJsonArray()) {
                        arr = obj.getAsJsonArray("cells");
                    }
                }
                if (arr != null) {
                    for (JsonElement e : arr) {
                        if (e.isJsonObject()) {
                            JsonObject obj = e.getAsJsonObject();
                            CellResult cr = new CellResult();
                            cr.type = obj.has("$type") ? obj.get("$type").getAsString() : "";
                            cr.location = obj.has("location") ? obj.get("location").getAsString() : "";
                            cr.name = obj.has("name") ? obj.get("name").getAsString() : "";
                            cr.error = obj.has("error") && obj.get("error").getAsBoolean();
                            if (obj.has("data") && !obj.get("data").isJsonNull()) {
                                JsonElement data = obj.get("data");
                                if (data.isJsonPrimitive()) {
                                    cr.data = data.getAsString();
                                } else {
                                    cr.data = data.toString();
                                }
                            }
                            // 同时解析 expression 字段
                            if (obj.has("expression") && !obj.get("expression").isJsonNull()) {
                                cr.expression = obj.get("expression").getAsString();
                            }
                            results.add(cr);
                        }
                    }
                }
                return results;
            });
    }

    private void fireConnectedChanged() {
        if (onConnectedChanged != null) SwingUtilities.invokeLater(onConnectedChanged);
    }

    private void fireConnectingChanged() {
        if (onConnectingChanged != null) SwingUtilities.invokeLater(onConnectingChanged);
    }

    private void fireStateChanged() {
        if (onStateChanged != null) SwingUtilities.invokeLater(onStateChanged);
    }

    private void fireLiveModeChanged() {
        if (onLiveModeChanged != null) SwingUtilities.invokeLater(onLiveModeChanged);
    }

    private void fireJobInfoChanged() {
        if (onJobInfoChanged != null) SwingUtilities.invokeLater(onJobInfoChanged);
    }

    private void fireJobLoadingChanged() {
        if (onJobLoadingChanged != null) SwingUtilities.invokeLater(onJobLoadingChanged);
    }

    private void fireEditorAttachedChanged() {
        if (onEditorAttachedChanged != null) SwingUtilities.invokeLater(onEditorAttachedChanged);
    }

    private void fireResultsChanged() {
        if (onResultsChanged != null) SwingUtilities.invokeLater(onResultsChanged);
    }
}
