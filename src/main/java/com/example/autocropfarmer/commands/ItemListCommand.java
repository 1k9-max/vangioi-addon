package com.example.autocropfarmer.commands;

import com.example.autocropfarmer.modules.AutoDropVanilla;
import com.example.autocropfarmer.modules.CustomDropListData;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.command.CommandSource;

import java.util.List;

/**
 * .itemlist -> hien thi toan bo List 2 (Custom) cua AutoDropVanilla, kem STT de dung voi .delitem [stt].
 * Cung 1 du lieu voi nut "Edit" trong GUI module (setting "custom-items").
 */
public class ItemListCommand extends Command {

    public ItemListCommand() {
        super("itemlist", "Hien thi list custom cua AutoDropVanilla.", "listitem", "dslist");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(ctx -> {
            printList();
            return SINGLE_SUCCESS;
        });
    }

    private void printList() {
        AutoDropVanilla module = Modules.get().get(AutoDropVanilla.class);
        if (module == null) {
            ChatUtils.error("Khong tim thay module AutoDropVanilla.");
            return;
        }

        List<CustomDropListData.Entry> entries = module.getCustomItems().entries;

        if (entries.isEmpty()) {
            ChatUtils.info("List custom dang trong. Dung .additem [ten] hoac mo GUI module de them.");
            return;
        }

        ChatUtils.info("---- List Custom (AutoDropVanilla) - " + entries.size() + " item ----");
        for (int i = 0; i < entries.size(); i++) {
            CustomDropListData.Entry e = entries.get(i);
            String trangThai = e.enabled ? "[BAT]" : "[TAT]";
            ChatUtils.info((i + 1) + ". " + e.name + " (" + e.itemId + ") " + trangThai);
        }
        ChatUtils.info("Dung .delitem [stt] de xoa 1 item.");
    }
}
