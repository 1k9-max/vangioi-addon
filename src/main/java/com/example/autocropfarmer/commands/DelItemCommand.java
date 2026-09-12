package com.example.autocropfarmer.commands;

import com.example.autocropfarmer.modules.AutoDropVanilla;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.command.CommandSource;

/**
 * .delitem [stt] -> xoa entry theo so thu tu hien thi trong .itemlist (hoac trong GUI module).
 */
public class DelItemCommand extends Command {

    public DelItemCommand() {
        super("delitem", "Xoa item theo stt khoi list custom cua AutoDropVanilla.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(argument("stt", IntegerArgumentType.integer(1))
            .executes(this::delItem));
    }

    private int delItem(CommandContext<CommandSource> ctx) {
        int stt = IntegerArgumentType.getInteger(ctx, "stt");

        AutoDropVanilla module = Modules.get().get(AutoDropVanilla.class);
        if (module == null) {
            ChatUtils.error("Khong tim thay module AutoDropVanilla.");
            return SINGLE_SUCCESS;
        }

        boolean ok = module.getCustomItems().removeByIndex(stt);
        if (ok) {
            module.notifyCustomItemsChanged();
            ChatUtils.info("Da xoa item stt " + stt + " khoi list custom.");
        } else {
            ChatUtils.error("Khong tim thay stt " + stt + " trong list custom. Dung .itemlist de xem lai.");
        }
        return SINGLE_SUCCESS;
    }
}
