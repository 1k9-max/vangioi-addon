package com.example.vangioi.modules;

import com.example.vangioi.AutoCropFarmerAddon;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.GenericSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;

import java.text.Normalizer;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class AutoDotPhaModule extends Module {
    private static final Map<Character, Character> STYLE_MAP = buildStyleMap();
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Integer> itemSlot = sgGeneral.add(new IntSetting.Builder()
        .name("item-slot").description("Slot hotbar dung vat pham do kiep, tu 1 den 9.")
        .defaultValue(1).range(1, 9).sliderMin(1).sliderMax(9).build());
    private final Setting<Integer> itemDelay = sgGeneral.add(new IntSetting.Builder()
        .name("item-delay-ticks").description("So tick cho truoc khi dung vat pham do kiep.")
        .defaultValue(12).range(1, 100).sliderMin(1).sliderMax(40).build());
    private final Setting<Integer> commandDelay = sgGeneral.add(new IntSetting.Builder()
        .name("command-delay-ticks").description("So tick cho giua cac lenh /dotpha va /dokiep.")
        .defaultValue(12).range(1, 100).sliderMin(1).sliderMax(40).build());
    private final Setting<CustomDropListData> selectedItem = sgGeneral.add(new GenericSetting.Builder<CustomDropListData>()
        .name("item").description("Mo Edit va bat dung 1 vat pham custom lam vat pham do kiep.")
        .defaultValue(new CustomDropListData()).build());
    private final Setting<Integer> refillAmount = sgGeneral.add(new IntSetting.Builder()
        .name("refill-amount").description("Neu so luong trong slot nho hon gia tri nay thi nap them, tu 1 den 63.")
        .defaultValue(32).range(1, 63).sliderMin(1).sliderMax(63).build());

    private final ArrayDeque<Action> actions = new ArrayDeque<>();
    private int cooldown;
    private String itemId;
    private String itemName;
    private int previousSlot = -1;

    public AutoDotPhaModule() {
        super(AutoCropFarmerAddon.CATEGORY, "auto-dotpha", "Tu dong dot pha va do kiep theo tin nhan may chu.");
    }

    @Override
    public void onActivate() {
        actions.clear();
        cooldown = 0;
        if (!loadSelectedItem()) {
            error("Hay bat dung 1 vat pham trong setting item cua Auto Dot Pha.");
            toggle();
            return;
        }
        enqueueCommand("dotpha", commandDelay.get());
    }

    @Override
    public void onDeactivate() {
        actions.clear();
        cooldown = 0;
        itemId = null;
        itemName = null;
        previousSlot = -1;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        Action action = actions.poll();
        if (action == null) return;
        action.run.run();
        cooldown = action.delay;
    }

    @EventHandler
    private void onChat(ReceiveMessageEvent event) {
        if (!isActive() || mc.player == null) return;
        String message = normalize(event.getMessage().getString());

        if (contains(message, "de vuot qua thien kiep")) {
            if (!prepareItemForUse()) return;
            actions.clear();
            enqueueAction(this::useItemAndRestoreSlot, itemDelay.get());
            enqueueCommand("dokiep", commandDelay.get());
            return;
        }

        if (contains(message, "dot pha that bai") || contains(message, "dot pha thanh cong len")) {
            if (!hasAnyMatchingItem()) {
                error("Da het vat pham do kiep, Auto Dot Pha tu tat.");
                toggle();
                return;
            }
            enqueueCommand("dotpha", commandDelay.get());
            return;
        }

        if (!mentionsPlayer(message)) return;
        if (contains(message, "that bai trong do loi kiep")
            || contains(message, "do kiep thanh cong")
            || contains(message, "dot pha canh gioi")
            || contains(message, "song sot qua do loi kiep")) {
            if (!hasAnyMatchingItem()) {
                error("Da het vat pham do kiep, Auto Dot Pha tu tat.");
                toggle();
                return;
            }
            enqueueCommand("dotpha", commandDelay.get());
        }
    }

    private boolean loadSelectedItem() {
        CustomDropListData.Entry chosen = null;
        for (CustomDropListData.Entry entry : selectedItem.get().entries) {
            if (!entry.enabled) continue;
            if (chosen != null) {
                error("Setting item chi duoc bat 1 vat pham custom.");
                return false;
            }
            chosen = entry;
        }
        if (chosen == null || chosen.itemId == null || chosen.name == null) return false;
        itemId = chosen.itemId;
        itemName = normalize(chosen.name);
        return true;
    }

    private boolean prepareItemForUse() {
        if (!loadSelectedItem() || mc.player.currentScreenHandler != mc.player.playerScreenHandler) return false;
        int targetIndex = itemSlot.get() - 1;
        ItemStack target = mc.player.getInventory().getStack(targetIndex);
        if (!target.isEmpty() && !matches(target)) {
            int emptyIndex = findEmptyInventoryIndex(targetIndex);
            if (emptyIndex < 0) {
                error("Slot item dang co vat pham khac va inventory khong con o trong de cat vat pham do.");
                toggle();
                return false;
            }
            moveInventoryStack(targetIndex, emptyIndex);
            target = mc.player.getInventory().getStack(targetIndex);
        }

        if (target.isEmpty() || target.getCount() < refillAmount.get()) {
            refillToAmount(targetIndex, target.isEmpty() ? 0 : target.getCount());
        }
        if (mc.player.getInventory().getStack(targetIndex).isEmpty()) {
            error("Khong con vat pham do kiep trong inventory, Auto Dot Pha tu tat.");
            toggle();
            return false;
        }
        return true;
    }

    private void refillToAmount(int targetIndex, int currentCount) {
        int needed = refillAmount.get() - currentCount;
        if (needed <= 0) return;
        for (int sourceIndex = 0; sourceIndex < 36 && needed > 0; sourceIndex++) {
            if (sourceIndex == targetIndex) continue;
            ItemStack source = mc.player.getInventory().getStack(sourceIndex);
            if (!matches(source)) continue;
            int move = Math.min(needed, source.getCount());
            moveExactAmount(sourceIndex, targetIndex, move);
            needed -= move;
        }
    }

    private void moveExactAmount(int sourceIndex, int targetIndex, int amount) {
        PlayerScreenHandler handler = mc.player.playerScreenHandler;
        int sourceSlot = mapSlot(handler, sourceIndex);
        int targetSlot = mapSlot(handler, targetIndex);
        if (sourceSlot < 0 || targetSlot < 0 || mc.interactionManager == null) return;
        mc.interactionManager.clickSlot(handler.syncId, sourceSlot, 0, SlotActionType.PICKUP, mc.player);
        for (int i = 0; i < amount; i++) {
            mc.interactionManager.clickSlot(handler.syncId, targetSlot, 1, SlotActionType.PICKUP, mc.player);
        }
        mc.interactionManager.clickSlot(handler.syncId, sourceSlot, 0, SlotActionType.PICKUP, mc.player);
    }

    private void moveInventoryStack(int sourceIndex, int targetIndex) {
        PlayerScreenHandler handler = mc.player.playerScreenHandler;
        int sourceSlot = mapSlot(handler, sourceIndex);
        int targetSlot = mapSlot(handler, targetIndex);
        if (sourceSlot < 0 || targetSlot < 0 || mc.interactionManager == null) return;
        mc.interactionManager.clickSlot(handler.syncId, sourceSlot, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(handler.syncId, targetSlot, 0, SlotActionType.PICKUP, mc.player);
    }

    private void useItemAndRestoreSlot() {
        if (mc.player == null || mc.interactionManager == null) return;
        previousSlot = mc.player.getInventory().selectedSlot;
        selectSlot(itemSlot.get() - 1);
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        mc.player.swingHand(Hand.MAIN_HAND);
        if (previousSlot >= 0) selectSlot(previousSlot);
    }

    private void selectSlot(int index) {
        mc.player.getInventory().setSelectedSlot(index);
        if (mc.player.networkHandler != null) {
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(index));
        }
    }

    private boolean hasAnyMatchingItem() {
        for (int i = 0; i < 36; i++) if (matches(mc.player.getInventory().getStack(i))) return true;
        return false;
    }

    private int findEmptyInventoryIndex(int except) {
        for (int i = 0; i < 36; i++) {
            if (i != except && mc.player.getInventory().getStack(i).isEmpty()) return i;
        }
        return -1;
    }

    private boolean matches(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        String id = net.minecraft.registry.Registries.ITEM.getId(stack.getItem()).toString();
        return itemId.equals(id) && itemName.equals(normalize(stack.getName().getString()));
    }

    private int mapSlot(ScreenHandler handler, int rawIndex) {
        for (int i = 0; i < handler.slots.size(); i++) {
            Slot slot = handler.slots.get(i);
            if (slot.inventory == mc.player.getInventory() && slot.getIndex() == rawIndex) return i;
        }
        return -1;
    }

    private void enqueueCommand(String command, int delay) {
        enqueueAction(() -> {
            if (mc.player != null && mc.player.networkHandler != null) mc.player.networkHandler.sendChatCommand(command);
        }, delay);
    }

    private void enqueueAction(Runnable action, int delay) {
        actions.add(new Action(action, delay));
    }

    private boolean mentionsPlayer(String message) {
        String username = mc.getSession().getUsername();
        return message.contains("ban ") || username == null || username.isBlank()
            || message.contains(username.toLowerCase(Locale.ROOT));
    }

    private static boolean contains(String text, String part) {
        return text.contains(part);
    }

    private static String normalize(String text) {
        String lower = text == null ? "" : text.toLowerCase(Locale.forLanguageTag("vi"));
        StringBuilder result = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) result.append(STYLE_MAP.getOrDefault(lower.charAt(i), lower.charAt(i)));
        return Normalizer.normalize(result.toString(), Normalizer.Form.NFD).replaceAll("\\p{M}", "").replace("d", "d");
    }

    private static Map<Character, Character> buildStyleMap() {
        Map<Character, Character> map = new HashMap<>();
        String stylized = "ABCDEFGHIJKLMNOPoRzTUVWhYZ";
        String normal = "abcdefghijklmnopqrstuvwxyz";
        for (int i = 0; i < stylized.length(); i++) map.put(stylized.charAt(i), normal.charAt(i));
        return map;
    }

    private record Action(Runnable run, int delay) {}
}