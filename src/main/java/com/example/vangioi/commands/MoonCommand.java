package com.example.vangioi.commands;

import com.example.vangioi.util.MoonController;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.command.CommandSource;

public class MoonCommand extends Command {
    public MoonCommand() {
        super("moon", "Mo giao dien quan ly module Van Gioi.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            MoonController.open();
            return SINGLE_SUCCESS;
        });
    }
}
