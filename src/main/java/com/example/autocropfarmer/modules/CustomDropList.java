package com.example.autocropfarmer.modules;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.item.Item;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Quan ly List 2 (Custom) cho AutoDropVanilla.
 * Moi entry: stt (tu dong theo vi tri trong list) + ten (custom name nguoi dung tu dat) + itemId + enabled.
 * Duoc luu ra file JSON de giu qua cac lan mo lai client.
 *
 * File: <config-dir>/autocropfarmer/autodrop_custom.json
 */
public class CustomDropList {

    private static CustomDropList INSTANCE;

    public static CustomDropList get() {
        if (INSTANCE == null) {
            INSTANCE = new CustomDropList();
            INSTANCE.load();
        }
        return INSTANCE;
    }

    public static class Entry {
        public String name;
        public String itemId; // vi du: "minecraft:diamond"
        public boolean enabled = true;

        public Entry(String name, String itemId) {
            this.name = name;
            this.itemId = itemId;
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private File getFile() {
        File dir = new File(MeteorClient.FOLDER, "autocropfarmer");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "autodrop_custom.json");
    }

    public void load() {
        entries.clear();
        File file = getFile();
        if (!file.exists()) return;
        try (FileReader reader = new FileReader(file)) {
            Type type = new TypeToken<List<Entry>>() {}.getType();
            List<Entry> loaded = gson.fromJson(reader, type);
            if (loaded != null) entries.addAll(loaded);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void save() {
        try (FileWriter writer = new FileWriter(getFile())) {
            gson.toJson(entries, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** .additem <ten> - luu item dang cam tren tay player. Tra ve stt (1-based) hoac -1 neu fail. */
    public int addHeldItem(String customName, Item item) {
        if (item == null) return -1;
        Identifier id = Registries.ITEM.getId(item);
        entries.add(new Entry(customName, id.toString()));
        save();
        return entries.size(); // stt 1-based, item vua them nam cuoi list
    }

    /** .delitem [stt] - xoa entry theo so thu tu hien thi (1-based). */
    public boolean removeByIndex(int oneBasedIndex) {
        int idx = oneBasedIndex - 1;
        if (idx < 0 || idx >= entries.size()) return false;
        entries.remove(idx);
        save();
        return true;
    }

    public void setEnabled(int oneBasedIndex, boolean enabled) {
        int idx = oneBasedIndex - 1;
        if (idx < 0 || idx >= entries.size()) return;
        entries.get(idx).enabled = enabled;
        save();
    }

    public List<Entry> getEntries() {
        return entries;
    }

    /** Dung trong module de kiem tra 1 Item co dang bi tick drop trong list custom khong. */
    public boolean isEnabledFor(Item item) {
        if (item == null) return false;
        Identifier targetId = Registries.ITEM.getId(item);
        for (Entry e : entries) {
            if (e.enabled && e.itemId.equals(targetId.toString())) return true;
        }
        return false;
    }
}
