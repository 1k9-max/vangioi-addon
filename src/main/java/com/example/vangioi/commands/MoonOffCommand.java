package com.example.vangioi.commands;

import com.example.vangioi.util.MoonController;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.command.CommandSource;

public class MoonOffCommand extends Command {
    public MoonOffCommand() {
        super("moonoff", "Tat tat ca module cua Van Gioi Addon.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            MoonController.disableAll();
            return SINGLE_SUCCESS;
        });
    }
}
