package vn.vangioi.autofish.client;

import java.awt.Desktop;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class f {
    // TẠM THỜI TẮT KẾT NỐI SERVER BRIDGE — chỉ còn xác minh key qua GitHub.
    // Muốn bật lại kết nối server, đổi giá trị này thành true.
    private static final boolean SERVER_BRIDGE_ENABLED = false;

    private static final String aP = "MCowBQYDK2VwAyEAUgtSeudd9GRldGZM/30bl21tNkzxtmv9nDeMPRZSeQg=";
    private static final String aQ = "vgaf-2104-bridge-v54";
    private static final String GITHUB_LICENSES_URL = "https://key-eosin-theta.vercel.app/key/public/licenses.json";
    private static final long aR = 3000000000L;
    private static final long aS = 120000000000L;
    private final g aT;
    private volatile boolean aV;
    private volatile long aZ;
    private volatile long ba;
    private final AtomicReference<b> aU = new AtomicReference<>(new b(c.UNCHECKED, a.NONE, "Chưa kết nối License Bridge", "", 0, 0, 0, 0));
    private volatile String aW = "";
    private volatile int aX = 12;
    private volatile String aY = "";

    public enum a {
        NONE, PAID, FREE, OWNER
    }

    public record b(c bg, a bh, String bi, String bj, long bk, long bl, long bm, long bn) {

        public boolean ad() {
            return this.bg == c.VALID && this.bn > 0 && System.nanoTime() < this.bn;
        }

        public c ae() { return this.bg; }
        public a af() { return this.bh; }
        public String ag() { return this.bi; }
        public String ah() { return this.bj; }
        public long ai() { return this.bk; }
        public long aj() { return this.bl; }
        public long ak() { return this.bm; }
        public long al() { return this.bn; }
    }

    public enum c {
        UNCHECKED, CONNECTING, CHECKING, VALID, EXPIRED, INVALID, ERROR
    }

    public f(g gVar) {
        this.aT = gVar;
    }

    // Yêu cầu nhập key trở lại (đã bật lại check key GitHub/Bridge).
    private static final boolean REQUIRE_LICENSE_KEY = true;

    public boolean R() { return false; }
    public b S() { return this.aU.get(); }
    public boolean T() { return this.aV; }
    public String U() { return this.aW; }
    public int V() { return this.aX; }

    public boolean W() {
        if (!REQUIRE_LICENSE_KEY) {
            return true;
        }
        return this.aU.get().ad() && this.ba != 0;
    }

    public long X() {
        if (!REQUIRE_LICENSE_KEY) {
            return -1L;
        }
        if (W()) {
            return this.ba;
        }
        return 0L;
    }

    /**
     * Phương thức xác minh Key trực tiếp qua GitHub JSON
     */
    public CompletableFuture<b> verifyWithGithub(String inputKey) {
        return CompletableFuture.supplyAsync(() -> {
            String strTrim = (inputKey == null) ? "" : inputKey.trim();
            if (strTrim.isBlank()) {
                b res = b(a.PAID, "Key không được để trống");
                this.aU.set(res);
                return res;
            }

            this.aU.set(a(a.PAID, "Đang kiểm tra key trên GitHub..."));

            try {
                URL url = new URL(GITHUB_LICENSES_URL);
                URLConnection connection = url.openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                try (InputStreamReader reader = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
                    JsonObject rootObject = JsonParser.parseReader(reader).getAsJsonObject();

                    if (rootObject.has("keys")) {
                        JsonObject keysMap = rootObject.getAsJsonObject("keys");
                        String hashedKey = sha256Hex(strTrim);

                        if (keysMap.has(hashedKey)) {
                            JsonObject keyInfo = keysMap.getAsJsonObject(hashedKey);
                            long expiresAt = keyInfo.has("expires") ? keyInfo.get("expires").getAsLong() : 0L;
                            String typeStr = keyInfo.has("type") ? keyInfo.get("type").getAsString() : "PAID";
                            a kind = "FREE".equalsIgnoreCase(typeStr) ? a.FREE : a.PAID;

                            long nowSec = Instant.now().getEpochSecond();

                            // expires = 0 có nghĩa là vĩnh viễn (không hết hạn)
                            if (expiresAt > 0 && nowSec > expiresAt) {
                                b expiredResult = new b(c.EXPIRED, kind, "Key trên GitHub đã hết hạn", strTrim, expiresAt, nowSec, nowSec, 0L);
                                this.aU.set(expiredResult);
                                return expiredResult;
                            }

                            long leaseUntil = (expiresAt == 0) ? nowSec + 8640000 : expiresAt;
                            long leaseDeadlineNanos = System.nanoTime() + (86400L * 1_000_000_000L);

                            b validResult = new b(c.VALID, kind, "Xác minh key GitHub thành công!", strTrim, expiresAt, leaseUntil, nowSec, leaseDeadlineNanos);
                            this.ba = a("github_sig", strTrim, expiresAt);
                            this.aU.set(validResult);
                            return validResult;
                        }
                    }
                }

                b invalidRes = b(a.PAID, "Key không tồn tại trên GitHub");
                this.aU.set(invalidRes);
                return invalidRes;

            } catch (Exception e) {
                b errRes = new b(c.ERROR, a.NONE, "Lỗi kết nối GitHub: " + e.getMessage(), "", 0L, 0L, Instant.now().getEpochSecond(), 0L);
                this.aU.set(errRes);
                return errRes;
            }
        });
    }

    public CompletableFuture<b> j(MinecraftClient class_310Var) {
        String strTrim = this.aT.licenseKey == null ? "" : this.aT.licenseKey.trim();
        if (strTrim.isBlank()) {
            b bVarB = b(a.PAID, "Chưa nhập key trả phí");
            this.aU.set(bVarB);
            return CompletableFuture.completedFuture(bVarB);
        }

        // Kiểm tra qua GitHub trước
        return verifyWithGithub(strTrim).thenCompose(result -> {
            if (result.ae() == c.VALID) {
                return CompletableFuture.completedFuture(result);
            }

            if (!SERVER_BRIDGE_ENABLED) {
                // Server Bridge đang tắt tạm thời: dùng thẳng kết quả GitHub (không fallback qua server).
                return CompletableFuture.completedFuture(result);
            }

            // Fallback về kiểm tra qua Server Bridge nếu GitHub không xác minh thành công
            if (!this.aV) {
                m(class_310Var);
                b bVarB2 = b(a.PAID, "Server chưa phản hồi Bridge");
                this.aU.set(bVarB2);
                return CompletableFuture.completedFuture(bVarB2);
            }
            String strAa = aa();
            this.aY = strAa;
            this.aU.set(a(a.PAID, "Đang xác minh key qua server..."));
            h(class_310Var, vn.vangioi.autofish.client.c.a("PAID", this.aT.installationId, strAa, aQ, strTrim));
            return CompletableFuture.completedFuture(this.aU.get());
        });
    }

    public void i(MinecraftClient class_310Var) {
        if (!SERVER_BRIDGE_ENABLED) {
            this.aV = false;
            this.aU.set(new b(c.UNCHECKED, a.NONE, "Server Bridge đang tắt tạm thời — chỉ dùng key GitHub", "", 0L, 0L, Instant.now().getEpochSecond(), 0L));
            return;
        }
        this.aV = false;
        this.aW = "";
        this.aY = "";
        this.ba = 0L;
        this.aU.set(new b(c.CONNECTING, a.NONE, "Đang tìm License Bridge trên server...", "", 0L, 0L, Instant.now().getEpochSecond(), 0L));
        this.aZ = 0L;
        m(class_310Var);
    }

    public void Y() {
        this.aV = false;
        this.aY = "";
        this.ba = 0L;
        this.aU.set(new b(c.UNCHECKED, a.NONE, "Đã ngắt kết nối server", "", 0L, 0L, Instant.now().getEpochSecond(), 0L));
    }

    public void b(MinecraftClient class_310Var) {
        if (!SERVER_BRIDGE_ENABLED) {
            return;
        }
        if (class_310Var == null || class_310Var.getNetworkHandler() == null) {
            return;
        }
        long jNanoTime = System.nanoTime();
        if (!this.aV) {
            if (this.aZ == 0 || jNanoTime - this.aZ >= aR) {
                m(class_310Var);
                return;
            }
            return;
        }
        b bVar = this.aU.get();
        if (bVar.ae() == c.VALID && bVar.al() > 0 && jNanoTime >= bVar.al()) {
            boolean z = bVar.ai() > 0 && bVar.aj() >= bVar.ai();
            if (this.aU.compareAndSet(bVar, new b(z ? c.EXPIRED : c.ERROR, bVar.af(), z ? "HẾT HẠN · Gia hạn hoặc nhập license mới" : "Phiên xác minh đã hết hiệu lực", bVar.ah(), bVar.ai(), bVar.aj(), Instant.now().getEpochSecond(), 0L))) {
                this.aY = "";
                this.ba = 0L;
                if (class_310Var.player != null) {
                    class_310Var.player.sendMessage(Text.literal(z ? "[AutoFish] License đã hết hạn. Auto đã bị dừng." : "[AutoFish] Phiên hết hiệu lực. Auto đã bị dừng."), false);
                    return;
                }
                return;
            }
            return;
        }
        if (!bVar.ad() || bVar.aj() >= bVar.ai() || bVar.al() - jNanoTime > aS || !this.aY.isBlank()) {
            return;
        }
        if (bVar.af() != a.PAID || this.aT.licenseKey == null || this.aT.licenseKey.isBlank()) {
            a(class_310Var, bVar);
        } else {
            l(class_310Var);
        }
    }

    public CompletableFuture<b> f(MinecraftClient class_310Var, String str) {
        String strTrim = str == null ? "" : str.trim();
        if (strTrim.isBlank()) {
            b bVarB = b(a.FREE, "Chưa nhập token Work.ink");
            this.aU.set(bVarB);
            return CompletableFuture.completedFuture(bVarB);
        }
        if (!SERVER_BRIDGE_ENABLED) {
            b bVarDisabled = b(a.FREE, "Server Bridge đang tắt tạm thời — không thể xác minh token Work.ink");
            this.aU.set(bVarDisabled);
            return CompletableFuture.completedFuture(bVarDisabled);
        }
        if (!this.aV) {
            m(class_310Var);
            b bVarB2 = b(a.FREE, "Server chưa phản hồi Bridge");
            this.aU.set(bVarB2);
            return CompletableFuture.completedFuture(bVarB2);
        }
        String strAa = aa();
        this.aY = strAa;
        this.aU.set(a(a.FREE, "Server đang kiểm token Work.ink..."));
        h(class_310Var, vn.vangioi.autofish.client.c.a("FREE", this.aT.installationId, strAa, aQ, strTrim));
        return CompletableFuture.completedFuture(this.aU.get());
    }

    public void k(MinecraftClient class_310Var) {
        if (!this.aV || this.aW.isBlank()) {
            m(class_310Var);
            this.aU.set(b(a.FREE, "Chưa nhận được link Work.ink từ server"));
            return;
        }
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(URI.create(this.aW));
            }
            this.aU.set(new b(c.UNCHECKED, a.FREE, "Hoàn thành Work.ink rồi copy token vào ô bên dưới", "", 0L, 0L, Instant.now().getEpochSecond(), 0L));
        } catch (Exception e) {
            this.aU.set(new b(c.ERROR, a.FREE, "Không mở được trình duyệt", "", 0L, 0L, Instant.now().getEpochSecond(), 0L));
        }
    }

    public void Z() {
        this.aT.licenseKey = "";
        this.aT.ap();
        if (this.aU.get().af() == a.PAID) {
            this.ba = 0L;
            this.aU.set(new b(c.UNCHECKED, a.NONE, "Đã xóa key trả phí", "", 0L, 0L, Instant.now().getEpochSecond(), 0L));
        }
    }

    public void g(MinecraftClient class_310Var, String str) {
        vn.vangioi.autofish.client.c.a aVarC = vn.vangioi.autofish.client.c.c(str);
        if (aVarC == null) return;
        switch (aVarC.y()) {
            case "CONFIG":
                a(class_310Var, aVarC);
                break;
            case "LICENSE":
                c(class_310Var, aVarC);
                break;
            case "ERROR":
                b(class_310Var, aVarC);
                break;
        }
    }

    private void a(MinecraftClient class_310Var, vn.vangioi.autofish.client.c.a aVar) {
        String strA = aVar.a(0);
        if (!aQ.equals(strA)) {
            this.aV = false;
            this.ba = 0L;
            this.aU.set(new b(c.ERROR, a.NONE, "Bridge sai build: " + strA, "", 0L, 0L, Instant.now().getEpochSecond(), 0L));
            return;
        }
        this.aV = true;
        this.aW = aVar.a(1);
        try {
            this.aX = Math.max(1, Integer.parseInt(aVar.a(2)));
        } catch (Exception e) {
            this.aX = 12;
        }
        if (!this.aU.get().ad()) {
            this.aU.set(new b(c.UNCHECKED, a.NONE, "Đã kết nối Bridge", "", 0L, 0L, Instant.now().getEpochSecond(), 0L));
        }
        if (this.aT.licenseKey == null || this.aT.licenseKey.isBlank() || !this.aY.isBlank()) {
            return;
        }
        j(class_310Var);
    }

    private void b(MinecraftClient class_310Var, vn.vangioi.autofish.client.c.a aVar) {
        String strA = aVar.a(0);
        if (this.aY.isBlank() || strA.isBlank() || this.aY.equals(strA)) {
            String strA2 = a(aVar.a(1), "License bị từ chối");
            boolean z = strA2.toLowerCase(Locale.ROOT).contains("hết hạn") || strA2.toLowerCase(Locale.ROOT).contains("het han");
            b bVar = this.aU.get();
            this.aY = "";
            this.ba = 0L;
            this.aU.set(new b(z ? c.EXPIRED : c.INVALID, bVar.af(), z ? "HẾT HẠN · Gia hạn hoặc nhập license mới" : strA2, bVar.ah(), bVar.ai(), bVar.aj(), Instant.now().getEpochSecond(), 0L));
            if (!z || class_310Var == null || class_310Var.player == null) {
                return;
            }
            class_310Var.player.sendMessage(Text.literal("[AutoFish] License đã hết hạn. Auto đã bị dừng."), false);
        }
    }

    private void c(MinecraftClient class_310Var, vn.vangioi.autofish.client.c.a aVar) {
        try {
            String strA = aVar.a(0);
            String strA2 = aVar.a(1);
            String strA3 = aVar.a(2);
            String strA4 = aVar.a(3);
            String strA5 = aVar.a(4);
            long j = Long.parseLong(aVar.a(5));
            long j2 = Long.parseLong(aVar.a(6));
            long j3 = Long.parseLong(aVar.a(7));
            String strA6 = aVar.a(8);
            String strA7 = aVar.a(9);
            String strA8 = aVar.a(10);
            if (this.aY.isBlank() || this.aY.equals(strA5)) {
                if (!n(class_310Var).equals(strA3) || !this.aT.installationId.equals(strA4) || !aQ.equals(strA6)) {
                    throw new IllegalStateException("identity mismatch");
                }
                if (!b("5|" + strA + "|" + strA2 + "|" + strA3 + "|" + strA4 + "|" + strA5 + "|" + j + "|" + strA + "|" + j2 + "|" + strA, strA7)) {
                    throw new IllegalStateException("bad signature");
                }
                if (j3 <= 0 || j2 <= j3 || j < j2) {
                    throw new IllegalStateException("bad lease");
                }
                long jNanoTime = System.nanoTime() + (Math.max(1L, Math.min(1900L, j2 - j3)) * 1000000000);
                a aVar2 = "free".equalsIgnoreCase(strA) ? a.FREE : a.PAID;
                this.aU.set(new b(c.VALID, aVar2, a(strA8, aVar2 == a.FREE ? "Free đã mở khóa" : "Key đã mở khóa"), strA2, j, j2, Instant.now().getEpochSecond(), jNanoTime));
                this.ba = a(strA7, strA5, j);
                this.aY = "";
            }
        } catch (Exception e) {
            this.aY = "";
            this.ba = 0L;
            this.aU.set(new b(c.ERROR, a.NONE, "Phản hồi Bridge không hợp lệ", "", 0L, 0L, Instant.now().getEpochSecond(), 0L));
        }
    }

    private void l(MinecraftClient class_310Var) {
        String strTrim = this.aT.licenseKey == null ? "" : this.aT.licenseKey.trim();
        if (strTrim.isBlank()) {
            return;
        }
        String strAa = aa();
        this.aY = strAa;
        h(class_310Var, vn.vangioi.autofish.client.c.a("PAID", this.aT.installationId, strAa, aQ, strTrim));
    }

    private void a(MinecraftClient class_310Var, b bVar) {
        String strAa = aa();
        this.aY = strAa;
        h(class_310Var, vn.vangioi.autofish.client.c.a("RENEW", this.aT.installationId, strAa, aQ, bVar.ah()));
    }

    private void m(MinecraftClient class_310Var) {
        if (!SERVER_BRIDGE_ENABLED) {
            return;
        }
        this.aZ = System.nanoTime();
        if (class_310Var == null || class_310Var.getNetworkHandler() == null) {
            return;
        }
        if (!ClientPlayNetworking.canSend(BridgePayload.ID)) {
            this.aU.set(new b(c.CONNECTING, a.NONE, "Server chưa có VanGioiAutoFishBridge", "", 0L, 0L, Instant.now().getEpochSecond(), 0L));
            return;
        }
        String strAa = aa();
        this.aY = strAa;
        h(class_310Var, vn.vangioi.autofish.client.c.a("HELLO", this.aT.installationId, strAa, aQ, ""));
    }

    private static void h(MinecraftClient class_310Var, String str) {
        if (!SERVER_BRIDGE_ENABLED) {
            return;
        }
        if (class_310Var == null || class_310Var.getNetworkHandler() == null || !ClientPlayNetworking.canSend(BridgePayload.ID)) {
            return;
        }
        ClientPlayNetworking.send(new BridgePayload(str));
    }

    private static b a(a aVar, String str) {
        return new b(c.CHECKING, aVar, str, "", 0L, 0L, Instant.now().getEpochSecond(), 0L);
    }

    private static b b(a aVar, String str) {
        return new b(c.INVALID, aVar, str, "", 0L, 0L, Instant.now().getEpochSecond(), 0L);
    }

    private static String n(MinecraftClient class_310Var) {
        if (class_310Var == null || class_310Var.player == null) {
            return (class_310Var == null || class_310Var.getSession().getUuidOrNull() == null) ? "offline" : class_310Var.getSession().getUuidOrNull().toString();
        }
        return class_310Var.player.getUuidAsString();
    }

    private static String aa() {
        return UUID.randomUUID().toString();
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte bt : hash) {
                sb.append(String.format("%02x", bt));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return "";
        }
    }

    private static String a(String str, String str2) {
        return (str == null || str.isBlank()) ? str2 : str;
    }

    private static long a(String str, String str2, long j) {
        long jRotateLeft = 4213102885370717789L ^ j;
        String str3 = str + str2;
        for (int i = 0; i < str3.length(); i++) {
            jRotateLeft = Long.rotateLeft(jRotateLeft ^ ((long) str3.charAt(i)), 9) * (-7046029254386353131L);
        }
        if (jRotateLeft == 0) {
            return 1L;
        }
        return jRotateLeft;
    }

    private static boolean b(String str, String str2) throws InvalidKeySpecException, NoSuchAlgorithmException, SignatureException, InvalidKeyException {
        PublicKey publicKeyGeneratePublic = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(aP)));
        Signature signature = Signature.getInstance("Ed25519");
        signature.initVerify(publicKeyGeneratePublic);
        signature.update(str.getBytes(StandardCharsets.UTF_8));
        return signature.verify(Base64.getDecoder().decode(str2));
    }
}
