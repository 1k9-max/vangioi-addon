package vn.vangioi.autofish.client;

import java.util.Objects;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;

public final class h extends ButtonWidget {
    private boolean bw;
    private boolean bx;

    public h(int i, int i2, int i3, int i4, String str, ButtonWidget.PressAction class_4241Var) {
        super(i, i2, i3, i4, Text.literal(vn.vangioi.autofish.client.i.l(str)), class_4241Var, DEFAULT_NARRATION_SUPPLIER);
    }

    public h b(boolean z) {
        this.bw = z;
        if (z) {
            this.bx = false;
        }
        return this;
    }

    public h c(boolean z) {
        this.bx = z;
        if (z) {
            this.bw = false;
        }
        return this;
    }

    protected void renderWidget(DrawContext class_332Var, int i, int i2, float f) {
        int i3;
        int i4;
        int i5;
        if (!this.active) {
            i3 = -1290396892;
            i4 = -14143169;
            i5 = -10918280;
        } else if (this.bx) {
            i3 = isHovered() ? -347724498 : -499965912;
            i4 = -39557;
            i5 = -7448;
        } else if (this.bw) {
            i3 = isHovered() ? -350401689 : -484820916;
            i4 = -11941633;
            i5 = -852993;
        } else {
            i3 = isHovered() ? -366530238 : -484761042;
            i4 = isHovered() ? -11377541 : -13287084;
            i5 = -1972240;
        }
        int iMethod_46426 = getX();
        int iMethod_46427 = getY();
        int i6 = iMethod_46426 + this.width;
        int i7 = iMethod_46427 + this.height;
        class_332Var.fill(iMethod_46426, iMethod_46427, i6, i7, i4);
        class_332Var.fill(iMethod_46426 + 1, iMethod_46427 + 1, i6 - 1, i7 - 1, i3);
        class_332Var.fill(iMethod_46426 + 1, iMethod_46427 + 1, i6 - 1, iMethod_46427 + 2, 860473599);
        class_332Var.fill(iMethod_46426 + 1, i7 - 2, i6 - 1, i7 - 1, 1711737617);
        if (this.bw || this.bx) {
            class_332Var.fill(iMethod_46426, iMethod_46427, iMethod_46426 + 2, i7, i4);
        }
        MinecraftClient class_310VarMethod_1551 = MinecraftClient.getInstance();
        int i8 = this.height;
        Objects.requireNonNull(class_310VarMethod_1551.textRenderer);
        class_332Var.drawCenteredTextWithShadow(class_310VarMethod_1551.textRenderer, getMessage(), iMethod_46426 + (this.width / 2), iMethod_46427 + ((i8 - 9) / 2) + 1, i5);
    }
}
