package com.example.autocropfarmer.modules;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WidgetScreen;
import meteordevelopment.meteorclient.gui.utils.IScreenFactory;
import meteordevelopment.meteorclient.utils.misc.ICopyable;
import meteordevelopment.meteorclient.utils.misc.ISerializable;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Du lieu cho "List 2 - Custom" cua AutoDropVanilla.
 *
 * Implement ICopyable + ISerializable + IScreenFactory de dung lam gia tri cua GenericSetting.
 * Nho vay Meteor se TU DONG:
 *  - Hien 1 nut "Edit" ngay trong GUI cua module (bam vao se mo CustomDropListScreen).
 *  - Luu/nap du lieu cung file config chung cua module (khong can tu ghi file JSON rieng nua).
 */
public class CustomDropListData implements ICopyable<CustomDropListData>, ISerializable<CustomDropListData>, IScreenFactory {

    public static class Entry {
        public String name;
        public String itemId; // vi du: "minecraft:diamond"
        public boolean enabled = true;

        public Entry() {
        }

        public Entry(String name, String itemId) {
            this.name = name;
            this.itemId = itemId;
        }

        public Entry copy() {
            Entry e = new Entry(name, itemId);
            e.enabled = enabled;
            return e;
        }
    }

    public final List<Entry> entries = new ArrayList<>();

    /**
     * .additem [ten] - them item dang cam tren tay.
     * Neu customName null/rong -> dung fallbackDisplayName (ten hien thi that cua item, ho tro ca do ren).
     * STT tra ve luon = vi tri cuoi list (n+1).
     */
    public int addHeldItem(String customName, Item item, String fallbackDisplayName) {
        if (item == null) return -1;

        String finalName = (customName == null || customName.isBlank()) ? fallbackDisplayName : customName;
        Identifier id = Registries.ITEM.getId(item);

        entries.add(new Entry(finalName, id.toString()));
        return entries.size();
    }

    /** .delitem [stt] - xoa theo so thu tu hien thi (1-based). */
    public boolean removeByIndex(int oneBasedIndex) {
        int idx = oneBasedIndex - 1;
        if (idx < 0 || idx >= entries.size()) return false;
        entries.remove(idx);
        return true;
    }

    public boolean isEnabledFor(Item item) {
        if (item == null) return false;
        Identifier targetId = Registries.ITEM.getId(item);
        for (Entry e : entries) {
            if (e.enabled && e.itemId.equals(targetId.toString())) return true;
        }
        return false;
    }

    /**
     * Kiem tra 1 ItemStack cu the co khop voi 1 entry trong list custom khong.
     * Khop khi CA HAI: cung item id (minecraft:xxx) VA cung ten hien thi that (getName().getString()).
     * Nho vay chi item DUNG TEN da luu (vd: item da dat ten qua de ren "Do cu xin") moi bi drop,
     * KHONG vut nham cac item cung loai (vd: minecraft:stone) nhung khong mang ten do.
     */
    public boolean isEnabledFor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;

        Identifier targetId = Registries.ITEM.getId(stack.getItem());
        String displayName = stack.getName().getString();

        for (Entry e : entries) {
            if (!e.enabled) continue;
            if (!e.itemId.equals(targetId.toString())) continue;
            if (e.name != null && e.name.equals(displayName)) return true;
        }
        return false;
    }

    // ===================== ICopyable =====================
    @Override
    public CustomDropListData set(CustomDropListData value) {
        entries.clear();
        for (Entry e : value.entries) entries.add(e.copy());
        return this;
    }

    @Override
    public CustomDropListData copy() {
        CustomDropListData data = new CustomDropListData();
        for (Entry e : entries) data.entries.add(e.copy());
        return data;
    }

    // ===================== ISerializable (luu cung config module) =====================
    @Override
    public NbtCompound toTag() {
        NbtCompound tag = new NbtCompound();
        NbtList list = new NbtList();

        for (Entry e : entries) {
            NbtCompound et = new NbtCompound();
            et.putString("name", e.name == null ? "" : e.name);
            et.putString("itemId", e.itemId);
            et.putBoolean("enabled", e.enabled);
            list.add(et);
        }

        tag.put("entries", list);
        return tag;
    }

    @Override
    public CustomDropListData fromTag(NbtCompound tag) {
        entries.clear();

        NbtList list = tag.getList("entries", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound et = list.getCompound(i);
            Entry e = new Entry(et.getString("name"), et.getString("itemId"));
            e.enabled = et.getBoolean("enabled");
            entries.add(e);
        }

        return this;
    }

    // ===================== IScreenFactory =====================
    @Override
    public WidgetScreen createScreen(GuiTheme theme) {
        return new CustomDropListScreen(theme, this);
    }
}
