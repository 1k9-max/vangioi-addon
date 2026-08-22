package vn.vangioi.autofish.client;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.screen.Screen;

public final class b extends Screen {
    private static final int B = 510;
    private static final int C = 342;
    private static final int D = 32;
    private static final int E = 108;
    private static final int F = -11941633;
    private static final int G = -15255477;
    private static final int H = -985862;
    private static final int I = -7233355;
    private static final int J = -10230107;
    private static final int K = -14249;
    private static final int L = -36472;
    private static final int M = -267381474;
    private static final int N = -183034584;
    private static final int O = -921297624;
    private static final int P = -14011324;
    private static final DateTimeFormatter Q = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());
    private final g R;
    private final f S;
    private final vn.vangioi.autofish.client.a T;
    private a U;
    private int V;
    private int W;
    private boolean X;
    private int Y;
    private int Z;
    private TextFieldWidget aa;
    private TextFieldWidget ab;
    private h ac;
    private h ad;
    private h ae;
    private h af;
    private final h[] ag;
    private final h[] ah;

    private enum a {
        HOME,
        LICENSE,
        SETTINGS
    }

    public b(g gVar, f fVar, vn.vangioi.autofish.client.a aVar) {
        super(Text.literal(vn.vangioi.autofish.client.i.l("Vạn Giới Auto Fish")));
        this.U = a.HOME;
        this.ag = new h[9];
        this.ah = new h[9];
        this.R = gVar;
        this.S = fVar;
        this.T = aVar;
    }

    protected void init() {
        int iMax = Math.max(0, this.width - B);
        int iMax2 = Math.max(0, this.height - C);
        this.V = this.R.uiX >= 0 ? Math.min(this.R.uiX, iMax) : Math.max(8, (this.width - B) / 2);
        this.W = this.R.uiY >= 0 ? Math.min(this.R.uiY, iMax2) : Math.max(8, (this.height - C) / 2);
        n();
    }

    private void n() {
        clearChildren();
        this.aa = null;
        this.ab = null;
        this.ac = null;
        this.ad = null;
        this.ae = null;
        this.af = null;
        for (int i = 0; i < 9; i++) {
            this.ag[i] = null;
            this.ah[i] = null;
        }
        addDrawableChild(new h(this.V + 10, this.W + 50, 88, 24, "trang chủ", class_4185Var -> {
            a(a.HOME);
        }).b(this.U == a.HOME));
        addDrawableChild(new h(this.V + 10, this.W + 82, 88, 24, "license", class_4185Var2 -> {
            a(a.LICENSE);
        }).b(this.U == a.LICENSE));
        addDrawableChild(new h(this.V + 10, this.W + 114, 88, 24, "cài đặt", class_4185Var3 -> {
            a(a.SETTINGS);
        }).b(this.U == a.SETTINGS));
        addDrawableChild(new h((this.V + B) - 28, this.W + 7, 20, 18, "x", class_4185Var4 -> {
            close();
        }).c(true));
        switch (this.U) {
            case HOME:
                o();
                break;
            case LICENSE:
                p();
                break;
            case SETTINGS:
                q();
                break;
        }
        u();
    }

    private void a(a aVar) {
        t();
        this.U = aVar;
        n();
    }

    private void o() {
        int iR = r();
        int iS = s();
        this.ac = addDrawableChild(new h(iR, this.W + 154, iS, 30, "bật auto · f8", class_4185Var -> {
            t();
            this.T.a(MinecraftClient.getInstance());
            u();
        }).b(true));
        int i = (iS - 8) / 2;
        addDrawableChild(new h(iR, this.W + 194, i, 24, "license", class_4185Var2 -> {
            a(a.LICENSE);
        }));
        addDrawableChild(new h(iR + i + 8, this.W + 194, i, 24, "cài đặt slot", class_4185Var3 -> {
            a(a.SETTINGS);
        }));
    }

    private void p() {
        int iR = r();
        int iS = s();
        if (this.S.R()) {
            addDrawableChild(new h(iR, this.W + 119, iS, 30, "owner build · full access", class_4185Var -> {
            }).b(true));
            return;
        }
        this.aa = new TextFieldWidget(this.textRenderer, iR + 10, this.W + 74, iS - 20, 21, Text.literal(vn.vangioi.autofish.client.i.l("license key")));
        this.aa.setMaxLength(128);
        this.aa.setText(this.R.licenseKey == null ? "" : this.R.licenseKey);
        this.aa.setPlaceholder(Text.literal("VGN-XXXX-XXXX-XXXX-XXXX"));
        addDrawableChild(this.aa);
        addDrawableChild(new h(iR + 10, this.W + 102, (iS - 26) - 78, 22, "xác minh key", class_4185Var2 -> {
            t();
            this.S.j(MinecraftClient.getInstance());
        }).b(true));
        addDrawableChild(new h(((iR + iS) - 10) - 78, this.W + 102, 78, 22, "xóa key", class_4185Var3 -> {
            this.S.Z();
            if (this.aa != null) {
                this.aa.setText("");
            }
        }).c(true));
        addDrawableChild(new h(iR + 10, this.W + 164, iS - 20, 22, "1 · mở work.ink · free 12h", class_4185Var4 -> {
            this.S.k(MinecraftClient.getInstance());
        }).b(true));
        this.ab = new TextFieldWidget(this.textRenderer, iR + 10, this.W + 192, iS - 20, 21, Text.literal(vn.vangioi.autofish.client.i.l("token work.ink sau quảng cáo")));
        this.ab.setMaxLength(128);
        this.ab.setPlaceholder(Text.literal("xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"));
        addDrawableChild(this.ab);
        addDrawableChild(new h(iR + 10, this.W + 220, iS - 20, 22, "2 · dùng token · nhận free 12h", class_4185Var5 -> {
            this.S.f(MinecraftClient.getInstance(), this.ab == null ? "" : this.ab.getText().trim());
            if (this.ab != null) {
                this.ab.setText("");
            }
        }).b(true));
    }

    private void q() {
        int iR = r();
        int iS = s();
        int i = iR + ((iS - ((34 * 9) + (4 * 8))) / 2);
        int i2 = this.W + 94;
        int i3 = this.W + 174;
        for (int i4 = 0; i4 < 9; i4++) {
            int i5 = i4 + 1;
            int i6 = i + (i4 * (34 + 4));
            this.ag[i4] = (h) addDrawableChild(new h(i6, i2, 34, 23, String.valueOf(i5), class_4185Var -> {
                if (i5 != this.R.baitHotbarSlot) {
                    this.R.rodHotbarSlot = i5;
                    this.R.ap();
                }
                u();
            }));
            this.ah[i4] = (h) addDrawableChild(new h(i6, i3, 34, 23, String.valueOf(i5), class_4185Var2 -> {
                if (i5 != this.R.rodHotbarSlot) {
                    this.R.baitHotbarSlot = i5;
                    this.R.ap();
                }
                u();
            }));
        }
        int i7 = this.W + 252;
        this.ad = addDrawableChild(new h(iR, i7, 112, 28, "gắn mồi", class_4185Var3 -> {
            this.R.autoRebait = !this.R.autoRebait;
            this.R.ap();
            u();
        }));
        this.ae = addDrawableChild(new h(iR + 112 + 6, i7, 112, 28, "câu lại", class_4185Var4 -> {
            this.R.autoRecast = !this.R.autoRecast;
            this.R.ap();
            u();
        }));
        this.af = addDrawableChild(new h(iR + (112 * 2) + 12, i7, (iS - (112 * 2)) - 12, 28, "exact", class_4185Var5 -> {
            this.R.preferExactProtocol = !this.R.preferExactProtocol;
            this.R.ap();
            u();
        }));
    }

    private int r() {
        return this.V + E + 16;
    }

    private int s() {
        return 374;
    }

    private void t() {
        if (this.aa != null) {
            this.R.licenseKey = this.aa.getText().trim();
        }
        this.R.uiX = this.V;
        this.R.uiY = this.W;
        this.R.ap();
    }

    private void u() {
        if (this.ac != null) {
            this.ac.setMessage(Text.literal(vn.vangioi.autofish.client.i.l(this.T.a() ? "dừng auto · f8" : "bật auto · f8")));
            this.ac.b(!this.T.a()).c(this.T.a());
        }
        if (this.ad != null) {
            this.ad.setMessage(Text.literal(vn.vangioi.autofish.client.i.l("gắn mồi: " + a(this.R.autoRebait))));
            this.ad.b(this.R.autoRebait);
        }
        if (this.ae != null) {
            this.ae.setMessage(Text.literal(vn.vangioi.autofish.client.i.l("câu lại: " + a(this.R.autoRecast))));
            this.ae.b(this.R.autoRecast);
        }
        if (this.af != null) {
            this.af.setMessage(Text.literal(vn.vangioi.autofish.client.i.l("exact: " + a(this.R.preferExactProtocol))));
            this.af.b(this.R.preferExactProtocol);
        }
        for (int i = 0; i < 9; i++) {
            if (this.ag[i] != null) {
                this.ag[i].b(this.R.rodHotbarSlot == i + 1);
                this.ag[i].active = this.R.baitHotbarSlot != i + 1 || this.R.rodHotbarSlot == i + 1;
            }
            if (this.ah[i] != null) {
                this.ah[i].b(this.R.baitHotbarSlot == i + 1);
                this.ah[i].active = this.R.rodHotbarSlot != i + 1 || this.R.baitHotbarSlot == i + 1;
            }
        }
    }

    private static String a(boolean z) {
        return z ? "bật" : "tắt";
    }

    public void tick() {
        super.tick();
        u();
    }

    protected void applyBlur() {
    }

    public void renderBackground(DrawContext class_332Var, int i, int i2, float f) {
    }

    public void render(DrawContext class_332Var, int i, int i2, float f) {
        a(class_332Var);
        switch (this.U) {
            case HOME:
                b(class_332Var);
                break;
            case LICENSE:
                c(class_332Var);
                break;
            case SETTINGS:
                d(class_332Var);
                break;
        }
        super.render(class_332Var, i, i2, f);
    }

    private void a(DrawContext class_332Var) {
        int i = this.V + B;
        int i2 = this.W + C;
        class_332Var.fill(this.V + 4, this.W + 5, i + 6, i2 + 7, 1711276032);
        class_332Var.fill(this.V - 1, this.W - 1, i + 1, i2 + 1, -14076603);
        class_332Var.fill(this.V, this.W, i, i2, M);
        class_332Var.fill(this.V, this.W, i, this.W + D, N);
        class_332Var.fill(this.V, this.W, i, this.W + 2, F);
        class_332Var.fill(this.V + E, this.W + D, this.V + E + 1, i2, -14077118);
        class_332Var.fill(this.V, this.W + D, i, this.W + D + 1, -14077118);
        class_332Var.drawTextWithShadow(this.textRenderer, Text.literal(vn.vangioi.autofish.client.i.l("Vạn Giới · Auto Fish")), this.V + 12, this.W + 12, H);
        class_332Var.drawTextWithShadow(this.textRenderer, Text.literal(vn.vangioi.autofish.client.i.l(v())), r(), this.W + 12, F);
        class_332Var.drawTextWithShadow(this.textRenderer, Text.literal(vn.vangioi.autofish.client.i.l("1.21.4 · Fabric · " + (this.S.R() ? "OWNER" : "MEMBER · BRIDGE"))), this.V + 12, (this.W + C) - 15, I);
    }

    private String v() throws MatchException {
        switch (this.U) {
            case HOME:
                return "trang chủ";
            case LICENSE:
                return "license";
            case SETTINGS:
                return "cài đặt";
            default:
                throw new MatchException((String) null, (Throwable) null);
        }
    }

    private void b(DrawContext class_332Var) {
        int i;
        int iR = r();
        int iS = s();
        int i2 = this.W + 48;
        a(class_332Var, iR, i2, iS, 96, false);
        f.b bVarS = this.S.S();
        if (bVarS.ad()) {
            i = J;
        } else {
            i = (bVarS.ae() == f.c.CHECKING || bVarS.ae() == f.c.CONNECTING) ? K : L;
        }
        int i3 = i;
        a(class_332Var, iR + 10, i2 + 9, "trạng thái hệ thống");
        a(class_332Var, iR + 10, i2 + 27, "auto", this.T.c(), this.T.a() ? J : H);
        a(class_332Var, iR + 10, i2 + 42, "license", bVarS.ag(), i3);
        a(class_332Var, iR + 10, i2 + 57, "nhận diện", this.T.j() ? "exact server state" : "actionbar fallback", this.T.j() ? J : I);
        String str = "slot " + this.R.rodHotbarSlot + " · " + a(this.T.f(this.client), 19);
        String str2 = "slot " + this.R.baitHotbarSlot + " · " + a(this.T.g(this.client), 19);
        a(class_332Var, iR + 10, i2 + 72, "cần", str, H);
        a(class_332Var, iR + 10, i2 + 84, "mồi", str2, H);
        a(class_332Var, iR, this.W + 229, iS, 64, false);
        a(class_332Var, iR + 10, this.W + 238, "thống kê phiên");
        TextRenderer class_327Var = this.textRenderer;
        long jD = this.T.d();
        long jE = this.T.e();
        this.T.f();
        class_332Var.drawTextWithShadow(class_327Var, Text.literal(vn.vangioi.autofish.client.i.l("lượt " + jD + "  ·  bắt " + class_332Var + "  ·  kết thúc " + jE)), iR + 10, this.W + 256, I);
        class_332Var.drawTextWithShadow(this.textRenderer, Text.literal(vn.vangioi.autofish.client.i.l("client/network · alt-tab vẫn hoạt động khi Minecraft còn chạy")), iR + 10, this.W + 274, -8530948);
    }

    private void c(DrawContext class_332Var) {
        int i;
        int iR = r();
        int iS = s();
        f.b bVarS = this.S.S();
        if (this.S.R()) {
            a(class_332Var, iR, this.W + 58, iS, 106, true);
            a(class_332Var, iR + 12, this.W + 70, "owner edition");
            class_332Var.drawTextWithShadow(this.textRenderer, Text.literal(vn.vangioi.autofish.client.i.l("full access · không cần key")), iR + 12, this.W + 91, J);
            class_332Var.drawTextWithShadow(this.textRenderer, Text.literal(vn.vangioi.autofish.client.i.l("không phụ thuộc bridge để mở tính năng")), iR + 12, this.W + 106, H);
            class_332Var.drawTextWithShadow(this.textRenderer, Text.literal(vn.vangioi.autofish.client.i.l("không phát file owner cho member")), iR + 12, this.W + 137, K);
            return;
        }
        a(class_332Var, iR, this.W + 48, iS, 84, false);
        a(class_332Var, iR + 10, this.W + 57, "key trả phí");
        a(class_332Var, iR, this.W + 140, iS, 110, false);
        a(class_332Var, iR + 10, this.W + 149, "free 12h · work.ink");
        a(class_332Var, iR, this.W + 258, iS, 54, false);
        if (bVarS.ad()) {
            i = J;
        } else {
            i = (bVarS.ae() == f.c.CHECKING || bVarS.ae() == f.c.CONNECTING) ? K : L;
        }
        int i2 = i;
        long jAi = bVarS.ai();
        String str = jAi > 0 ? Q.format(Instant.ofEpochSecond(jAi)) : "-";
        a(class_332Var, iR + 10, this.W + 268, "bridge", this.S.T() ? "đã kết nối" : "chưa kết nối", this.S.T() ? J : L);
        a(class_332Var, iR + 10, this.W + 283, "trạng thái", bVarS.ae() == f.c.EXPIRED ? "HẾT HẠN · cần gia hạn/kích hoạt lại" : a(bVarS.ag(), 34), i2);
        a(class_332Var, iR + 10, this.W + 298, "hết hạn", str, I);
    }

    private void d(DrawContext class_332Var) {
        int iR = r();
        int iS = s();
        a(class_332Var, iR, this.W + 50, iS, 76, false);
        a(class_332Var, iR + 10, this.W + 59, "hotbar cần câu");
        class_332Var.drawTextWithShadow(this.textRenderer, Text.literal(vn.vangioi.autofish.client.i.l("đang chọn: " + this.R.rodHotbarSlot + " · " + a(this.T.f(this.client), 29))), iR + 10, this.W + 76, I);
        a(class_332Var, iR, this.W + 130, iS, 76, false);
        a(class_332Var, iR + 10, this.W + 139, "hotbar mồi");
        class_332Var.drawTextWithShadow(this.textRenderer, Text.literal(vn.vangioi.autofish.client.i.l("đang chọn: " + this.R.baitHotbarSlot + " · " + a(this.T.g(this.client), 29))), iR + 10, this.W + 156, I);
        int i = this.W + 214;
        class_332Var.fill(iR, i, iR + iS, i + 28, -1439882220);
        class_332Var.fill(iR, i, iR + 2, i + 28, K);
        class_332Var.fill(iR, i, iR + iS, i + 1, -9744095);
        class_332Var.drawTextWithShadow(this.textRenderer, Text.literal(vn.vangioi.autofish.client.i.l("!  slot cần và mồi không được trùng")), iR + 10, i + 10, K);
        class_332Var.drawTextWithShadow(this.textRenderer, Text.literal(vn.vangioi.autofish.client.i.l("exact ưu tiên dữ liệu trực tiếp từ Skript · actionbar là fallback")), iR, this.W + 289, I);
    }

    private void a(DrawContext class_332Var, int i, int i2, int i3, int i4, boolean z) {
        class_332Var.fill(i, i2, i + i3, i2 + i4, O);
        class_332Var.fill(i, i2, i + i3, i2 + 1, z ? F : P);
        class_332Var.fill(i, (i2 + i4) - 1, i + i3, i2 + i4, -1442181354);
        class_332Var.fill(i, i2, i + 1, i2 + i4, z ? F : P);
        class_332Var.fill((i + i3) - 1, i2, i + i3, i2 + i4, P);
    }

    private void a(DrawContext class_332Var, int i, int i2, String str) {
        class_332Var.fill(i, i2 + 1, i + 2, i2 + 10, F);
        class_332Var.drawTextWithShadow(this.textRenderer, Text.literal(vn.vangioi.autofish.client.i.l(str)), i + 7, i2, H);
    }

    private void a(DrawContext class_332Var, int i, int i2, String str, String str2, int i3) {
        String strL = vn.vangioi.autofish.client.i.l(str + " · ");
        class_332Var.drawTextWithShadow(this.textRenderer, Text.literal(strL), i, i2, I);
        class_332Var.drawTextWithShadow(this.textRenderer, Text.literal(vn.vangioi.autofish.client.i.l(str2)), i + this.textRenderer.getWidth(strL), i2, i3);
    }

    public boolean mouseClicked(double d, double d2, int i) {
        if (super.mouseClicked(d, d2, i)) {
            return true;
        }
        if (i != 0 || !a(d, d2, this.V, this.W, 476, D)) {
            return false;
        }
        this.X = true;
        this.Y = ((int) d) - this.V;
        this.Z = ((int) d2) - this.W;
        return true;
    }

    public boolean mouseDragged(double d, double d2, int i, double d3, double d4) {
        if (!this.X || i != 0) {
            return super.mouseDragged(d, d2, i, d3, d4);
        }
        int iA = a(((int) d) - this.Y, 0, Math.max(0, this.width - B));
        int iA2 = a(((int) d2) - this.Z, 0, Math.max(0, this.height - C));
        a(iA - this.V, iA2 - this.W);
        this.V = iA;
        this.W = iA2;
        return true;
    }

    public boolean mouseReleased(double d, double d2, int i) {
        if (!this.X || i != 0) {
            return super.mouseReleased(d, d2, i);
        }
        this.X = false;
        this.R.uiX = this.V;
        this.R.uiY = this.W;
        this.R.ap();
        return true;
    }

    private void a(int i, int i2) {
        if (i == 0 && i2 == 0) {
            return;
        }
        for (Element class_339Var : children()) {
            if (class_339Var instanceof ClickableWidget) {
                ClickableWidget class_339Var2 = (ClickableWidget) class_339Var;
                class_339Var2.setX(class_339Var2.getX() + i);
                class_339Var2.setY(class_339Var2.getY() + i2);
            }
        }
    }

    private static boolean a(double d, double d2, int i, int i2, int i3, int i4) {
        return d >= ((double) i) && d < ((double) (i + i3)) && d2 >= ((double) i2) && d2 < ((double) (i2 + i4));
    }

    private static int a(int i, int i2, int i3) {
        return Math.max(i2, Math.min(i3, i));
    }

    private static String a(String str, int i) {
        if (str == null || str.isBlank()) {
            return "trống";
        }
        return str.length() <= i ? str : str.substring(0, Math.max(1, i - 1)) + "…";
    }

    public boolean shouldPause() {
        return false;
    }

    public void removed() {
        t();
        super.removed();
    }
}
