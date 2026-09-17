package com.example.vangioi.commands;

import com.example.vangioi.modules.AutoDropVanilla;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.command.CommandSource;
import net.minecraft.item.ItemStack;

/**
 * .additem            -> luu item dang cam tren tay, TU DONG lay ten hien thi that cua item
 *                        (support ca item da dat ten qua de ren) lam custom name.
 * .additem <ten>      -> luu item dang cam tren tay, dung <ten> lam custom name (ghi de ten that).
 *
 * STT cua entry moi luon TU DONG = vi tri cuoi cung trong list (n+1).
 * Du lieu duoc ghi TRUC TIEP vao setting "custom-items" cua module AutoDropVanilla, nen se hien
 * ngay lap tuc trong GUI module (nut Edit) khong can khoi dong lai.
 * Xem toan bo list custom -> dung lenh rieng: .itemlist
 */
public class AddItemCommand extends Command {

    public AddItemCommand() {
        super("additem", "Them item dang cam tren tay vao list custom cua AutoDropVanilla.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        // .additem (khong tham so) -> tu dong lay ten that cua item
        builder.executes(ctx -> {
            addItem(null);
            return SINGLE_SUCCESS;
        });

        // .additem <ten> -> dung ten tuy chinh
        builder.then(argument("ten", StringArgumentType.greedyString())
            .executes(ctx -> {
                String customName = StringArgumentType.getString(ctx, "ten");
                addItem(customName);
                return SINGLE_SUCCESS;
            }));
    }

    private void addItem(String customName) {
        if (mc.player == null) return;

        AutoDropVanilla module = Modules.get().get(AutoDropVanilla.class);
        if (module == null) {
            ChatUtils.error("Khong tim thay module AutoDropVanilla.");
            return;
        }

        ItemStack held = mc.player.getMainHandStack();
        if (held.isEmpty()) {
            ChatUtils.error("Ban dang khong cam item nao tren tay.");
            return;
        }

        String realDisplayName = held.getName().getString();

        int stt = module.getCustomItems().addHeldItem(customName, held.getItem(), realDisplayName);
        if (stt < 0) {
            ChatUtils.error("Khong the them item nay vao list custom.");
            return;
        }
        module.notifyCustomItemsChanged();

        String tenDaLuu = (customName == null || customName.isBlank()) ? realDisplayName : customName;
        ChatUtils.info("Da them [" + stt + "] " + tenDaLuu + " vao list custom. Dung .itemlist hoac mo GUI module de xem.");
    }
}
