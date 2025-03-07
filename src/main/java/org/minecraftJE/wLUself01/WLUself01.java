package org.minecraftJE.wLUself01;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.configuration.file.FileConfiguration;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import org.json.JSONArray;
import org.json.JSONObject;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import javax.mail.Authenticator;
import javax.mail.PasswordAuthentication;
import javax.mail.*;
import javax.mail.internet.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public final class WLUself01 extends JavaPlugin {

    private HttpServer server;
    private FileConfiguration config;
    private final Map<String, CaptchaInfo> captchaStore = new ConcurrentHashMap<>();
    private WebSocketClient webSocketClient;
    private boolean isWebSocketConnected = false;
    private String miraiSessionKey = null;
    private int reconnectAttempts = 0;
    private final int MAX_RECONNECT_ATTEMPTS = 10;
    private final ConcurrentHashMap<String, JSONObject> syncResponses = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CountDownLatch> syncLocks = new ConcurrentHashMap<>();

    // 配置键名常量
    private static final String MIRAI_WEBSOCKET_URL = "mirai.websocket_url";
    private static final String MIRAI_VERIFY_KEY = "mirai.verify_key";
    private static final String MIRAI_BOT_QQ = "mirai.bot_qq";
    private static final String MIRAI_GROUP_ID = "mirai.group_id";
    private static final String SERVER_PORT = "server.port";
    private static final String WHITELIST_CMD = "server.whitelist_command";
    private static final String EMAIL_HOST = "email.host";
    private static final String EMAIL_PORT = "email.port";
    private static final String EMAIL_USERNAME = "email.username";
    private static final String EMAIL_PASSWORD = "email.password";
    private static final String EMAIL_FROM = "email.from";
    private static final String EMAIL_SUBJECT = "email.subject";
    private static final String ENABLE_VERIFY = "mirai.enable_verify";
    private static final String SINGLE_MODE = "mirai.single_mode";
    private static final String DEBUG_MODE = "debug_mode";

    // 新增配置键
    private static final String REQUIRE_QQ_VERIFICATION = "verification.require_qq";
    private static final String QQ_GROUP_MEMBER_FILE = "verification.qq_members_file";
    private static final String QQ_ERROR_MESSAGE = "messages.qq_group_error";
    private static final String EMAIL_SIGNATURE = "email.signature";
    private static final String NOTIFY_VIA_QQ = "notification.notify_via_qq";

    @Override
    public void onEnable() {
        // 初始化配置系统
        setupConfigSystem();

        // 验证关键配置
        if (!validateEssentialConfig()) {
            getLogger().severe("关键配置缺失，插件将被禁用！");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // 仅当需要QQ验证时才连接WebSocket
        if (config.getBoolean(REQUIRE_QQ_VERIFICATION, false)) {
            // 连接WebSocket
            connectToMiraiWebSocket();

            // 定期检查WebSocket连接状态
            checkWebSocketConnection();
        }

        // 启动HTTP服务器
        startWebServer();

        @SuppressWarnings("deprecation")
        String version = getDescription().getVersion();
        getLogger().info("白名单插件已启动 v" + version);

        // 注册命令执行器 - 添加null检查以防止NPE
        if (getCommand("wlreload") != null) {
            getCommand("wlreload").setExecutor((sender, command, label, args) -> {
                if (sender.hasPermission("wluself01.reload")) {
                    reloadConfig();
                    sender.sendMessage("§a配置已重新加载");
                    return true;
                } else {
                    sender.sendMessage("§c您没有权限执行此命令");
                    return false;
                }
            });
        } else {
            getLogger().warning("无法注册命令 'wlreload'，请检查plugin.yml配置");
        }
    }

    @Override
    public void onDisable() {
        // 关闭WebSocket连接
        if (webSocketClient != null) {
            webSocketClient.close();
        }

        // 关闭HTTP服务器
        stopWebServer();

        getLogger().info("插件已卸载");
    }

    // ================= 配置系统 =================
    private void setupConfigSystem() {
        // 创建插件数据文件夹
        if (!getDataFolder().exists()) {
            boolean created = getDataFolder().mkdirs();
            if (created) {
                getLogger().info("已创建插件数据目录: " + getDataFolder().getPath());
            }
        }

        // 初始化配置文件
        saveDefaultConfig();
        config = getConfig();

        // 记录配置加载成功
        getLogger().info("配置文件已加载");
    }

    @Override
    public void saveDefaultConfig() {
        // 仅当配置文件不存在时生成默认配置
        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            InputStream resourceStream = getResource("config.yml");
            if (resourceStream != null) {
                // 如果 jar 内存在嵌入的 config.yml，则使用 saveResource
                saveResource("config.yml", false);
                getLogger().info("已生成默认配置文件");
            } else {
                // 如果没有嵌入的默认配置，则手动创建一个默认配置文件
                try {
                    if (configFile.getParentFile().mkdirs()) {
                        getLogger().info("已创建配置文件目录");
                    }
                    if (configFile.createNewFile()) {
                        try (PrintWriter writer = new PrintWriter(new FileWriter(configFile))) {
                            writer.println("# 验证设置");
                            writer.println("verification.require_qq: false");
                            writer.println("verification.qq_members_file: \"members.json\"");
                            writer.println("");
                            writer.println("# Mirai连接设置 (仅当verification.require_qq为true时使用)");
                            writer.println("mirai.websocket_url: \"ws://localhost:8080/message\"");
                            writer.println("mirai.verify_key: \"your_verify_key\"");
                            writer.println("mirai.bot_qq: 123456789");
                            writer.println("mirai.group_id: 123456789");
                            writer.println("mirai.enable_verify: true");
                            writer.println("mirai.single_mode: true");
                            writer.println("");
                            writer.println("# 服务器设置");
                            writer.println("server.port: 25565");
                            writer.println("server.whitelist_command: \"whitelist add %player%\"");
                            writer.println("");
                            writer.println("# 验证码设置");
                            writer.println("captcha.length: 6");
                            writer.println("captcha.expiry_minutes: 5");
                            writer.println("");
                            writer.println("# 邮箱设置");
                            writer.println("email.host: \"smtp.example.com\"");
                            writer.println("email.port: 587");
                            writer.println("email.username: \"your_email@example.com\"");
                            writer.println("email.password: \"your_password\"");
                            writer.println("email.from: \"Minecraft Server <your_email@example.com>\"");
                            writer.println("email.subject: \"Minecraft服务器白名单验证码\"");
                            writer.println("email.signature: \"Minecraft服务器管理团队\"");
                            writer.println("");
                            writer.println("# 消息设置");
                            writer.println("messages.qq_group_error: \"请先加入服务器指定的QQ群！\"");
                            writer.println("");
                            writer.println("# 通知设置");
                            writer.println("notification.notify_via_qq: false");
                            writer.println("");
                            writer.println("# 调试模式");
                            writer.println("debug_mode: false");
                        }
                        getLogger().info("未找到嵌入的默认配置，已创建新的配置文件");
                    }
                } catch (IOException e) {
                    getLogger().severe("无法创建默认配置文件: " + e.getMessage());
                }
            }
        }
    }

    @Override
    public void reloadConfig() {
        super.reloadConfig();
        config = getConfig();
        getLogger().info("配置文件已重新加载");
    }

    private boolean validateEssentialConfig() {
        boolean requireQQ = config.getBoolean(REQUIRE_QQ_VERIFICATION, false);

        if (requireQQ) {
            if (!config.contains(MIRAI_WEBSOCKET_URL)) {
                getLogger().severe("配置缺失: WebSocket URL未设置");
                return false;
            }

            if (config.getBoolean(ENABLE_VERIFY, true) && !config.contains(MIRAI_VERIFY_KEY)) {
                getLogger().severe("配置缺失: 已启用验证但未设置验证密钥");
                return false;
            }

            if (!config.getBoolean(SINGLE_MODE, false) && !config.contains(MIRAI_BOT_QQ)) {
                getLogger().severe("配置缺失: 非单会话模式下必须设置机器人QQ号");
                return false;
            }

            if (!config.contains(MIRAI_GROUP_ID)) {
                getLogger().severe("配置缺失: 未设置QQ群号");
                return false;
            }
        } else {
            // 如果不需要QQ验证，检查是否有QQ群成员文件
            if (!config.contains(QQ_GROUP_MEMBER_FILE)) {
                getLogger().info("没有设置QQ群成员文件，将不进行QQ验证");
            }
        }

        // 邮件配置验证
        if (!config.contains(EMAIL_HOST) || !config.contains(EMAIL_PORT) ||
                !config.contains(EMAIL_USERNAME) || !config.contains(EMAIL_PASSWORD)) {
            getLogger().severe("配置缺失: 邮箱配置不完整");
            return false;
        }

        return true;
    }

    // ================= 调试函数 =================
    private void logDebug(String message) {
        if (config.getBoolean(DEBUG_MODE, false)) {
            getLogger().info("[DEBUG] " + message);
        }
    }

    // ================= WebSocket连接 =================
    private void connectToMiraiWebSocket() {
        try {
            String wsUrl = config.getString(MIRAI_WEBSOCKET_URL);
            if (wsUrl == null || wsUrl.isEmpty()) {
                getLogger().severe("WebSocket URL未设置");
                return;
            }

            // 确保URL格式正确
            if (!wsUrl.startsWith("ws://") && !wsUrl.startsWith("wss://")) {
                wsUrl = "ws://" + wsUrl;
            }
            if (!wsUrl.endsWith("/message") && !wsUrl.endsWith("/all")) {
                if (!wsUrl.endsWith("/")) {
                    wsUrl += "/";
                }
                wsUrl += "message";
            }

            getLogger().info("正在连接到Mirai WebSocket: " + wsUrl);

            URI serverUri = new URI(wsUrl);
            webSocketClient = new WebSocketClient(serverUri) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    getLogger().info("WebSocket连接已建立");
                    isWebSocketConnected = true;
                    reconnectAttempts = 0;

                    // 根据配置判断是否需要进行验证
                    boolean enableVerify = config.getBoolean(ENABLE_VERIFY, true);
                    if (enableVerify) {
                        // 发送验证请求
                        sendAuthRequest();
                    } else if (config.getBoolean(SINGLE_MODE, false)) {
                        // 如果是单会话模式且不需要验证，则可以直接开始使用
                        getLogger().info("单会话模式，无需验证，连接已就绪");
                    } else {
                        // 如果非单会话模式且不需要验证，则需要绑定机器人
                        long botQQ = config.getLong(MIRAI_BOT_QQ);
                        getLogger().info("无需验证，直接绑定机器人: " + botQQ);
                    }
                }

                @Override
                public void onMessage(String message) {
                    try {
                        logDebug("收到WebSocket消息: " + message);
                        JSONObject json = new JSONObject(message);
                        processMessage(json);
                    } catch (Exception e) {
                        getLogger().log(Level.WARNING, "处理WebSocket消息时出错", e);
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    getLogger().info("WebSocket连接已关闭: 代码=" + code + " 原因=" + reason + " 远程关闭=" + remote);
                    isWebSocketConnected = false;
                    miraiSessionKey = null;

                    // 尝试重新连接
                    if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                        scheduleReconnect();
                    } else {
                        getLogger().severe("达到最大重连次数，放弃重连");
                    }
                }

                @Override
                public void onError(Exception ex) {
                    getLogger().log(Level.SEVERE, "WebSocket错误", ex);
                    isWebSocketConnected = false;

                    // 出错后也尝试重连
                    if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                        scheduleReconnect();
                    }
                }
            };

            // 设置连接超时时间
            webSocketClient.setConnectionLostTimeout(60); // 60秒检测一次连接状态

            // 连接WebSocket
            webSocketClient.connect();

        } catch (Exception e) {
            handleConnectionError(e);
        }
    }

    private void handleConnectionError(Exception e) {
        getLogger().log(Level.SEVERE, "设置WebSocket连接时发生错误", e);

        // 出错后延迟重试
        if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        reconnectAttempts++;
        long delay = Math.min(30, (long) Math.pow(2, reconnectAttempts - 1)); // 指数退避

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!isWebSocketConnected) {
                    getLogger().info("尝试重新连接WebSocket... (尝试 " + reconnectAttempts + "/" + MAX_RECONNECT_ATTEMPTS + ")");
                    connectToMiraiWebSocket();
                }
            }
        }.runTaskLater(this, 20 * delay); // delay秒后重试
    }

    // 定期检查WebSocket连接
    private void checkWebSocketConnection() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!isWebSocketConnected && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                    getLogger().info("定期检查: WebSocket连接已断开，尝试重新连接...");
                    connectToMiraiWebSocket();
                }
            }
        }.runTaskTimer(this, 20 * 60, 20 * 60); // 每分钟检查一次
    }

    // ================= 认证和绑定 =================
    private void sendAuthRequest() {
        if (!isConnectionReady(false)) {
            getLogger().warning("WebSocket未连接，无法发送验证请求");
            return;
        }

        String verifyKey = config.getString(MIRAI_VERIFY_KEY);
        if (verifyKey == null || verifyKey.isEmpty()) {
            getLogger().severe("验证密钥未设置");
            return;
        }

        JSONObject authRequest = new JSONObject();
        authRequest.put("syncId", UUID.randomUUID().toString());
        authRequest.put("command", "verify");

        JSONObject content = new JSONObject();
        content.put("verifyKey", verifyKey);
        authRequest.put("content", content);

        webSocketClient.send(authRequest.toString());
        logDebug("发送验证请求: " + authRequest);
    }

    private void sendBindRequest() {
        if (!isConnectionReady(false) || miraiSessionKey == null) {
            getLogger().warning("WebSocket未连接或未验证，无法发送绑定请求");
            return;
        }

        long botQQ = config.getLong(MIRAI_BOT_QQ);
        if (botQQ == 0) {
            getLogger().severe("机器人QQ未设置");
            return;
        }

        JSONObject bindRequest = new JSONObject();
        bindRequest.put("syncId", UUID.randomUUID().toString());
        bindRequest.put("command", "bind");

        JSONObject content = new JSONObject();
        content.put("sessionKey", miraiSessionKey);
        content.put("qq", botQQ);
        bindRequest.put("content", content);

        webSocketClient.send(bindRequest.toString());
        getLogger().info("已发送绑定请求，QQ: " + botQQ);
    }

    private void subscribeEvents() {
        if (!isConnectionReady(true)) {
            getLogger().warning("WebSocket未就绪，无法订阅事件");
            return;
        }

        JSONObject subscribeRequest = new JSONObject();
        String syncId = UUID.randomUUID().toString();
        subscribeRequest.put("syncId", syncId);
        subscribeRequest.put("command", "subscribeMessage");

        JSONObject content = new JSONObject();
        if (miraiSessionKey != null) {
            content.put("sessionKey", miraiSessionKey);
        }

        // 订阅所需的事件类型
        JSONArray eventTypes = new JSONArray();
        eventTypes.put("FriendMessage");
        eventTypes.put("GroupMessage");
        eventTypes.put("TempMessage");
        content.put("subscribeEventTypes", eventTypes);

        subscribeRequest.put("content", content);

        // 发送订阅请求
        CountDownLatch latch = new CountDownLatch(1);
        syncLocks.put(syncId, latch);

        webSocketClient.send(subscribeRequest.toString());
        getLogger().info("已发送事件订阅请求");

        try {
            // 等待响应
            boolean received = latch.await(5, TimeUnit.SECONDS);
            if (!received) {
                getLogger().warning("等待订阅响应超时");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            getLogger().warning("订阅事件等待被中断");
        } finally {
            syncLocks.remove(syncId);
        }
    }

    // ================= 消息处理 =================
    private void processMessage(JSONObject json) {
        try {
            String syncId = json.optString("syncId", "");

            // 处理同步请求的响应
            if (!syncId.isEmpty() && !syncId.equals("-1")) {
                syncResponses.put(syncId, json);
                CountDownLatch latch = syncLocks.get(syncId);
                if (latch != null) {
                    latch.countDown();
                }

                // 处理特定命令的响应
                String command = json.optString("command", "");
                if ("verify".equals(command)) {
                    handleVerifyResponse(json);
                } else if ("bind".equals(command)) {
                    handleBindResponse(json);
                } else if ("subscribeMessage".equals(command)) {
                    handleSubscribeResponse(json);
                }
            }

            // 处理验证响应 (兼容旧API)
            if (json.has("data") && !syncId.equals("-1")) {
                JSONObject data = json.optJSONObject("data");
                if (data != null) {
                    // 处理验证响应
                    if (data.has("code") && data.has("session")) {
                        int code = data.getInt("code");
                        if (code == 0) {
                            miraiSessionKey = data.getString("session");
                            getLogger().info("验证成功，获取到sessionKey: " + miraiSessionKey);
                            sendBindRequest();
                        } else {
                            getLogger().warning("验证失败，错误码: " + code + ", 消息: " + data.optString("msg", "无错误信息"));
                        }
                    }

                    // 处理绑定响应
                    if (data.has("code") && !data.has("session")) {
                        int code = data.getInt("code");
                        if (code == 0) {
                            getLogger().info("绑定成功，准备订阅事件");
                            subscribeEvents();
                        } else {
                            getLogger().warning("绑定失败，错误码: " + code + ", 消息: " + data.optString("msg", "无错误信息"));
                        }
                    }
                }
            }

            // 处理事件推送（如群消息等）
            if (syncId.equals("-1") && json.has("data")) {
                handleEvent(json);
            }
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "解析WebSocket消息时出错", e);
        }
    }

    // 处理verify命令响应
    private void handleVerifyResponse(JSONObject json) {
        try {
            JSONObject data = json.optJSONObject("data");
            if (data != null) {
                int code = data.optInt("code", -1);
                if (code == 0) {
                    miraiSessionKey = data.getString("session");
                    getLogger().info("验证成功，获取到sessionKey: " + miraiSessionKey);

                    // 添加此检查，在单会话模式下不进行绑定
                    if (!config.getBoolean(SINGLE_MODE, false)) {
                        sendBindRequest();
                    } else {
                        getLogger().info("单会话模式，跳过绑定操作");
                        // 单会话模式下直接订阅事件
                        subscribeEvents();
                    }
                } else {
                    getLogger().warning("验证失败，错误码: " + code + ", 消息: " + data.optString("msg", "无错误信息"));
                }
            }
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "处理验证响应时出错", e);
        }
    }

    // 处理bind命令响应
    private void handleBindResponse(JSONObject json) {
        try {
            JSONObject data = json.optJSONObject("data");
            if (data != null) {
                int code = data.optInt("code", -1);
                if (code == 0) {
                    getLogger().info("绑定成功，准备订阅事件");
                    subscribeEvents();
                } else {
                    getLogger().warning("绑定失败，错误码: " + code + ", 消息: " + data.optString("msg", "无错误信息"));
                }
            }
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "处理绑定响应时出错", e);
        }
    }

    // 处理订阅响应
    private void handleSubscribeResponse(JSONObject json) {
        try {
            JSONObject data = json.optJSONObject("data");
            if (data != null) {
                int code = data.optInt("code", -1);
                if (code == 0) {
                    getLogger().info("事件订阅成功，可以开始接收消息");
                } else {
                    getLogger().warning("事件订阅失败，错误码: " + code + ", 消息: " + data.optString("msg", "无错误信息"));
                }
            }
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "处理订阅响应时出错", e);
        }
    }

    // 处理事件消息
    private void handleEvent(JSONObject json) {
        // 获取事件类型
        String type = json.optString("type", "");
        if (type.isEmpty()) {
            // 尝试从data中获取类型
            JSONObject data = json.optJSONObject("data");
            if (data != null) {
                type = data.optString("type", "");
            }
        }

        // 根据事件类型分发处理
        switch (type) {
            case "GroupMessage":
                handleGroupMessage(json);
                break;
            case "FriendMessage":
                handleFriendMessage(json);
                break;
            case "TempMessage":
                handleTempMessage(json);
                break;
            default:
                logDebug("收到未处理的事件类型: " + type);
                break;
        }
    }

    // 处理群消息
    private void handleGroupMessage(JSONObject json) {
        try {
            JSONObject data = json.optJSONObject("data");
            if (data == null) return;

            // 获取发送者信息
            JSONObject sender = data.optJSONObject("sender");
            if (sender == null) return;

            long senderId = sender.optLong("id", 0);
            String senderName = sender.optString("memberName", "");

            // 获取群信息
            JSONObject group = sender.optJSONObject("group");
            if (group == null) return;

            long groupId = group.optLong("id", 0);
            String groupName = group.optString("name", "");

            // 获取消息内容
            JSONArray messageChain = data.optJSONArray("messageChain");
            if (messageChain == null) return;

            StringBuilder messageBuilder = new StringBuilder();
            for (int i = 0; i < messageChain.length(); i++) {
                JSONObject messageItem = messageChain.getJSONObject(i);
                String msgType = messageItem.optString("type", "");
                if ("Plain".equals(msgType)) {
                    messageBuilder.append(messageItem.optString("text", ""));
                }
                // 可以处理其他类型的消息
            }

            String messageContent = messageBuilder.toString();
            logDebug("群消息: 群=" + groupName + "(" + groupId + "), 发送者=" + senderName + "(" + senderId + "), 内容=" + messageContent);

            // 处理群消息的业务逻辑，例如白名单申请命令
            // processGroupCommand(groupId, senderId, senderName, messageContent);

        } catch (Exception e) {
            getLogger().log(Level.WARNING, "处理群消息时出错", e);
        }
    }

    // 处理好友消息
    private void handleFriendMessage(JSONObject json) {
        try {
            JSONObject data = json.optJSONObject("data");
            if (data == null) return;

            // 获取发送者信息
            JSONObject sender = data.optJSONObject("sender");
            if (sender == null) return;

            long senderId = sender.optLong("id", 0);
            String senderName = sender.optString("nickname", "");

            // 获取消息内容
            JSONArray messageChain = data.optJSONArray("messageChain");
            if (messageChain == null) return;

            StringBuilder messageBuilder = new StringBuilder();
            for (int i = 0; i < messageChain.length(); i++) {
                JSONObject messageItem = messageChain.getJSONObject(i);
                String msgType = messageItem.optString("type", "");
                if ("Plain".equals(msgType)) {
                    messageBuilder.append(messageItem.optString("text", ""));
                }
            }

            String messageContent = messageBuilder.toString();
            logDebug("好友消息: 发送者=" + senderName + "(" + senderId + "), 内容=" + messageContent);

            // 处理好友消息的业务逻辑

        } catch (Exception e) {
            getLogger().log(Level.WARNING, "处理好友消息时出错", e);
        }
    }

    // 处理临时消息
    private void handleTempMessage(JSONObject json) {
        try {
            JSONObject data = json.optJSONObject("data");
            if (data == null) return;

            // 获取发送者信息
            JSONObject sender = data.optJSONObject("sender");
            if (sender == null) return;

            long senderId = sender.optLong("id", 0);
            String senderName = sender.optString("memberName", "");

            // 获取群信息
            JSONObject group = sender.optJSONObject("group");
            if (group == null) return;

            long groupId = group.optLong("id", 0);

            // 获取消息内容
            JSONArray messageChain = data.optJSONArray("messageChain");
            if (messageChain == null) return;

            StringBuilder messageBuilder = new StringBuilder();
            for (int i = 0; i < messageChain.length(); i++) {
                JSONObject messageItem = messageChain.getJSONObject(i);
                String msgType = messageItem.optString("type", "");
                if ("Plain".equals(msgType)) {
                    messageBuilder.append(messageItem.optString("text", ""));
                }
            }

            String messageContent = messageBuilder.toString();
            logDebug("临时消息: 发送者=" + senderName + "(" + senderId + "), 群=" + groupId + ", 内容=" + messageContent);

            // 处理临时消息的业务逻辑

        } catch (Exception e) {
            getLogger().log(Level.WARNING, "处理临时消息时出错", e);
        }
    }

    // ================= Web服务器 =================
    private void startWebServer() {
        try {
            int port = config.getInt(SERVER_PORT, 25565);
            server = HttpServer.create(new InetSocketAddress(port), 0);

            // 注册处理器
            server.createContext("/sendCaptcha", new SendCaptchaHandler());
            server.createContext("/apply", new ApplyHandler());
            server.createContext("/", new StaticFileHandler());

            // 设置执行器 - 避免可能的NPE
            server.setExecutor(null);
            server.start();
            getLogger().info("HTTP服务已启动，端口: " + port);
        } catch (IOException e) {
            getLogger().severe("无法启动HTTP服务器: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void stopWebServer() {
        if (server != null) {
            server.stop(1);
            getLogger().info("HTTP服务已关闭");
        }
    }

    // ================= 发送消息 =================
    private JSONObject buildMessageRequest(String command, long targetId, String message) {
        JSONObject requestMsg = new JSONObject();
        requestMsg.put("command", command);
        String syncId = UUID.randomUUID().toString();
        requestMsg.put("syncId", syncId);

        JSONObject content = new JSONObject();
        if (miraiSessionKey != null && !config.getBoolean(SINGLE_MODE, false)) {
            content.put("sessionKey", miraiSessionKey);
        }
        content.put("target", targetId);

        JSONArray messageChain = new JSONArray();
        JSONObject textMsg = new JSONObject();
        textMsg.put("type", "Plain");
        textMsg.put("text", message);
        messageChain.put(textMsg);

        content.put("messageChain", messageChain);
        requestMsg.put("content", content);
        return requestMsg;
    }

    private JSONObject buildGroupMessageRequest(long groupId, String message) {
        return buildMessageRequest("sendGroupMessage", groupId, message);
    }

    public boolean sendGroupMessage(long groupId, String message) {
        if (!config.getBoolean(REQUIRE_QQ_VERIFICATION, false)) {
            getLogger().warning("QQ验证未启用，无法发送群消息");
            return false;
        }

        if (!isConnectionReady(true)) {
            getLogger().warning("WebSocket未就绪，无法发送群消息");
            return false;
        }

        try {
            JSONObject requestMsg = buildMessageRequest("sendGroupMessage", groupId, message);
            String syncId = requestMsg.getString("syncId");

            CountDownLatch latch = new CountDownLatch(1);
            syncLocks.put(syncId, latch);

            webSocketClient.send(requestMsg.toString());
            logDebug("发送群消息请求: " + requestMsg);

            // 等待响应
            boolean received = latch.await(5, TimeUnit.SECONDS);
            syncLocks.remove(syncId);

            if (!received) {
                getLogger().warning("发送群消息超时");
                return false;
            }

            JSONObject response = syncResponses.remove(syncId);
            return handleSendResponse(response, groupId);

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "发送群消息异常", e);
            return false;
        }
    }

    private boolean handleSendResponse(JSONObject response, long groupId) {
        if (response == null) {
            return false;
        }

        try {
            JSONObject data = response.optJSONObject("data");
            if (data != null) {
                int code = data.optInt("code", -1);
                if (code == 0) {
                    getLogger().info("成功发送消息到群: " + groupId);
                    return true;
                } else {
                    getLogger().warning("发送消息失败，错误码: " + code + ", 消息: " + data.optString("msg", "无错误信息"));
                }
            }
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "处理发送响应时出错", e);
        }

        return false;
    }

    // 发送好友消息
    public boolean sendFriendMessage(long friendId, String message) {
        if (!config.getBoolean(REQUIRE_QQ_VERIFICATION, false)) {
            getLogger().warning("QQ验证未启用，无法发送好友消息");
            return false;
        }

        if (!isConnectionReady(true)) {
            getLogger().warning("WebSocket未就绪，无法发送好友消息");
            return false;
        }

        try {
            JSONObject requestMsg = buildMessageRequest("sendFriendMessage", friendId, message);
            String syncId = requestMsg.getString("syncId");

            CountDownLatch latch = new CountDownLatch(1);
            syncLocks.put(syncId, latch);

            webSocketClient.send(requestMsg.toString());
            logDebug("发送好友消息请求: " + requestMsg);

            // 等待响应
            boolean received = latch.await(5, TimeUnit.SECONDS);
            syncLocks.remove(syncId);

            if (!received) {
                getLogger().warning("发送好友消息超时");
                return false;
            }

            JSONObject response = syncResponses.remove(syncId);
            if (response != null) {
                JSONObject data = response.optJSONObject("data");
                if (data != null && data.optInt("code", -1) == 0) {
                    getLogger().info("成功发送消息给好友: " + friendId);
                    return true;
                }
            }

            getLogger().warning("发送好友消息失败");
            return false;

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "发送好友消息异常", e);
            return false;
        }
    }

    // ================= 业务逻辑 =================
    private void addToWhitelist(String playerName) {
        new BukkitRunnable() {
            @Override
            public void run() {
                String commandTemplate = config.getString(WHITELIST_CMD, "");
                // 判断白名单命令是否为空字符串
                if (commandTemplate.isEmpty()) {
                    getLogger().severe("白名单命令未设置！");
                    return;
                }
                String command = commandTemplate.replace("%player%", playerName);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                getLogger().info("已执行白名单命令: " + command);
            }
        }.runTask(this);
    }

    // ================= QQ群成员验证 =================
    private boolean verifyGroupMember(String qq) {
        // 如果不需要QQ验证，则直接通过
        if (!config.getBoolean(REQUIRE_QQ_VERIFICATION, false)) {
            return true;
        }

        // 首先尝试通过本地文件验证
        if (verifyGroupMemberFromFile(qq)) {
            return true;
        }

        // 如果本地验证失败且WebSocket连接可用，则尝试通过WebSocket验证
        try {
            if (!isConnectionReady(true)) {
                getLogger().warning("WebSocket未就绪，无法验证QQ群成员");
                return false;
            }

            long groupId = config.getLong(MIRAI_GROUP_ID);
            if (groupId == 0) {
                getLogger().warning("QQ群号未设置");
                return false;
            }

            JSONObject request = new JSONObject();
            request.put("command", "memberList");
            String syncId = UUID.randomUUID().toString();
            request.put("syncId", syncId);

            JSONObject content = new JSONObject();
            if (miraiSessionKey != null && !config.getBoolean(SINGLE_MODE, false)) {
                content.put("sessionKey", miraiSessionKey);
            }
            content.put("target", groupId);
            request.put("content", content);

            CountDownLatch latch = new CountDownLatch(1);
            syncLocks.put(syncId, latch);

            webSocketClient.send(request.toString());
            logDebug("发送获取群成员列表请求: " + request);

            // 等待响应
            boolean received = latch.await(10, TimeUnit.SECONDS);
            syncLocks.remove(syncId);

            if (!received) {
                getLogger().warning("获取群成员列表超时");
                return false;
            }

            JSONObject response = syncResponses.remove(syncId);
            if (response == null) {
                getLogger().warning("获取群成员列表失败，返回数据为空");
                return false;
            }

            // 解析响应数据，查找QQ号码
            JSONArray members;
            if (response.has("data")) {
                Object dataObj = response.get("data");
                if (dataObj instanceof JSONArray) {
                    members = (JSONArray) dataObj;
                } else if (dataObj instanceof JSONObject data) {
                    int code = data.optInt("code", -1);
                    if (code != 0) {
                        getLogger().warning("获取群成员列表失败，错误码: " + code + ", 消息: " + data.optString("msg", "无错误信息"));
                        return false;
                    }
                    members = data.optJSONArray("data");
                    if (members == null) {
                        getLogger().warning("获取群成员列表成功但数据格式不正确");
                        return false;
                    }
                } else {
                    getLogger().warning("获取群成员列表数据格式不正确");
                    return false;
                }
            } else {
                getLogger().warning("获取群成员列表返回数据格式不正确");
                return false;
            }

            // 查找目标QQ号
            for (int i = 0; i < members.length(); i++) {
                JSONObject member = members.getJSONObject(i);
                if (String.valueOf(member.optLong("id", 0)).equals(qq)) {
                    // 如果找到，更新本地文件
                    updateGroupMemberFile(members);
                    return true;
                }
            }

            return false;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "验证QQ群成员时出错", e);
            return false;
        }
    }

    // 从本地文件验证QQ群成员
    private boolean verifyGroupMemberFromFile(String qq) {
        String fileName = config.getString(QQ_GROUP_MEMBER_FILE, "members.json");
        File memberFile = new File(getDataFolder(), fileName);

        if (!memberFile.exists()) {
            logDebug("群成员文件不存在，无法进行本地验证");
            return false;
        }

        try {
            JSONArray members = new JSONArray(java.nio.file.Files.readString(memberFile.toPath()));

            for (int i = 0; i < members.length(); i++) {
                JSONObject member = members.getJSONObject(i);
                if (String.valueOf(member.optLong("id", 0)).equals(qq)) {
                    logDebug("从本地文件成功验证QQ群成员: " + qq);
                    return true;
                }
            }

            logDebug("QQ号 " + qq + " 不在本地群成员列表中");
            return false;
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "从本地文件验证QQ群成员时出错", e);
            return false;
        }
    }

    // 更新本地群成员文件
    private void updateGroupMemberFile(JSONArray members) {
        String fileName = config.getString(QQ_GROUP_MEMBER_FILE, "members.json");
        File memberFile = new File(getDataFolder(), fileName);

        try {
            java.nio.file.Files.writeString(memberFile.toPath(), members.toString(2));
            getLogger().info("已更新群成员列表文件，共 " + members.length() + " 个成员");
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "更新群成员文件时出错", e);
        }
    }

    // ================= 邮件发送 =================
    private boolean sendVerificationEmail(String email, String captcha) {
        try {
            // 设置邮件属性
            Properties properties = new Properties();
            properties.put("mail.smtp.auth", "true");
            properties.put("mail.smtp.host", config.getString(EMAIL_HOST));
            properties.put("mail.smtp.port", config.getString(EMAIL_PORT));

            // 检查端口，如果是465则使用SSL
            if ("465".equals(config.getString(EMAIL_PORT))) {
                properties.put("mail.smtp.socketFactory.port", "465");
                properties.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
            } else {
                properties.put("mail.smtp.starttls.enable", "true");
            }

            // 创建会话
            final String username = config.getString(EMAIL_USERNAME);
            final String password = config.getString(EMAIL_PASSWORD);

            Session session = Session.getInstance(properties, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(username, password);
                }
            });

            // 开启调试模式（仅在调试时启用）
            session.setDebug(config.getBoolean(DEBUG_MODE, false));

            // 创建邮件
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(Objects.requireNonNull(config.getString(EMAIL_FROM, username))));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(email));
            message.setSubject(config.getString(EMAIL_SUBJECT, "Minecraft服务器白名单验证码"));

            // 邮件内容
            String signature = config.getString(EMAIL_SIGNATURE, "Minecraft服务器管理团队");
            String content = "您好，\n\n"
                    + "您的Minecraft服务器白名单验证码是: " + captcha + "\n\n"
                    + "此验证码将在 " + config.getInt("captcha.expiry_minutes", 5) + " 分钟后失效。\n\n"
                    + "如果您没有申请白名单，请忽略此邮件。\n\n"
                    + "祝您游戏愉快！\n"
                    + signature;

            message.setText(content);

            // 发送邮件
            Transport.send(message);

            getLogger().info("验证码邮件已发送至: " + email);
            return true;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "发送邮件失败", e);
            return false;
        }
    }

    // ================= 工具方法 =================
    // 检查连接是否就绪
    private boolean isConnectionReady(boolean needSession) {
        if (!config.getBoolean(REQUIRE_QQ_VERIFICATION, false)) {
            return false;
        }

        boolean ready = isWebSocketConnected && webSocketClient != null && webSocketClient.isOpen();

        if (needSession) {
            // 如果需要会话且不是单会话模式，则还需要检查sessionKey
            if (!config.getBoolean(SINGLE_MODE, false)) {
                ready = ready && miraiSessionKey != null;
            }
        }

        return ready;
    }

    private String generateCaptcha() {
        int length = config.getInt("captcha.length", 6);
        int max = (int) Math.pow(10, length) - 1;
        return String.format("%0" + length + "d", ThreadLocalRandom.current().nextInt(0, max));
    }

    // ================= HTTP处理器 =================
    class SendCaptchaHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendErrorResponse(exchange, 405, "Method Not Allowed");
                    return;
                }

                Map<String, String> params = parseFormData(exchange);
                String qqNumber = params.get("qqNumber");
                String email = params.get("email");

                // 参数验证
                if (qqNumber == null || qqNumber.isEmpty()) {
                    sendErrorResponse(exchange, 400, "QQ号不能为空");
                    return;
                }

                // 邮箱验证
                if (email == null || email.isEmpty()) {
                    sendErrorResponse(exchange, 400, "请输入邮箱地址");
                    return;
                }

                // 简单验证邮箱格式
                if (!email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
                    sendErrorResponse(exchange, 400, "邮箱格式不正确");
                    return;
                }

                // 检查群成员 - 使用配置的错误信息
                if (config.getBoolean(REQUIRE_QQ_VERIFICATION, false)) {
                    if (!verifyGroupMember(qqNumber)) {
                        String errorMsg = config.getString(QQ_ERROR_MESSAGE, "请先加入服务器指定的QQ群！");
                        sendErrorResponse(exchange, 403, errorMsg);
                        return;
                    }
                }

                // 生成验证码
                String captcha = generateCaptcha();
                long expiryTime = System.currentTimeMillis() +
                        ((long) config.getInt("captcha.expiry_minutes", 5) * 60 * 1000);

                captchaStore.put(qqNumber, new CaptchaInfo(captcha, expiryTime, email));

                // 发送验证码到邮箱
                boolean emailSent = sendVerificationEmail(email, captcha);

                if (emailSent) {
                    sendSuccessResponse(exchange, "验证码已发送到邮箱: " + maskEmail(email));
                } else {
                    sendErrorResponse(exchange, 500, "发送验证码邮件失败，请检查邮箱地址或稍后再试");
                }

            } catch (Exception e) {
                getLogger().log(Level.WARNING, "验证码处理异常", e);
                sendErrorResponse(exchange, 500, "服务器内部错误");
            }
        }

        private String maskEmail(String email) {
            // 隐藏部分邮箱地址以保护隐私
            if (email == null || email.length() <= 4 || !email.contains("@")) {
                return email;
            }

            int atIndex = email.indexOf('@');
            String username = email.substring(0, atIndex);
            String domain = email.substring(atIndex);

            // 如果用户名很短，只显示第一个字符
            if (username.length() <= 2) {
                return username.charAt(0) + "***" + domain;
            }

            // 否则显示第一个和最后一个字符
            return username.charAt(0) + "***" + username.charAt(username.length() - 1) + domain;
        }
    }

    class ApplyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                Map<String, String> params = parseFormData(exchange);
                String qq = params.get("qqNumber");
                String playerName = params.get("playerName");
                String inputCaptcha = params.get("captcha");

                // 参数校验
                if (anyEmpty(qq, playerName, inputCaptcha)) {
                    sendErrorResponse(exchange, 400, "所有字段必须填写");
                    return;
                }

                CaptchaInfo stored = captchaStore.get(qq);
                if (stored == null) {
                    sendErrorResponse(exchange, 400, "请先获取验证码");
                    return;
                }

                if (System.currentTimeMillis() > stored.expiryTime) {
                    captchaStore.remove(qq);
                    sendErrorResponse(exchange, 400, "验证码已过期");
                    return;
                }

                if (!stored.captcha.equals(inputCaptcha)) {
                    sendErrorResponse(exchange, 400, "验证码错误");
                    return;
                }

                // 添加白名单
                addToWhitelist(playerName);

                // 仅当QQ验证已启用且配置允许QQ通知时才通过WebSocket通知用户
                if (config.getBoolean(REQUIRE_QQ_VERIFICATION, false) &&
                        config.getBoolean(NOTIFY_VIA_QQ, false) &&
                        isConnectionReady(true)) {

                    sendFriendMessage(Long.parseLong(qq), "您的Minecraft白名单申请已通过，游戏名: " + playerName);

                    // 同时在群内通知
                    long groupId = config.getLong(MIRAI_GROUP_ID);
                    if (groupId != 0) {
                        sendGroupMessage(groupId, "用户 " + playerName + " 的白名单申请已通过！");
                    }
                }

                captchaStore.remove(qq);
                sendSuccessResponse(exchange, "白名单添加成功");
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "申请处理异常", e);
                sendErrorResponse(exchange, 500, "服务器内部错误");
            }
        }

        private boolean anyEmpty(String... fields) {
            for (String field : fields) {
                if (field == null || field.isEmpty()) {
                    return true;
                }
            }
            return false;
        }
    }

    class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) path = "/index.html";

            try (InputStream is = getClass().getResourceAsStream("web" + path)) {
                if (is == null) {
                    sendErrorResponse(exchange, 404, "资源未找到");
                    return;
                }

                // 读取文件内容
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] data = new byte[4096];
                int bytesRead;
                while ((bytesRead = is.read(data)) != -1) {
                    buffer.write(data, 0, bytesRead);
                }

                // 设置MIME类型
                String contentType = "text/html";
                if (path.endsWith(".css")) contentType = "text/css";
                else if (path.endsWith(".js")) contentType = "application/javascript";

                exchange.getResponseHeaders().set("Content-Type", contentType);
                exchange.sendResponseHeaders(200, buffer.size());
                try (OutputStream os = exchange.getResponseBody()) {
                    buffer.writeTo(os);
                }
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "处理静态文件请求异常", e);
                sendErrorResponse(exchange, 500, "服务器内部错误");
            }
        }
    }

    // ================= HTTP工具方法 =================
    private Map<String, String> parseFormData(HttpExchange exchange) throws IOException {
        Map<String, String> params = new HashMap<>();
        String query = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=");
            if (kv.length == 2) {
                params.put(kv[0], URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
            }
        }
        return params;
    }

    private static void sendSuccessResponse(HttpExchange exchange, String message) throws IOException {
        sendResponse(exchange, 200, message);
    }

    private static void sendErrorResponse(HttpExchange exchange, int code, String error) throws IOException {
        sendResponse(exchange, code, "错误: " + error);
    }

    private static void sendResponse(HttpExchange exchange, int code, String message) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        byte[] responseBytes = message.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }

    // ================= 数据结构 =================
    static class CaptchaInfo {
        String captcha;
        long expiryTime;
        String email;

        CaptchaInfo(String captcha, long expiryTime) {
            this.captcha = captcha;
            this.expiryTime = expiryTime;
            this.email = null;
        }

        CaptchaInfo(String captcha, long expiryTime, String email) {
            this.captcha = captcha;
            this.expiryTime = expiryTime;
            this.email = email;
        }
    }
}