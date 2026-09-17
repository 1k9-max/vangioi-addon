package com.example.vangioi.modules;

import com.example.vangioi.AutoCropFarmerAddon;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.regex.Pattern;

public class GuiDumperModule extends Module {
    private static final Pattern INVALID_FILE_CHARS = Pattern.compile("[^A-Za-z0-9_.-]");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public GuiDumperModule() {
        super(AutoCropFarmerAddon.CATEGORY, "gui-dumper", "Dump thong tin GUI hien tai ra file JSON trong ./gui_dumps/ipsv/[tengui]/.");
    }

    public boolean canShowButton(ScreenHandler handler) {
        return isActive() && handler != null && mc.currentScreen instanceof HandledScreen<?>;
    }

    public void dumpCurrentGui(ScreenHandler handler) {
        if (mc.player == null || mc.currentScreen == null || !(mc.currentScreen instanceof HandledScreen<?> screen) || handler == null) {
            return;
        }

        String guiName = sanitizeName(screen.getTitle().getString());
        if (guiName.isBlank()) guiName = sanitizeName(screen.getClass().getSimpleName());
        if (guiName.isBlank()) guiName = "gui";

        File dumpRoot = new File(mc.runDirectory, "gui_dumps/ipsv");
        File folder = new File(dumpRoot, guiName);
        if (!folder.exists() && !folder.mkdirs()) {
            error("Khong tao duoc thu muc dump: " + folder.getAbsolutePath());
            return;
        }

        File target = nextAvailableDumpFile(folder);
        JsonObject root = new JsonObject();
        root.addProperty("timestamp", Instant.now().toString());
        root.addProperty("mod", "Van Gioi Addon");
        root.addProperty("screenClass", mc.currentScreen.getClass().getName());
        root.addProperty("screenTitle", screen.getTitle().getString());
        root.addProperty("handlerType", handler.getType().toString());
        root.addProperty("slotCount", handler.slots.size());

        JsonArray slots = new JsonArray();
        for (int i = 0; i < handler.slots.size(); i++) {
            Slot slot = handler.getSlot(i);
            JsonObject slotJson = new JsonObject();
            slotJson.addProperty("index", i);
            slotJson.addProperty("slotId", slot.id);
            slotJson.addProperty("x", slot.x);
            slotJson.addProperty("y", slot.y);
            slotJson.addProperty("realIndex", slot.getIndex());
            slotJson.addProperty("hasStack", !slot.getStack().isEmpty());

            ItemStack stack = slot.getStack();
            if (!stack.isEmpty()) {
                slotJson.addProperty("itemId", stack.getItem().toString());
                slotJson.addProperty("itemName", stack.getName().getString());
                slotJson.addProperty("count", stack.getCount());
                slotJson.addProperty("maxDamage", stack.getMaxDamage());
                slotJson.addProperty("damage", stack.getDamage());
            }
            slots.add(slotJson);
        }
        root.add("slots", slots);

        try (FileWriter writer = new FileWriter(target, StandardCharsets.UTF_8)) {
            writer.write(GSON.toJson(root));
            info("Dump GUI da luu vao: " + target.getAbsolutePath());
        } catch (IOException e) {
            error("Khong ghi duoc file dump: " + e.getMessage());
        }
    }

    private File nextAvailableDumpFile(File folder) {
        File base = new File(folder, "file.json");
        if (!base.exists()) return base;

        int index = 1;
        while (true) {
            File candidate = new File(folder, "file" + index + ".json");
            if (!candidate.exists()) return candidate;
            index++;
        }
    }

    private static String sanitizeName(String input) {
        String clean = input == null ? "gui" : input.trim();
        clean = INVALID_FILE_CHARS.matcher(clean).replaceAll("_");
        clean = clean.replace("__", "_").replace("..", ".");
        clean = clean.substring(0, Math.min(clean.length(), 80));
        return clean.isBlank() ? "gui" : clean.toLowerCase(Locale.ROOT);
    }
}
