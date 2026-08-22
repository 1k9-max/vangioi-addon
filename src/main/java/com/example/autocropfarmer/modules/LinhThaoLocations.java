package com.example.autocropfarmer.modules;

import com.example.autocropfarmer.AutoCropFarmerAddon;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the output of the "/linhthao" command (a list of "Linh Thao" locations
 * printed in chat) and draws each name/tier as a floating label at its world
 * position, visible through walls and from a distance - same technique Meteor's
 * built-in Nametags module uses.
 */
public class LinhThaoLocations extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<SettingColor> textColor = sgGeneral.add(new ColorSetting.Builder()
        .name("mau-chu")
        .description("Mau chu hien thi trong world.")
        .defaultValue(new SettingColor(255, 255, 0))
        .build()
    );

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Kich thuoc chu.")
        .defaultValue(1.5)
        .range(0.1, 10)
        .build()
    );

    private final Setting<Boolean> hideChat = sgGeneral.add(new BoolSetting.Builder()
        .name("an-chat-goc")
        .description("An cac dong chat goc tra ve tu lenh /linhthao.")
        .defaultValue(false)
        .build()
    );

    // Header line that marks the start of a new /linhthao result list
    private static final Pattern HEADER = Pattern.compile("ᴅᴀɴʜ\\s*ꜱáᴄʜ\\s*ᴠị\\s*ᴛʀí\\s*ʟɪɴʜ\\s*ᴛʜảᴏ");

    // Each location line, e.g.:
    // - ᴛêɴ: ɴɢọᴄ ᴛủʏ ᴄʜɪ | ʙậᴄ: ʟɪɴʜ | ᴛọᴀ độ: x:-1516, ʏ:12, ᴢ:-298 | ᴍᴀᴘ: ʙí ᴄảɴʜ
    private static final Pattern LINE = Pattern.compile(
        "ᴛêɴ:\\s*(.+?)\\s*\\|\\s*ʙậᴄ:\\s*(.+?)\\s*\\|\\s*ᴛọᴀ\\s*độ:\\s*x:(-?\\d+),\\s*ʏ:(-?\\d+),\\s*ᴢ:(-?\\d+)\\s*\\|\\s*ᴍᴀᴘ:\\s*(.+)"
    );

    private final List<Entry> entries = new ArrayList<>();
    private final Vector3d pos = new Vector3d();

    public LinhThaoLocations() {
        super(AutoCropFarmerAddon.CATEGORY, "linh-thao-locations", "Hien ten cac Linh Thao lay tu lenh /linhthao tren world.");
    }

    @EventHandler
    private void onMessage(ReceiveMessageEvent event) {
        String msg = event.getMessage().getString();

        // New list starting -> clear old markers
        if (HEADER.matcher(msg).find()) {
            entries.clear();
            if (hideChat.get()) event.setCancelled(true);
            return;
        }

        Matcher m = LINE.matcher(msg);
        if (m.find()) {
            entries.add(new Entry(
                m.group(1).trim(),
                m.group(2).trim(),
                Integer.parseInt(m.group(3)),
                Integer.parseInt(m.group(4)),
                Integer.parseInt(m.group(5)),
                m.group(6).trim()
            ));

            if (hideChat.get()) event.setCancelled(true);
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (entries.isEmpty()) return;

        TextRenderer text = TextRenderer.get();

        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            pos.set(e.x + 0.5, e.y + 1.2, e.z + 0.5);

            // Projects the world position to screen space.
            // Returns false when the point is behind the camera - this does NOT
            // check for blocks in between, so labels stay visible through walls.
            if (!NametagUtils.to2D(pos, scale.get())) continue;

            String label = "[" + (i + 1) + "]";
            double width = text.getWidth(label, true);

            NametagUtils.begin(pos);
            text.beginBig();
            text.render(label, -width / 2, 0, textColor.get(), true);
            text.end();
            NametagUtils.end();
        }
    }

    @Override
    public void onDeactivate() {
        entries.clear();
    }

    private record Entry(String name, String tier, int x, int y, int z, String map) {}
}
