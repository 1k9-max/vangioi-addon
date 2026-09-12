package com.example.autocropfarmer.commands;

import com.example.autocropfarmer.modules.AutoCropWaterer;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.command.CommandSource;

/**
 * Lenh chat ".clear-waterer" (alias ".reset-waterer").
 * Goi forceReset() cua module AutoCropWaterer de xoa toan bo du lieu tam
 * (item tuoi, Pos 1, Pos 2) va dua module ve WAITING_ITEM.
 */
public class ClearWatererCommand extends Command {

    public ClearWatererCommand() {
        super("clear-waterer", "Reset trang thai cua module Auto Crop Waterer.", "reset-waterer");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            AutoCropWaterer module = Modules.get().get(AutoCropWaterer.class);

            if (module == null) {
                error("Khong tim thay module AutoCropWaterer. Addon co the chua duoc dang ky dung cach.");
                return SINGLE_SUCCESS;
            }

            module.forceReset();
            return SINGLE_SUCCESS;
        });
    }
}
