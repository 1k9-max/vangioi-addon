package com.example.autocropfarmer.modules;

import com.example.autocropfarmer.AutoCropFarmerAddon;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.input.WIntEdit;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.gui.widgets.pressable.WCheckbox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WMinus;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * ChatAutoResponder
 * ------------------
 * Port lai tu module cung ten trong "obot-addon" (decompile lai tu file .jar nguoi dung gui, vi mat
 * source goc) - giu nguyen thiet ke: mot bang "profile" (Enabled / Trigger / Response / Delay) co the
 * them/xoa/sua truc tiep tren GUI cua module (khong phai qua settings thuong), thay vi 1 danh sach
 * string don gian nhu ban truoc.
 *
 * Moi profile: khi 1 dong chat den chua "trigger" (khong phan biet hoa/thuong) VA profile dang bat,
 * se tu dong gui "response" len server sau "delay" mili-giay (co the la tin nhan thuong hoac lenh
 * bat dau bang /). Danh sach profile duoc luu duoi dang chuoi serialize trong 1 setting an
 * (khong hien trong bang settings thong thuong, chi sua qua GUI rieng cua module).
 */
public class ChatAutoResponder extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // Luu profile duoi dang chuoi serialize (moi phan tu 1 profile) - setting nay AN trong bang
    // settings thong thuong, vi da co GUI rieng (getWidget) de chinh sua truc quan hon nhieu.
    private final Setting<List<String>> profiles = sgGeneral.add(new StringListSetting.Builder()
        .name("profiles")
        .description("Du lieu profile luu noi bo. Dung GUI rieng cua module (nhan vao module trong danh "
            + "sach) de chinh sua thay vi sua truc tiep o day.")
        .defaultValue(List.of())
        .visible(() -> false)
        .build()
    );

    public ChatAutoResponder() {
        super(AutoCropFarmerAddon.CATEGORY, "chat-auto-responder", "Tu dong tra loi chat theo cac profile co the cau hinh.");
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (mc.player == null) return;

        String message = event.getMessage().getString();

        for (Profile profile : parseProfiles()) {
            if (!profile.enabled || profile.trigger.isBlank() || !containsIgnoreCase(message, profile.trigger)) continue;

            String resp = profile.response;

            if (profile.delayMs <= 0L) {
                sendResponse(resp);
                break;
            }

            MeteorExecutor.execute(() -> {
                try {
                    Thread.sleep(profile.delayMs);
                } catch (InterruptedException ignored) {
                }

                if (mc != null) {
                    mc.execute(() -> sendResponse(resp));
                }
            });
            break;
        }
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WVerticalList list = theme.verticalList();

        WHorizontalList header = list.add(theme.horizontalList()).expandX().widget();
        header.add(theme.label("Enabled", false)).minWidth(60).widget();
        header.add(theme.label("Trigger", false)).expandX().widget();
        header.add(theme.label("Response", false)).expandX().widget();
        header.add(theme.label("Delay", false)).minWidth(75).widget();
        header.add(theme.label("", false)).minWidth(30).widget();

        WVerticalList profileList = list.add(theme.verticalList()).expandX().widget();
        refreshProfiles(profileList, theme);

        WButton add = list.add(theme.button("Add profile")).widget();
        add.action = () -> {
            List<Profile> current = parseProfiles();
            current.add(new Profile(true, "", "", 500L));
            saveProfiles(current);
            refreshProfiles(profileList, theme);
        };

        return list;
    }

    private void refreshProfiles(WVerticalList list, GuiTheme theme) {
        list.clear();

        List<Profile> current = parseProfiles();

        for (int i = 0; i < current.size(); i++) {
            Profile profile = current.get(i);
            int index = i;

            WHorizontalList row = list.add(theme.horizontalList()).expandX().widget();

            WCheckbox enabled = row.add(theme.checkbox(profile.enabled)).minWidth(20).widget();
            enabled.action = () -> {
                profile.enabled = enabled.checked;
                saveProfiles(current);
            };

            WTextBox trigger = row.add(theme.textBox(profile.trigger)).expandX().widget();
            trigger.actionOnUnfocused = () -> {
                profile.trigger = trigger.get();
                saveProfiles(current);
            };

            WTextBox response = row.add(theme.textBox(profile.response)).expandX().widget();
            response.actionOnUnfocused = () -> {
                profile.response = response.get();
                saveProfiles(current);
            };

            WIntEdit delay = row.add(theme.intEdit((int) profile.delayMs, 0, 1_000_000, true)).minWidth(75).widget();
            delay.action = () -> {
                profile.delayMs = delay.get();
                saveProfiles(current);
            };

            WMinus remove = row.add(theme.minus()).minWidth(30).widget();
            remove.action = () -> {
                current.remove(index);
                saveProfiles(current);
                refreshProfiles(list, theme);
            };
        }
    }

    private List<Profile> parseProfiles() {
        List<Profile> result = new ArrayList<>();

        for (String entry : profiles.get()) {
            if (entry == null || entry.isBlank()) continue;

            try {
                result.add(Profile.fromString(entry));
            } catch (Exception ignored) {
            }
        }

        return result;
    }

    private void saveProfiles(List<Profile> current) {
        List<String> strings = new ArrayList<>();
        for (Profile profile : current) strings.add(profile.serialize());
        profiles.set(strings);
    }

    private void sendResponse(String response) {
        if (mc.player == null || response == null || response.isBlank()) return;
        ChatUtils.sendPlayerMsg(response);
    }

    private boolean containsIgnoreCase(String text, String trigger) {
        if (text == null || trigger == null || trigger.isBlank()) return false;
        return text.toLowerCase(Locale.ROOT).contains(trigger.toLowerCase(Locale.ROOT));
    }

    @Override
    public String getInfoString() {
        return parseProfiles().size() + " profiles";
    }

    private static final class Profile {
        public boolean enabled = true;
        public String trigger = "";
        public String response = "";
        public long delayMs = 0L;

        public Profile() {
        }

        public Profile(boolean enabled, String trigger, String response, long delayMs) {
            this.enabled = enabled;
            this.trigger = trigger == null ? "" : trigger;
            this.response = response == null ? "" : response;
            this.delayMs = delayMs;
        }

        public String serialize() {
            return "enabled=" + enabled + ";trigger=" + escape(trigger) + ";response=" + escape(response) + ";delay=" + delayMs;
        }

        public static Profile fromString(String s) {
            Profile p = new Profile();

            for (String part : s.split(";")) {
                String[] kv = part.split("=", 2);
                if (kv.length != 2) continue;

                String key = kv[0].trim().toLowerCase(Locale.ROOT);
                String value = kv[1].trim();

                switch (key) {
                    case "trigger" -> p.trigger = unescape(value);
                    case "response" -> p.response = unescape(value);
                    case "delay" -> p.delayMs = Long.parseLong(value);
                    case "enabled" -> p.enabled = Boolean.parseBoolean(value);
                }
            }

            return p;
        }

        private static String escape(String in) {
            if (in == null) return "";
            return in.replace(";", "\\;").replace("=", "\\=");
        }

        private static String unescape(String in) {
            if (in == null) return "";
            return in.replace("\\;", ";").replace("\\=", "=");
        }
    }
}
