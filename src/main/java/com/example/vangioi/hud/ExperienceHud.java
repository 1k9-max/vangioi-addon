package com.example.vangioi.hud;

import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.MinecraftClient;

public class ExperienceHud extends HudElement {
    public static final HudElementInfo<ExperienceHud> INFO = new HudElementInfo<>(
        Hud.GROUP,
        "experience",
        "Displays the current experience level and total experience.",
        ExperienceHud::create
    );

    private static final Color LABEL_COLOR = new Color(255, 220, 80);
    private static final Color VALUE_COLOR = Color.WHITE;

    public ExperienceHud(HudElementInfo<ExperienceHud> info) {
        super(info);
        setSize(120, 36);
    }

    private static ExperienceHud create() {
        return new ExperienceHud(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            String emptyText = "level : 0";
            String emptyXpText = "xp    : 0";
            double width = Math.max(renderer.textWidth(emptyText), renderer.textWidth(emptyXpText));
            setSize(width, renderer.textHeight() * 2);
            renderer.text(emptyText, x, y, LABEL_COLOR, true);
            renderer.text(emptyXpText, x, y + renderer.textHeight(), VALUE_COLOR, true);
            return;
        }

        int level = client.player.experienceLevel;
        int xp = getCurrentExperience(client.player.experienceLevel, client.player.experienceProgress);

        String levelText = "level : " + level;
        String xpText = "xp    : " + xp;
        double width = Math.max(renderer.textWidth(levelText), renderer.textWidth(xpText));
        double height = renderer.textHeight() * 2;
        setSize(width, height);
        renderer.text(levelText, x, y, LABEL_COLOR, true);
        renderer.text(xpText, x, y + renderer.textHeight(), VALUE_COLOR, true);
    }

    private int getCurrentExperience(int level, float progress) {
        int baseXp;
        int xpToNextLevel;

        if (level < 16) {
            baseXp = level * level + 6 * level;
            xpToNextLevel = 2 * level + 7;
        } else if (level < 31) {
            baseXp = (int) (2.5 * level * level - 40.5 * level + 360);
            xpToNextLevel = 5 * level - 38;
        } else {
            baseXp = (int) (4.5 * level * level - 162.5 * level + 2220);
            xpToNextLevel = 9 * level - 158;
        }

        return baseXp + Math.round(progress * xpToNextLevel);
    }
}
