package com.example.vangioi.modules;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WCheckbox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WMinus;
import meteordevelopment.meteorclient.gui.widgets.pressable.WPlus;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Man hinh cho GenericSetting<CustomDropListData> - bam nut "Edit" trong GUI cua module
 * AutoDropVanilla se mo man hinh nay.
 *
 * Bo cuc:
 *  - Hang tren: textbox nhap ten tuy chinh (co the de trong) + nut "+" de them item DANG CAM TREN TAY.
 *  - Bang phia duoi: moi dong = [checkbox bat/tat] [icon + ten item] [nut "-" xoa].
 *
 * Moi thay doi (them/xoa/tick) deu ghi truc tiep vao CustomDropListData (chinh la gia tri that cua
 * setting), nen se duoc luu cung config cua module nhu moi setting khac.
 */
public class CustomDropListScreen extends WindowScreen {
    private final CustomDropListData data;

    private WTextBox nameBox;
    private WTable table;

    public CustomDropListScreen(GuiTheme theme, CustomDropListData data) {
        super(theme, "List Custom - Auto Drop");
        this.data = data;
    }

    @Override
    public void initWidgets() {
        // ---- Hang tren: them item dang cam tren tay ----
        WHorizontalList addRow = add(theme.horizontalList()).expandX().widget();

        nameBox = addRow.add(theme.textBox("", "Ten tuy chon (de trong = lay ten that)")).expandX().widget();

        WPlus addButton = addRow.add(theme.plus()).widget();
        addButton.action = this::addHeldItem;

        add(theme.horizontalSeparator("Danh sach")).expandX();

        // ---- Bang danh sach ----
        table = add(theme.table()).expandX().widget();
        rebuildTable();
    }

    private void addHeldItem() {
        if (mc.player == null) return;

        ItemStack held = mc.player.getMainHandStack();
        if (held.isEmpty()) {
            ChatUtils.error("Ban dang khong cam item nao tren tay.");
            return;
        }

        String customName = nameBox.get() == null ? "" : nameBox.get().trim();
        String realDisplayName = held.getName().getString();

        data.addHeldItem(customName.isEmpty() ? null : customName, held.getItem(), realDisplayName);

        nameBox.set("");
        rebuildTable();
    }

    private void rebuildTable() {
        table.clear();

        for (int i = 0; i < data.entries.size(); i++) {
            CustomDropListData.Entry entry = data.entries.get(i);
            int index = i; // effectively final cho lambda

            // Checkbox bat/tat
            WCheckbox checkbox = table.add(theme.checkbox(entry.enabled)).widget();
            checkbox.action = () -> entry.enabled = checkbox.checked;

            // Icon + ten (fallback ve barrier neu item id khong hop le)
            Identifier id = Identifier.tryParse(entry.itemId);
            Item item = (id != null && Registries.ITEM.containsId(id)) ? Registries.ITEM.get(id) : net.minecraft.item.Items.BARRIER;
            String displayLabel = (i + 1) + ". " + entry.name;
            table.add(theme.itemWithLabel(new ItemStack(item), displayLabel)).expandCellX();

            // Nut xoa
            WMinus remove = table.add(theme.minus()).right().widget();
            remove.action = () -> {
                data.removeByIndex(index + 1); // ham nay dung stt 1-based
                rebuildTable();
            };

            table.row();
        }
    }
}
