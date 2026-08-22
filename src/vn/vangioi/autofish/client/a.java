package vn.vangioi.autofish.client;

import java.util.Locale;
import java.util.Optional;
import net.minecraft.util.Hand;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

public final class a {
    private final g a;
    private final f b;
    private boolean e;
    private int h;
    private long i;
    private long j;
    private e.a k;
    private d.a l;
    private boolean n;
    private boolean o;
    private long p;
    private long q;
    private long r;
    private long s;
    private final e c = new e();
    private final d d = new d();
    private EnumC0000a f = EnumC0000a.OFF;
    private String g = "Đã dừng";
    private double m = Double.NaN;
    private Item lastBaitItem;
    private boolean justRefilledBait;
    private EnumC0000a lastObservedState;
    private long stateEnteredAtNanos;
    private int rodMissingStreak;
    private boolean hookSeenThisCast;

    public enum EnumC0000a {
        OFF,
        PREPARING,
        ATTACHING_BAIT,
        SELECTING_ROD,
        WAITING_BITE,
        FIGHTING,
        ERROR
    }

    public a(g gVar, f fVar) {
        this.a = gVar;
        this.b = fVar;
    }

    public boolean a() {
        return this.e;
    }

    public EnumC0000a b() {
        return this.f;
    }

    public String c() {
        return this.g;
    }

    public long d() {
        return this.q;
    }

    public long e() {
        return this.r;
    }

    public long f() {
        return this.s;
    }

    public e.a g() {
        return this.k;
    }

    public d.a h() {
        return this.l;
    }

    public boolean i() {
        return this.o;
    }

    public boolean j() {
        return this.o && this.p > 0 && System.nanoTime() - this.p < 2000000000;
    }

    public void a(MinecraftClient class_310Var) {
        if (this.e) {
            a(class_310Var, "Đã dừng");
            return;
        }
        if (!this.b.W() || this.b.X() == 0) {
            boolean z = this.b.S().ae() == vn.vangioi.autofish.client.f.c.EXPIRED;
            this.g = z ? "License đã hết hạn" : "License chưa hợp lệ";
            e(class_310Var, z ? "[AutoFish] License đã hết hạn. Gia hạn hoặc kích hoạt license mới." : "[AutoFish] License chưa hợp lệ. Mở Right Shift để kích hoạt.");
        } else if (a(class_310Var, true)) {
            this.e = true;
            b(class_310Var, true);
            this.f = EnumC0000a.PREPARING;
            this.h = 2;
            this.g = "Chuẩn bị câu";
            this.m = Double.NaN;
            this.lastObservedState = null;
            this.rodMissingStreak = 0;
            e(class_310Var, "[AutoFish] Đã bật auto client-side.");
        }
    }

    public void a(MinecraftClient class_310Var, String str) {
        boolean z = this.e;
        this.e = false;
        this.f = EnumC0000a.OFF;
        this.g = str;
        this.k = null;
        this.l = null;
        this.m = Double.NaN;
        h(class_310Var);
        if (z) {
            b(class_310Var, false);
        }
    }

    public boolean b(MinecraftClient class_310Var, String str) {
        if (!this.d.f(str)) {
            return false;
        }
        Optional<Boolean> optionalG = this.d.g(str);
        if (optionalG.isPresent()) {
            this.o = optionalG.get().booleanValue();
            if (this.o) {
                return true;
            }
            this.l = null;
            this.p = 0L;
            return true;
        }
        Optional<d.a> optionalH = this.d.h(str);
        if (!optionalH.isPresent()) {
            return true;
        }
        this.o = true;
        this.l = optionalH.get();
        this.p = this.l.K();
        this.i = this.p;
        if (!this.e || !k()) {
            return true;
        }
        if (this.f != EnumC0000a.FIGHTING) {
            this.q++;
            this.m = Double.NaN;
        }
        this.f = EnumC0000a.FIGHTING;
        this.g = "Đang kéo cá - EXACT " + this.l.J() + "%";
        a(class_310Var, this.l);
        return true;
    }

    public void a(MinecraftClient class_310Var, String str, boolean z) {
        String str2;
        if (str == null) {
            return;
        }
        if (z) {
            Optional<e.a> optionalI = this.c.i(str);
            if (optionalI.isPresent()) {
                this.k = optionalI.get();
                this.i = System.nanoTime();
                if (this.e && k()) {
                    if (this.f != EnumC0000a.FIGHTING) {
                        this.q++;
                        this.m = Double.NaN;
                    }
                    this.f = EnumC0000a.FIGHTING;
                    if (j()) {
                        str2 = "Đang kéo cá - EXACT " + (this.l == null ? this.k.J() : this.l.J()) + "%";
                    } else {
                        str2 = "Đang kéo cá - ActionBar " + this.k.J() + "%";
                    }
                    this.g = str2;
                    if (j()) {
                        return;
                    }
                    a(class_310Var, this.k);
                    return;
                }
                return;
            }
        }
        String lowerCase = str.toLowerCase(Locale.ROOT);
        if (lowerCase.contains("đã ᴄâᴜ đượᴄ ᴄá") || lowerCase.contains("đã câu được cá")) {
            this.r++;
            if (this.e) {
                c(class_310Var, "Câu thành công");
                return;
            }
            return;
        }
        if (this.e && (lowerCase.contains("ᴄầɴ ᴘʜảɪ ɢắɴ ᴍồi") || lowerCase.contains("cần phải gắn mồi"))) {
            h(class_310Var);
            this.f = EnumC0000a.PREPARING;
            this.h = 5;
            this.g = "Server yêu cầu gắn mồi lại";
            return;
        }
        if (this.e) {
            if (lowerCase.contains("ᴋʜôɴɢ ᴄó ʟᴏàɪ ᴄá ɴàᴏ ᴘʜù ʜợᴘ") || lowerCase.contains("không có loại cá nào phù hợp")) {
                d(class_310Var, "Mồi/biome hiện tại không có cá phù hợp");
            }
        }
    }

    public void b(MinecraftClient class_310Var) {
        if (this.e) {
            if (class_310Var.player == null || class_310Var.interactionManager == null) {
                a(class_310Var, "Không ở trong thế giới");
                return;
            }
            if (!k()) {
                boolean z = this.b.S().ae() == vn.vangioi.autofish.client.f.c.EXPIRED;
                a(class_310Var, z ? "License đã hết hạn" : "License hết hiệu lực");
                if (z) {
                    return;
                }
                e(class_310Var, "[AutoFish] License không còn hiệu lực.");
                return;
            }

            // Chỉ kiểm tra mất phao khi đang chờ cá cắn (WAITING_BITE). 
            // Khi đã vào FIGHTING, cho phép tiếp tục đánh mini-game kể cả khi server clear phao.
            if (this.f == EnumC0000a.WAITING_BITE) {
                boolean hookPresent = class_310Var.player.fishHook != null && !class_310Var.player.fishHook.isRemoved();
                if (hookPresent) {
                    this.hookSeenThisCast = true;
                } else if (this.hookSeenThisCast) {
                    c(class_310Var, "Phao câu đã biến mất bất ngờ - câu lại");
                    return;
                }
            }

            // Watchdog: 3 phút (180000ms) cho WAITING_BITE, 2 phút (120000ms) cho các trạng thái khác/FIGHTING
            if (this.f != this.lastObservedState) {
                this.lastObservedState = this.f;
                this.stateEnteredAtNanos = System.nanoTime();
            } else {
                long watchdogMs = (this.f == EnumC0000a.WAITING_BITE) ? 180000 : 120000;
                if ((System.nanoTime() - this.stateEnteredAtNanos) / 1000000 > watchdogMs) {
                    c(class_310Var, "Watchdog: không hoạt động quá thời gian cho phép - tự khởi động lại");
                    return;
                }
            }

            try {
                if (this.f == EnumC0000a.FIGHTING) {
                    if ((System.nanoTime() - this.i) / 1000000 > this.a.actionbarTimeoutMs) {
                        this.s++;
                        c(class_310Var, "Kết thúc lượt - chuẩn bị lượt mới");
                        return;
                    } else if (j() && this.l != null) {
                        a(class_310Var, this.l);
                        return;
                    } else {
                        if (this.k != null) {
                            a(class_310Var, this.k);
                            return;
                        }
                        return;
                    }
                }
                h(class_310Var);
                if (this.h > 0) {
                    this.h--;
                    return;
                }
                switch (this.f.ordinal()) {
                    case 1:
                        c(class_310Var);
                        break;
                    case 2:
                        d(class_310Var);
                        break;
                    case 3:
                        e(class_310Var);
                        break;
                    case 4:
                        if ((System.nanoTime() - this.j) / 1000000 > 70000) {
                            class_310Var.interactionManager.interactItem(class_310Var.player, Hand.MAIN_HAND);
                            c(class_310Var, "Timeout chờ cá cắn");
                        }
                        break;
                }
            } catch (Exception ex) {
                c(class_310Var, "Lỗi xử lý (" + ex.getClass().getSimpleName() + ") - tự khởi động lại");
            }
        }
    }

    private boolean k() {
        return this.b.W() && this.b.X() != 0;
    }

    private void c(MinecraftClient class_310Var) {
        if (a(class_310Var, true)) {
            if (this.justRefilledBait) {
                this.justRefilledBait = false;
                this.h = 3;
                this.g = "Vừa refill mồi - chờ đồng bộ...";
                return;
            }
            if (!this.a.autoRebait) {
                this.f = EnumC0000a.SELECTING_ROD;
                this.h = 1;
                this.g = "Chọn cần câu";
                return;
            }
            ClientPlayerEntity class_746Var = class_310Var.player;
            if (class_746Var.currentScreenHandler != class_746Var.playerScreenHandler) {
                this.g = "Đang chờ đóng GUI inventory khác";
                this.h = 5;
                return;
            }
            int i = this.a.baitHotbarSlot - 1;
            int i2 = this.a.rodHotbarSlot - 1;
            PlayerScreenHandler class_1723Var = class_746Var.playerScreenHandler;
            int iA = a((ScreenHandler) class_1723Var, class_746Var, i);
            int iA2 = a((ScreenHandler) class_1723Var, class_746Var, i2);
            if (iA < 0 || iA2 < 0) {
                d(class_310Var, "Không ánh xạ được Hotbar cần/mồi");
                return;
            }
            class_310Var.interactionManager.clickSlot(((ScreenHandler) class_1723Var).syncId, iA, 0, SlotActionType.PICKUP, class_746Var);
            class_310Var.interactionManager.clickSlot(((ScreenHandler) class_1723Var).syncId, iA2, 0, SlotActionType.PICKUP, class_746Var);
            this.f = EnumC0000a.ATTACHING_BAIT;
            this.h = this.a.attachDelayTicks;
            this.g = "Đang gắn mồi từ Hotbar " + this.a.baitHotbarSlot + " vào cần Hotbar " + this.a.rodHotbarSlot;
        }
    }

    private void d(MinecraftClient class_310Var) {
        if (a(class_310Var, false)) {
            class_310Var.player.getInventory().setSelectedSlot(this.a.rodHotbarSlot - 1);
            this.f = EnumC0000a.SELECTING_ROD;
            this.h = 3;
            this.g = "Đang chọn cần - Hotbar " + this.a.rodHotbarSlot;
        }
    }

    private void e(MinecraftClient class_310Var) {
        ClientPlayerEntity class_746Var = class_310Var.player;
        class_746Var.getInventory().setSelectedSlot(this.a.rodHotbarSlot - 1);
        if (!class_746Var.getMainHandStack().isOf(Items.FISHING_ROD)) {
            d(class_310Var, "Hotbar " + this.a.rodHotbarSlot + " không phải cần câu");
            return;
        }
        if (!k()) {
            d(class_310Var, "License không còn hiệu lực");
            return;
        }
        class_310Var.interactionManager.interactItem(class_746Var, Hand.MAIN_HAND);
        class_746Var.swingHand(Hand.MAIN_HAND);
        this.f = EnumC0000a.WAITING_BITE;
        this.j = System.nanoTime();
        this.h = this.a.castSettleTicks;
        this.g = "Đã thả câu - chờ cá cắn";
        this.hookSeenThisCast = false;
    }

    private boolean a(MinecraftClient class_310Var, boolean z) {
        if (class_310Var == null || class_310Var.player == null) {
            this.g = "Chưa vào server";
            return false;
        }
        if (this.a.rodHotbarSlot < 1 || this.a.rodHotbarSlot > 9 || this.a.baitHotbarSlot < 1 || this.a.baitHotbarSlot > 9) {
            d(class_310Var, "Slot Hotbar phải từ 1 đến 9");
            return false;
        }
        if (this.a.rodHotbarSlot == this.a.baitHotbarSlot) {
            d(class_310Var, "Slot cần và mồi không được trùng nhau");
            return false;
        }
        if (!class_310Var.player.getInventory().getStack(this.a.rodHotbarSlot - 1).isOf(Items.FISHING_ROD)) {
            this.rodMissingStreak++;
            if (this.rodMissingStreak < 5) {
                this.g = "Đang kiểm tra lại Hotbar cần câu...";
                return false;
            }
            d(class_310Var, "Hotbar " + this.a.rodHotbarSlot + " không có cần câu");
            return false;
        }
        this.rodMissingStreak = 0;
        if (!autoSwitchRodIfNeeded(class_310Var)) {
            d(class_310Var, "Cần câu gần hỏng và không tìm được cần thay thế trong túi đồ");
            return false;
        }
        if (!z || !this.a.autoRebait) {
            return true;
        }
        ItemStack class_1799VarMethod_5438 = class_310Var.player.getInventory().getStack(this.a.baitHotbarSlot - 1);
        if (!class_1799VarMethod_5438.isEmpty() && !class_1799VarMethod_5438.isOf(Items.FISHING_ROD)) {
            this.lastBaitItem = class_1799VarMethod_5438.getItem();
            this.justRefilledBait = false;
            return true;
        }
        if (refillBait(class_310Var)) {
            this.justRefilledBait = true;
            return true;
        }
        d(class_310Var, "Hotbar " + this.a.baitHotbarSlot + " không có mồi hợp lệ" + (this.lastBaitItem != null ? " (đã hết mồi cùng loại trong túi đồ)" : ""));
        return false;
    }

    private static final int ROD_NEAR_BROKEN_THRESHOLD = 5;

    private boolean autoSwitchRodIfNeeded(MinecraftClient class_310Var) {
        ClientPlayerEntity class_746Var = class_310Var.player;
        int iRodDest = this.a.rodHotbarSlot - 1;
        ItemStack rodStack = class_746Var.getInventory().getStack(iRodDest);
        if (rodStack.isEmpty() || !rodStack.isOf(Items.FISHING_ROD)) {
            return true;
        }
        int iRemaining = rodStack.getMaxDamage() - rodStack.getDamage();
        if (rodStack.getMaxDamage() <= 0 || iRemaining > ROD_NEAR_BROKEN_THRESHOLD) {
            return true;
        }
        if (class_746Var.currentScreenHandler != class_746Var.playerScreenHandler) {
            return false;
        }
        int iBait = this.a.baitHotbarSlot - 1;
        for (int i = 0; i < 36; i++) {
            if (i == iRodDest || i == iBait) {
                continue;
            }
            ItemStack candidate = class_746Var.getInventory().getStack(i);
            if (candidate.isEmpty() || !candidate.isOf(Items.FISHING_ROD)) {
                continue;
            }
            int iCandRemaining = candidate.getMaxDamage() - candidate.getDamage();
            if (candidate.getMaxDamage() > 0 && iCandRemaining > ROD_NEAR_BROKEN_THRESHOLD) {
                PlayerScreenHandler class_1723Var = class_746Var.playerScreenHandler;
                int iSrcSlot = a((ScreenHandler) class_1723Var, class_746Var, i);
                int iDestSlot = a((ScreenHandler) class_1723Var, class_746Var, iRodDest);
                if (iSrcSlot < 0 || iDestSlot < 0) {
                    return false;
                }
                class_310Var.interactionManager.clickSlot(((ScreenHandler) class_1723Var).syncId, iDestSlot, 0, SlotActionType.PICKUP, class_746Var);
                class_310Var.interactionManager.clickSlot(((ScreenHandler) class_1723Var).syncId, iSrcSlot, 0, SlotActionType.PICKUP, class_746Var);
                class_310Var.interactionManager.clickSlot(((ScreenHandler) class_1723Var).syncId, iDestSlot, 0, SlotActionType.PICKUP, class_746Var);
                this.g = "Đã tự động đổi cần câu (cần cũ gần hỏng đã đưa ra khỏi Hotbar)";
                return true;
            }
        }
        return false;
    }

    private boolean refillBait(MinecraftClient class_310Var) {
        ClientPlayerEntity class_746Var = class_310Var.player;
        if (class_746Var.currentScreenHandler != class_746Var.playerScreenHandler) {
            return false;
        }
        int iDest = this.a.baitHotbarSlot - 1;
        int iRod = this.a.rodHotbarSlot - 1;

        if (this.lastBaitItem != null) {
            if (doRefillFrom(class_310Var, class_746Var, iDest, iRod, itemStack -> itemStack.isOf(this.lastBaitItem))) {
                return true;
            }
        }
        return doRefillFrom(class_310Var, class_746Var, iDest, iRod, itemStack -> !itemStack.isOf(Items.FISHING_ROD));
    }

    private interface BaitMatcher {
        boolean test(ItemStack itemStack);
    }

    private boolean doRefillFrom(MinecraftClient class_310Var, ClientPlayerEntity class_746Var, int iDest, int iRod, BaitMatcher matcher) {
        for (int i = 0; i < 36; i++) {
            if (i == iDest || i == iRod) {
                continue;
            }
            ItemStack itemStack = class_746Var.getInventory().getStack(i);
            if (itemStack.isEmpty() || !matcher.test(itemStack)) {
                continue;
            }
            PlayerScreenHandler class_1723Var = class_746Var.playerScreenHandler;
            int iSrcSlot = a((ScreenHandler) class_1723Var, class_746Var, i);
            int iDestSlot = a((ScreenHandler) class_1723Var, class_746Var, iDest);
            if (iSrcSlot < 0 || iDestSlot < 0) {
                return false;
            }
            class_310Var.interactionManager.clickSlot(((ScreenHandler) class_1723Var).syncId, iSrcSlot, 0, SlotActionType.PICKUP, class_746Var);
            class_310Var.interactionManager.clickSlot(((ScreenHandler) class_1723Var).syncId, iDestSlot, 0, SlotActionType.PICKUP, class_746Var);
            this.lastBaitItem = itemStack.getItem();
            this.g = "Đã tự động refill mồi (" + this.lastBaitItem.getName().getString() + ") vào Hotbar " + this.a.baitHotbarSlot;
            return true;
        }
        return false;
    }

    public String f(MinecraftClient class_310Var) {
        return a(class_310Var, this.a.rodHotbarSlot);
    }

    public String g(MinecraftClient class_310Var) {
        return a(class_310Var, this.a.baitHotbarSlot);
    }

    private static String a(MinecraftClient class_310Var, int i) {
        if (class_310Var == null || class_310Var.player == null || i < 1 || i > 9) {
            return "-";
        }
        ItemStack class_1799VarMethod_5438 = class_310Var.player.getInventory().getStack(i - 1);
        return class_1799VarMethod_5438.isEmpty() ? "trống" : class_1799VarMethod_5438.getName().getString();
    }

    private void a(MinecraftClient class_310Var, d.a aVar) {
        if (this.e && class_310Var.player != null && k()) {
            double dA = aVar.A();
            if (aVar.G().equals("moving")) {
                dA += aVar.D() * ((double) aVar.E()) * 1.15d;
            }
            double dMax = Math.max(0.0d, Math.min(1.0d, dA));
            double dMax2 = Math.max(0.004d, aVar.C() / 2.0d);
            double dMax3 = (Math.max(0.0d, dMax - dMax2) + Math.min(1.0d, dMax + dMax2)) / 2.0d;
            double dMax4 = Math.max(0.0025d, Math.min(0.035d, aVar.C() * 0.12d));
            if (aVar.B() < dMax3 - dMax4) {
                c(class_310Var, true);
            } else if (aVar.B() > dMax3 + dMax4) {
                c(class_310Var, false);
            }
            this.g = "Đang kéo cá - EXACT " + aVar.J() + "%";
        }
    }

    private void a(MinecraftClient class_310Var, e.a aVar) {
        if (this.e && class_310Var.player != null && k()) {
            double dQ = Double.isNaN(this.m) ? 0.0d : aVar.Q() - this.m;
            this.m = aVar.Q();
            double dMax = Math.max(aVar.O(), Math.min(aVar.P(), aVar.Q() + (Math.max(-2.0d, Math.min(2.0d, dQ)) * this.a.predictionStrength)));
            double dN = aVar.N();
            double d = this.a.hysteresisCells;
            if (dN < dMax - d) {
                c(class_310Var, true);
            } else if (dN > dMax + d) {
                c(class_310Var, false);
            }
            this.g = "Đang kéo cá - " + aVar.J() + "%";
        }
    }

    private void c(MinecraftClient class_310Var, String str) {
        h(class_310Var);
        this.k = null;
        this.l = null;
        this.m = Double.NaN;
        if (this.e) {
            if (!this.a.autoRecast) {
                a(class_310Var, str + " - Auto recast tắt");
                return;
            }
            this.f = EnumC0000a.PREPARING;
            this.h = this.a.reprepareDelayTicks;
            this.g = str;
        }
    }

    private void b(MinecraftClient class_310Var, boolean z) {
        if (!this.a.preferExactProtocol || class_310Var == null || class_310Var.getNetworkHandler() == null) {
            return;
        }
        try {
            class_310Var.getNetworkHandler().sendChatCommand("vgfishmod " + (z ? "on" : "off"));
        } catch (Exception e) {
        }
    }

    private void c(MinecraftClient class_310Var, boolean z) {
        if (k()) {
            class_310Var.options.sneakKey.setPressed(z);
            this.n = z;
        }
    }

    private void h(MinecraftClient class_310Var) {
        if (class_310Var != null && class_310Var.options != null && this.n) {
            class_310Var.options.sneakKey.setPressed(false);
        }
        this.n = false;
    }

    private void d(MinecraftClient class_310Var, String str) {
        boolean z = this.e;
        this.f = EnumC0000a.ERROR;
        this.e = false;
        this.g = str;
        this.k = null;
        this.l = null;
        h(class_310Var);
        if (z) {
            b(class_310Var, false);
        }
        e(class_310Var, "[AutoFish] " + str);
    }

    private static int a(ScreenHandler class_1703Var, ClientPlayerEntity class_746Var, int i) {
        for (int i2 = 0; i2 < class_1703Var.slots.size(); i2++) {
            Slot class_1735Var = (Slot) class_1703Var.slots.get(i2);
            if (class_1735Var.inventory == class_746Var.getInventory() && class_1735Var.getIndex() == i) {
                return i2;
            }
        }
        return -1;
    }

    private static void e(MinecraftClient class_310Var, String str) {
        if (class_310Var == null || class_310Var.player == null) {
            return;
        }
        class_310Var.player.sendMessage(Text.literal(str), false);
    }
}
