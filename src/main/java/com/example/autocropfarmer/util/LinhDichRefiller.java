package com.example.autocropfarmer.util;

import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * LinhDichRefiller
 * -----------------
 * Tu dong hoa quy trinh "nap Linh Dich":
 *   1) Go lenh (mac dinh "/linhdich") -> mo GUI "Tu Linh Duoc".
 *   2) Click vao o "Chai Kinh Nghiem" trong GUI do (tim theo TEN HIEN THI cua item, fallback tim
 *      theo loai item stained_glass_pane neu khong tim thay theo ten).
 *   3) Server hien dong chat "NHAP SO LUONG LINH DICH VAO CHAT" -> CHI KHI thay dung dong chat nay
 *      moi gui 1 dong chat thuong (KHONG phai lenh) chua so luong can nap.
 *   4) Cho server xac nhan da nap xong (dong chat "... da truyen ...") roi moi coi nhu HOAN TAT.
 *      Neu khong thay xac nhan truoc khi timeout, coi nhu THAT BAI (khong tu suy dien la thanh cong).
 *
 * QUAN TRONG - UNICODE SMALL-CAPS:
 * Nhieu server dung font "chu hoa nho" (vi du "ɴʜậᴘ ѕố ʟượɴɢ") de trang tri chat he thong. Cac ky
 * tu nhu ɴ ʜ ᴘ ѕ ʟ ɢ ... KHONG PHAI la chu Latin thuong/hoa binh thuong ma la cac code point Unicode
 * rieng (khoi Phonetic Extensions + 1 vai ky tu muon tu Cyrillic nhu "ѕ"). toLowerCase() KHONG the
 * chuan hoa duoc chung, nen so sanh chuoi truc tiep se KHONG BAO GIO khop va khien refiller cho mai
 * roi timeout. STYLE_MAP ben duoi dung de "dich nguoc" cac ky tu nay ve chu Latin thuong truoc khi
 * so sanh - cac ky tu co dau tieng Viet (a, o, u, ...) khong bi dong trong bang nay van giu nguyen,
 * roi duoc xu ly boi buoc bo dau o normalizeForMatch().
 *
 * QUAN TRONG - TICH HOP VOI MODULE CHU DONG (AutoCropFarmer/AutoCropWaterer):
 *   - Khi module tuoi cay nhan duoc dong chat he thong bao het linh dich (vi du dang
 *     "[Linh Thao] Binh khong du linh dich!"), goi LinhDichRefiller.isOutOfLinhDichMessage(text) de
 *     kiem tra, neu true thi goi refiller.start("/linhdich", "<so luong muon nap>").
 *   - Module PHAI tu goi onChatMessage(text) tu ham onReceiveMessage() cua chinh no, chuyen tiep MOI
 *     dong chat den cho refiller nay xu ly - vi ReceiveMessageEvent chi bat duoc trong Module, khong
 *     bat duoc truc tiep trong class tien ich nay.
 *   - Module nen kiem tra refiller.isBusy() truoc khi thuc hien cac hanh dong tuoi cay khac, va goi
 *     refiller.tick(mc) moi tick trong luc isBusy() == true de cac buoc GUI/chat duoc xu ly.
 */
public class LinhDichRefiller {

    private enum Phase {
        IDLE, SENT_COMMAND, WAITING_GUI, CLICKED_SLOT, WAITING_PROMPT, SENT_AMOUNT, WAITING_CONFIRM
    }

    // "Dich nguoc" chu hoa nho Unicode (small caps) ve chu Latin thuong de so sanh chuoi cho dung.
    private static final Map<Character, Character> STYLE_MAP = new HashMap<>();
    static {
        String stylized = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘʀѕᴛᴜᴠᴡʏᴢ";
        String normal   = "abcdefghijklmnoprstuvwyz";
        for (int i = 0; i < stylized.length(); i++) {
            STYLE_MAP.put(stylized.charAt(i), normal.charAt(i));
        }
        // Cac bien the/ky tu ngoai bang chinh hay gap them trong cac font "chu hoa nho" khac nhau
        STYLE_MAP.put('ꜱ', 's');
        STYLE_MAP.put('ǫ', 'q');
        STYLE_MAP.put('x', 'x');
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
     * nhan biet dong chat "nhap so luong" va dong chat "xac nhan da truyen xong" - ke ca khi server
     * dung font chu hoa nho Unicode - thay vi doan bang 1 khoang cho co dinh.
     */
    public void onChatMessage(String rawText) {
        if (rawText == null) return;
        String norm = normalizeForMatch(rawText);

        if (phase == Phase.WAITING_PROMPT) {
            if (norm.contains("nhap so luong")) {
                phase = Phase.SENT_AMOUNT; // se gui o lan tick() tiep theo (tranh gui ngay trong callback su kien)
                waitTicks = 1;
            }
            return;
        }

        if (phase == Phase.WAITING_CONFIRM) {
            if (norm.contains("da truyen") || norm.contains("nap thanh cong") || norm.contains("thanh cong")) {
                reset("Da nap Linh Dich thanh cong (" + pendingAmount + ").");
                return;
            }
            // Server co the huy/bao loi (vi du go sai dinh dang so luong) - khong doan mo, coi la that bai ro rang
            if (norm.contains("da huy") || norm.contains("khong hop le") || norm.contains("that bai")) {
                reset("Server bao loi/huy khi nap Linh Dich - can kiem tra lai.");
            }
        }
    }

    /**
     * Goi moi tick khi dang isBusy(). Tra ve true khi vua HOAN TAT (hoac HUY) xong 1 luot nap ngay
     * tick nay - cac module goi ham nay co the dung gia tri tra ve de biet luc nao tiep tuc hoat
     * dong binh thuong tro lai. LUU Y: tra ve true KHONG dong nghia voi thanh cong - kiem tra
     * getStatus() hoac them 1 co rieng neu module can phan biet thanh cong/that bai/timeout.
     */
    public boolean tick(MinecraftClient mc) {
        if (phase == Phase.IDLE) return false;
        if (mc.player == null) {
            reset("Mat ket noi player, huy luot nap");
            return true;
        }

        timeoutTicks--;
        if (timeoutTicks <= 0) {
            reset("Timeout khi nap Linh Dich (khong thay server xac nhan) - huy luot nap");
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
                phase = Phase.WAITING_CONFIRM;
                waitTicks = 0;
                timeoutTicks = Math.min(timeoutTicks, 60); // toi da them 3s de doi xac nhan sau khi gui so luong
                lastStatus = "Da gui so luong " + pendingAmount + " - cho server xac nhan da truyen...";
                // KHONG reset()/coi la xong o day - cho onChatMessage() bat dong xac nhan "da truyen"
                // (hoac dong bao loi/huy) truoc khi ket thuc, tranh bao thanh cong gia khi server tu choi.
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
     * Nhan dien dong chat he thong bao "binh khong du linh dich" (vi du dang
     * "[Linh Thao] Binh khong du linh dich!"), ke ca khi server dung font chu hoa nho Unicode.
     * Module tuoi cay goi ham static nay tu onReceiveMessage() de biet khi nao can goi start().
     */
    public static boolean isOutOfLinhDichMessage(String rawText) {
        if (rawText == null) return false;
        String norm = normalizeForMatch(rawText);
        return norm.contains("khong du linh dich");
    }

    /**
     * Chuan hoa 1 chuoi de so sanh: dich nguoc chu hoa nho Unicode ve chu Latin thuong, ha chu
     * thuong, roi bo dau tieng Viet. Dung cho ca viec nhan dien trigger, prompt "nhap so luong" va
     * dong xac nhan hoan tat.
     */
    private static String normalizeForMatch(String rawText) {
        StringBuilder sb = new StringBuilder(rawText.length());
        for (int i = 0; i < rawText.length(); i++) {
            char c = rawText.charAt(i);
            Character mapped = STYLE_MAP.get(c);
            sb.append(mapped != null ? mapped : c);
        }
        return stripVietnameseDiacritics(sb.toString().toLowerCase(Locale.ROOT));
    }

    private static String stripVietnameseDiacritics(String s) {
        String normalized = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD);
        StringBuilder sb = new StringBuilder(normalized.length());
        for (char c : normalized.toCharArray()) {
            // bo cac dau (combining marks) sau khi tach NFD
            if (Character.getType(c) == Character.NON_SPACING_MARK) continue;
            sb.append(c);
        }
        // dong 'd' (khong dau) trong tieng Viet tach NFD ra 'đ' + khong co mark rieng -> xu ly thu cong
        return sb.toString().replace('\u0111', 'd').replace('\u0110', 'd');
    }

    /**
     * Tim slot "Chai Kinh Nghiem" trong GUI hien tai. Uu tien tim theo TEN HIEN THI cua item (chua
     * "kinh nghiệm" hoac "linh dịch", khong phan biet hoa/thuong, chiu duoc font chu hoa nho Unicode
     * va co/khong dau) - chinh xac hon nhieu so voi doan theo loai item, vi GUI co the co nhieu tam
     * kinh mau khac nhau cho nhieu chuc nang khac nhau. Fallback: neu khong tim thay theo ten, lay
     * item *_stained_glass_pane DAU TIEN tim duoc.
     */
    private int findRefillSlotId(MinecraftClient mc) {
        var handler = mc.player.currentScreenHandler;
        int fallbackGlassPaneSlot = -1;

        for (int i = 0; i < handler.slots.size(); i++) {
            Slot slot = handler.slots.get(i);
            if (slot.inventory == mc.player.getInventory()) continue; // bo qua tui do nguoi choi

            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) continue;

            String name = normalizeForMatch(stack.getName().getString());
            if (name.contains("kinh nghiem") || name.contains("linh dich")) {
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
