package com.example.autocropfarmer.util;

import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.Slot;

import java.util.Locale;

/**
 * LinhDichRefiller
 * -----------------
 * Tu dong hoa quy trinh "nap Linh Dich" quan sat duoc tu video mau nguoi dung gui:
 *   1) Go lenh (mac dinh "/linhdich") -> mo GUI "Tu Linh Duoc".
 *   2) Click vao o icon binh Linh Dich trong GUI do (ve mat ky thuat la 1 item *_stained_glass_pane,
 *      chi hien thi bang icon/ten tuy chinh, nen tim theo loai item "stained_glass_pane" bat ky mau nao
 *      thay vi doan cung 1 slot index - GUI co the sap xep khac nhau tuy luc).
 *   3) Server hien dong chat "NHAP SO LUONG LINH DICH VAO CHAT" -> gui 1 dong chat thuong (KHONG phai
 *      lenh) chua so luong can nap (vi du "2500000000").
 *   4) Cho vai tick de server xu ly xong (GUI thuong tu dong o lai, KHONG can dong tay).
 *
 * Dung chung cho ca AutoCropWaterer va AutoCropFarmer (ca 2 module deu dung Item 1/binh Linh Dich
 * de "tuoi" nen deu co the can nap lai khi het).
 */
public class LinhDichRefiller {

    private enum Phase {
        IDLE, SENT_COMMAND, WAITING_GUI, CLICKED_SLOT, SENT_AMOUNT, COOLDOWN
    }

    private Phase phase = Phase.IDLE;
    private int waitTicks = 0;
    private int timeoutTicks = 0;
    private String lastStatus = "";

    public boolean isBusy() {
        return phase != Phase.IDLE;
    }

    public String getStatus() {
        return lastStatus;
    }

    /** Bat dau 1 luot nap. Khong lam gi neu dang ban voi 1 luot nap khac. */
    public void start(String command) {
        if (isBusy()) return;

        ChatUtils.sendPlayerMsg(command.startsWith("/") ? command : "/" + command);
        phase = Phase.SENT_COMMAND;
        waitTicks = 5; // cho server xu ly lenh va mo GUI
        timeoutTicks = 100; // 5s tong cong toi da cho tung buoc, tranh treo mai neu co loi
        lastStatus = "Da gui lenh nap, cho GUI mo...";
    }

    /**
     * Goi moi tick khi dang isBusy(). Tra ve true khi vua HOAN TAT xong 1 luot nap (chuyen ve IDLE
     * ngay tick nay) - cac module goi ham nay co the dung gia tri tra ve de biet luc nao tiep tuc
     * hoat dong binh thuong tro lai.
     */
    public boolean tick(MinecraftClient mc, String refillAmount) {
        if (phase == Phase.IDLE) return false;
        if (mc.player == null) {
            reset("Mat ket noi player, huy luot nap");
            return true;
        }

        if (waitTicks > 0) {
            waitTicks--;
            timeoutTicks--;
            if (timeoutTicks <= 0) {
                reset("Timeout khi nap Linh Dich, huy luot nap");
                return true;
            }
            return false;
        }

        switch (phase) {
            case SENT_COMMAND -> {
                // Cho them 1 nhip roi kiem tra GUI da mo chua
                if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) {
                    phase = Phase.WAITING_GUI;
                    waitTicks = 2;
                } else {
                    timeoutTicks -= 1;
                    if (timeoutTicks <= 0) {
                        reset("Khong thay GUI mo sau khi go lenh nap - huy");
                        return true;
                    }
                    waitTicks = 2; // thu kiem tra lai sau vai tick
                }
            }
            case WAITING_GUI -> {
                if (mc.player.currentScreenHandler == mc.player.playerScreenHandler) {
                    reset("GUI dong truoc khi kip click - huy luot nap");
                    return true;
                }

                int slotId = findGlassPaneSlotId(mc);
                if (slotId < 0) {
                    reset("Khong tim thay o Linh Dich trong GUI - huy luot nap");
                    return true;
                }

                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slotId, 0,
                    net.minecraft.screen.slot.SlotActionType.PICKUP, mc.player);

                phase = Phase.CLICKED_SLOT;
                waitTicks = 4; // cho server hien prompt "nhap so luong"
                lastStatus = "Da click o Linh Dich, cho nhap so luong...";
            }
            case CLICKED_SLOT -> {
                ChatUtils.sendPlayerMsg(refillAmount);
                phase = Phase.SENT_AMOUNT;
                waitTicks = 10; // cho server xu ly xong giao dich
                lastStatus = "Da gui so luong " + refillAmount + ", cho xac nhan...";
            }
            case SENT_AMOUNT -> {
                reset("Da nap xong Linh Dich.");
                return true;
            }
            default -> reset("");
        }

        return false;
    }

    private void reset(String status) {
        phase = Phase.IDLE;
        waitTicks = 0;
        timeoutTicks = 0;
        if (!status.isEmpty()) lastStatus = status;
    }

    /** Tim slot dau tien trong GUI hien tai la 1 item *_stained_glass_pane (bat ky mau nao). */
    private int findGlassPaneSlotId(MinecraftClient mc) {
        var handler = mc.player.currentScreenHandler;

        for (int i = 0; i < handler.slots.size(); i++) {
            Slot slot = handler.slots.get(i);
            if (slot.inventory == mc.player.getInventory()) continue; // bo qua tui do nguoi choi

            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) continue;

            if (isStainedGlassPane(stack.getItem())) return i;
        }

        return -1;
    }

    private static boolean isStainedGlassPane(Item item) {
        String id = net.minecraft.registry.Registries.ITEM.getId(item).getPath();
        return id.toLowerCase(Locale.ROOT).endsWith("stained_glass_pane");
    }
}
