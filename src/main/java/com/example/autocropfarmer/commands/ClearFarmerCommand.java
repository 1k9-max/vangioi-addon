package com.example.autocropfarmer.commands;

import com.example.autocropfarmer.modules.AutoCropFarmer;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.command.CommandSource;

/**
 * Lenh chat ".clear-farmer" (alias ".reset-farmer").
 * Goi forceReset() cua module AutoCropFarmer de xoa toan bo du lieu tam
 * (Item 1 / Item 2 name, Pos 1, Pos 2) va dua module ve WAITING_ITEM_1.
 */
public class ClearFarmerCommand extends Command {

    public ClearFarmerCommand() {
        super("clear-farmer", "Reset trang thai cua module Auto Crop Farmer.", "reset-farmer");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            AutoCropFarmer module = Modules.get().get(AutoCropFarmer.class);

            if (module == null) {
                error("Khong tim thay module AutoCropFarmer. Addon co the chua duoc dang ky dung cach.");
                return SINGLE_SUCCESS;
            }

            module.forceReset();
            return SINGLE_SUCCESS;
        });
    }
}
