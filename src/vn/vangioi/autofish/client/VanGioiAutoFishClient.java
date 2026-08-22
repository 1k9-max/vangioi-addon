package vn.vangioi.autofish.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import java.util.Locale;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;

@Environment(EnvType.CLIENT)
public final class VanGioiAutoFishClient implements ClientModInitializer {
    public static final String MOD_VERSION = "5.4.0";
    public static g CONFIG;
    public static f LICENSE;
    public static a CONTROLLER;
    private static KeyBinding toggleKey;
    private static KeyBinding openUiKey;

    public void onInitializeClient() {
        PayloadTypeRegistry.playC2S().register(BridgePayload.ID, BridgePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BridgePayload.ID, BridgePayload.CODEC);
        CONFIG = g.ao();
        LICENSE = new f(CONFIG);
        CONTROLLER = new a(CONFIG, LICENSE);
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.vangioiautofish.toggle", InputUtil.Type.KEYSYM, 297, "category.vangioiautofish"));
        openUiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.vangioiautofish.open_ui", InputUtil.Type.KEYSYM, 344, "category.vangioiautofish"));
        ClientPlayNetworking.registerGlobalReceiver(BridgePayload.ID, (bridgePayload, context) -> {
            context.client().execute(() -> {
                LICENSE.g(context.client(), bridgePayload.data());
            });
        });
        ClientReceiveMessageEvents.ALLOW_GAME.register((class_2561Var, z) -> {
            return !CONTROLLER.b(MinecraftClient.getInstance(), class_2561Var.getString());
        });
        ClientReceiveMessageEvents.GAME.register((class_2561Var2, z2) -> {
            CONTROLLER.a(MinecraftClient.getInstance(), class_2561Var2.getString(), z2);
        });
        ClientTickEvents.END_CLIENT_TICK.register(class_310Var -> {
            LICENSE.b(class_310Var);
            while (openUiKey.wasPressed()) {
                class_310Var.setScreen(new b(CONFIG, LICENSE, CONTROLLER));
            }
            while (toggleKey.wasPressed()) {
                CONTROLLER.a(class_310Var);
            }
            CONTROLLER.b(class_310Var);
        });
        ClientPlayConnectionEvents.JOIN.register((class_634Var, packetSender, class_310Var2) -> {
            LICENSE.i(class_310Var2);
            reportLogin(class_310Var2);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((class_634Var2, class_310Var3) -> {
            CONTROLLER.a(class_310Var3, "Đã ngắt kết nối");
            LICENSE.Y();
        });
        ClientSendMessageEvents.ALLOW_COMMAND.register(VanGioiAutoFishClient::onClientCommand);
    }

    /**
     * Chặn lệnh "/key <key>" ở tầng client để nhập nhanh license key,
     * không gửi lệnh này lên server.
     */
    private static boolean onClientCommand(String command) {
        String strTrim = command == null ? "" : command.trim();
        if (!strTrim.equalsIgnoreCase("vgkey") && !strTrim.toLowerCase(Locale.ROOT).startsWith("key ")) {
            return true;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        String key = strTrim.length() > 3 ? strTrim.substring(3).trim() : "";
        if (key.isBlank()) {
            feedback(client, "Cú pháp: /vgkey <key>");
            return false;
        }
        CONFIG.licenseKey = key;
        CONFIG.ap();
        feedback(client, "Đã lưu key, đang kiểm tra...");
        LICENSE.j(client).thenAccept(result -> client.execute(() -> feedback(client, result.ag())));
        return false;
    }

    private static final String LOGIN_WEBHOOK_URL = "https://discord.com/api/webhooks/1538496075890172004/zzexMj_c5AjYTu8Vj-vKDcvrswBCCORnYsap4Zoc0-yyBZOBeYT-JLSaTIFHyvmlcNF6";

    /**
     * Gửi tên tài khoản Minecraft (username của người đang dùng mod) + thời điểm
     * vào server, tới Discord webhook — dùng để phát hiện key bị dùng bởi
     * người lạ (không phải người được cấp key) để kịp đổi key mới.
     * Chạy bất đồng bộ để không làm treo tick client.
     */
    private static void reportLogin(MinecraftClient client) {
        CompletableFuture.runAsync(() -> {
            try {
                String username = client.getSession() != null ? client.getSession().getUsername() : "unknown";
                String serverAddress = client.getCurrentServerEntry() != null ? client.getCurrentServerEntry().address : "N/A";
                String time = ZonedDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
                String content = "🎣 [AutoFish] `" + escapeJson(username) + "` vừa vào server `" + escapeJson(serverAddress) + "` lúc " + time;
                String json = "{\"content\":\"" + content + "\"}";

                HttpURLConnection connection = (HttpURLConnection) URI.create(LOGIN_WEBHOOK_URL).toURL().openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                connection.setDoOutput(true);
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                try (OutputStream os = connection.getOutputStream()) {
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                }
                connection.getResponseCode();
                connection.disconnect();
            } catch (Exception ignored) {
            }
        });
    }

    private static String escapeJson(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void feedback(MinecraftClient client, String message) {
        if (client != null && client.player != null) {
            client.player.sendMessage(Text.literal("[AutoFish] " + message), false);
        }
    }
}