package vn.vangioi.autofish.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;

public final class g {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("vangioiautofish.json");
    public String licenseKey = "";
    public String installationId = UUID.randomUUID().toString();
    public boolean autoRebait = true;
    public boolean autoRecast = true;
    public boolean preferExactProtocol = true;
    public int rodHotbarSlot = 1;
    public int baitHotbarSlot = 2;
    public int reprepareDelayTicks = 8;
    public int attachDelayTicks = 5;
    public int castSettleTicks = 14;
    public int actionbarTimeoutMs = 1200;
    public double hysteresisCells = 0.35d;
    public double predictionStrength = 1.15d;
    public int uiX = -1;
    public int uiY = -1;

    public static g ao() {
        try {
            if (Files.exists(PATH, new LinkOption[0])) {
                g gVar = (g) GSON.fromJson(Files.readString(PATH, StandardCharsets.UTF_8), g.class);
                if (gVar != null) {
                    gVar.aq();
                    gVar.ap();
                    return gVar;
                }
            }
        } catch (Exception e) {
        }
        g gVar2 = new g();
        gVar2.ap();
        return gVar2;
    }

    public void ap() {
        aq();
        try {
            Files.createDirectories(PATH.getParent(), new FileAttribute[0]);
            Files.writeString(PATH, GSON.toJson(this), StandardCharsets.UTF_8, new OpenOption[0]);
        } catch (IOException e) {
        }
    }

    private void aq() {
        if (this.installationId == null || this.installationId.isBlank()) {
            this.installationId = UUID.randomUUID().toString();
        }
        if (this.licenseKey == null) {
            this.licenseKey = "";
        }
        this.rodHotbarSlot = Math.max(1, Math.min(9, this.rodHotbarSlot));
        this.baitHotbarSlot = Math.max(1, Math.min(9, this.baitHotbarSlot));
        if (this.baitHotbarSlot == this.rodHotbarSlot) {
            this.baitHotbarSlot = this.rodHotbarSlot == 9 ? 8 : this.rodHotbarSlot + 1;
        }
        this.reprepareDelayTicks = Math.max(2, Math.min(this.reprepareDelayTicks, 100));
        this.attachDelayTicks = Math.max(2, Math.min(this.attachDelayTicks, 40));
        this.castSettleTicks = Math.max(4, Math.min(this.castSettleTicks, 80));
        this.actionbarTimeoutMs = Math.max(350, Math.min(this.actionbarTimeoutMs, 3000));
        this.hysteresisCells = Math.max(0.0d, Math.min(this.hysteresisCells, 3.0d));
        this.predictionStrength = Math.max(0.0d, Math.min(this.predictionStrength, 3.0d));
        this.uiX = Math.max(-1, this.uiX);
        this.uiY = Math.max(-1, this.uiY);
    }
}
