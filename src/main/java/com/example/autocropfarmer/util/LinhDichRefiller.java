package com.example.autocropfarmer.util;

import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

import java.util.Locale;

/**
 * LinhDichRefiller
 * -----------------
 * Tu dong hoa quy trinh "nap Linh Dich" quan sat duoc tu video mau nguoi dung gui:
 *   1) Go lenh (mac dinh "/linhdich") -> mo GUI "Tu Linh Duoc".
 *   2) Click vao o "Chai Kinh Nghiem" trong GUI do (tim theo TEN HIEN THI cua item, vi ve mat ky
 *      thuat no la 1 item *_stained_glass_pane duoc dat ten/model tuy chinh - fallback tim theo
 *      loai item stained_glass_pane neu khong tim thay theo ten).
 *   3) Server hien dong chat "NHAP SO LUONG LINH DICH VAO CHAT" -> CHI KHI thay dung dong chat nay
 *      (khong doan mo theo so tick co dinh) moi gui 1 dong chat thuong (KHONG phai lenh) chua so
 *      luong can nap (vi du "2500000000").
 *
 * QUAN TRONG: module CHU DONG (AutoCropFarmer/AutoCropWaterer) phai tu goi onChatMessage(text) tu
 * ham onReceiveMessage() cua chinh no, chuyen tiep MOI dong chat den cho refiller nay xu ly - vi
 * ReceiveMessageEvent chi bat duoc trong Module, khong bat duoc truc tiep trong class tien ich nay.
 */
public class LinhDichRefiller {

    private enum Phase {
        IDLE, SENT_COMMAND, WAITING_GUI, CLICKED_SLOT, WAITING_PROMPT, SENT_AMOUNT
    }

    private Phase phase = Phase.IDLE;
    private int waitTicks = 0;
    private int timeoutTicks = 0;
    private String lastStatus = "";
    private String pendingAmount = "";

    public boolean isBusy() {
        return phase != Phase.IDLE;
    }

    public String getStatus() {
        return lastStatus;
    }

    /** Bat dau 1 luot nap. Khong lam gi neu dang ban voi 1 luot nap khac. */
    public void start(String command, String refillAmount) {
        if (isBusy()) return;

        ChatUtils.sendPlayerMsg(command.startsWith("/") ? command : "/" + command);
        pendingAmount = refillAmount;
        phase = Phase.SENT_COMMAND;
        waitTicks = 5; // cho server xu ly lenh va mo GUI
        timeoutTicks = 200; // 10s tong cong toi da cho ca quy trinh, tranh treo mai neu co loi
        lastStatus = "Da gui lenh nap, cho GUI mo...";
    }

    /**
     * Goi tu onReceiveMessage() cua module chu dong, chuyen tiep MOI dong chat den. Refiller se tu
     * nhan biet dong chat "nhap so luong" de biet chinh xac khi nao duoc phep go so luong vao chat -
     * thay vi doan bang 1 khoang cho co dinh (co the qua som/qua muon tuy do tre server).
     */
    public void onChatMessage(String rawText) {
        if (phase != Phase.WAITING_PROMPT || rawText == null) return;

        String lower = rawText.toLowerCase(Locale.ROOT);
        if (lower.contains("nhập số lượng") || lower.contains("nhap so luong")) {
            phase = Phase.SENT_AMOUNT; // se gui o lan tick() tiep theo (tranh gui ngay trong callback su kien)
            waitTicks = 1;
        }
    }

    /**
     * Goi moi tick khi dang isBusy(). Tra ve true khi vua HOAN TAT (hoac HUY) xong 1 luot nap ngay
     * tick nay - cac module goi ham nay co the dung gia tri tra ve de biet luc nao tiep tuc hoat
     * dong binh thuong tro lai.
     */
    public boolean tick(MinecraftClient mc) {
        if (phase == Phase.IDLE) return false;
        if (mc.player == null) {
            reset("Mat ket noi player, huy luot nap");
            return true;
        }

        timeoutTicks--;
        if (timeoutTicks <= 0) {
            reset("Timeout khi nap Linh Dich, huy luot nap");
            return true;
        }

        if (waitTicks > 0) {
            waitTicks--;
            return false;
        }

        switch (phase) {
            case SENT_COMMAND -> {
                if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) {
                    phase = Phase.WAITING_GUI;
                    waitTicks = 2;
                } else {
                    waitTicks = 3; // thu kiem tra lai sau vai tick
                }
            }
            case WAITING_GUI -> {
                if (mc.player.currentScreenHandler == mc.player.playerScreenHandler) {
                    reset("GUI dong truoc khi kip click - huy luot nap");
                    return true;
                }

                int slotId = findRefillSlotId(mc);
                if (slotId < 0) {
                    reset("Khong tim thay o Chai Kinh Nghiem/Linh Dich trong GUI - huy luot nap");
                    return true;
                }

                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slotId, 0,
                    SlotActionType.PICKUP, mc.player);

                phase = Phase.WAITING_PROMPT;
                waitTicks = 0;
                lastStatus = "Da click o nap, cho server hien dong 'nhap so luong'...";
                // KHONG dat waitTicks o day - cho onChatMessage() bao hieu khi dong chat prompt xuat hien,
                // xem timeoutTicks o tren de tranh treo mai neu server khong bao gio hien prompt nay.
            }
            case SENT_AMOUNT -> {
                ChatUtils.sendPlayerMsg(pendingAmount);
                reset("Da gui so luong " + pendingAmount + " - hoan tat nap Linh Dich.");
                return true;
            }
            default -> {
                reset("");
                return true;
            }
        }

        return false;
    }

    private void reset(String status) {
        phase = Phase.IDLE;
        waitTicks = 0;
        timeoutTicks = 0;
        pendingAmount = "";
        if (!status.isEmpty()) lastStatus = status;
    }

    /**
     * Tim slot "Chai Kinh Nghiem" trong GUI hien tai. Uu tien tim theo TEN HIEN THI cua item (chua
     * "kinh nghiệm" hoac "linh dịch", khong phan biet hoa/thuong) - chinh xac hon nhieu so voi doan
     * theo loai item, vi GUI co the co nhieu tam kinh mau khac nhau cho nhieu chuc nang khac nhau.
     * Fallback: neu khong tim thay theo ten, lay item *_stained_glass_pane DAU TIEN tim duoc.
     */
    private int findRefillSlotId(MinecraftClient mc) {
        var handler = mc.player.currentScreenHandler;
        int fallbackGlassPaneSlot = -1;

        for (int i = 0; i < handler.slots.size(); i++) {
            Slot slot = handler.slots.get(i);
            if (slot.inventory == mc.player.getInventory()) continue; // bo qua tui do nguoi choi

            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) continue;

            String name = stack.getName().getString().toLowerCase(Locale.ROOT);
            if (name.contains("kinh nghiệm") || name.contains("kinh nghiem") || name.contains("linh dịch")
                || name.contains("linh dich")) {
                return i;
            }

            if (fallbackGlassPaneSlot < 0 && isStainedGlassPane(stack.getItem())) {
                fallbackGlassPaneSlot = i;
            }
        }

        return fallbackGlassPaneSlot;
    }

    private static boolean isStainedGlassPane(Item item) {
        String id = net.minecraft.registry.Registries.ITEM.getId(item).getPath();
        return id.toLowerCase(Locale.ROOT).endsWith("stained_glass_pane");
    }
}
