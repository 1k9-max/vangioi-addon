package com.example.autocropfarmer.commands;

import com.example.autocropfarmer.modules.CustomDropList;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.command.CommandSource;
import net.minecraft.item.ItemStack;

/**
 * .additem <ten>        -> luu item dang cam tren tay vao List 2 (Custom) cua AutoDropVanilla
 * .additem               -> (khong tham so) in ra danh sach custom hien tai kem stt
 */
public class AddItemCommand extends Command {

    public AddItemCommand() {
        super("additem", "Them item dang cam tren tay vao list custom cua AutoDropVanilla.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(ctx -> {
            printList();
            return SINGLE_SUCCESS;
        });

        builder.then(argument("ten", StringArgumentType.greedyString())
            .executes(this::addItem));
    }

    private int addItem(CommandContext<CommandSource> ctx) {
        String name = StringArgumentType.getString(ctx, "ten");

        if (mc.player == null) return SINGLE_SUCCESS;
        ItemStack held = mc.player.getMainHandStack();
        if (held.isEmpty()) {
            ChatUtils.error("Ban dang khong cam item nao tren tay.");
            return SINGLE_SUCCESS;
        }

        int stt = CustomDropList.get().addHeldItem(name, held.getItem());
        ChatUtils.info("Da them [" + stt + "] " + name + " (" + held.getItem().toString() + ") vao list custom.");
        return SINGLE_SUCCESS;
    }

    private void printList() {
        var entries = CustomDropList.get().getEntries();
        if (entries.isEmpty()) {
            ChatUtils.info("List custom dang trong. Dung .additem <ten> de them item dang cam tren tay.");
            return;
        }
        ChatUtils.info("---- List Custom (AutoDropVanilla) ----");
        for (int i = 0; i < entries.size(); i++) {
            var e = entries.get(i);
            String trangThai = e.enabled ? "[BAT]" : "[TAT]";
            ChatUtils.info((i + 1) + ". " + e.name + " - " + e.itemId + " " + trangThai);
        }
        ChatUtils.info("Dung .delitem [stt] de xoa.");
    }
}
