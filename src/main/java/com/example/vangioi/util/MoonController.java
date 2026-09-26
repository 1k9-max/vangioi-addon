package com.example.vangioi.util;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.tabs.Tabs;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;

public final class MoonController {
    private MoonController() {
    }

    public static void open() {
        MoonConfig.setDisabled(false);
        Tabs.get().getFirst().openScreen(GuiThemes.get());
    }

    public static int disableAll() {
        int disabledCount = 0;
        for (Module module : Modules.get().getAll()) {
            if (!module.getClass().getPackageName().startsWith("com.example.vangioi")) continue;
            if (!module.isActive()) continue;
            module.toggle();
            disabledCount++;
        }

        MoonConfig.setDisabled(true);
        if (MeteorClient.mc.currentScreen != null) MeteorClient.mc.currentScreen.close();
        return disabledCount;
    }
}
