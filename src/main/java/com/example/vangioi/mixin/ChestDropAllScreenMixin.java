package com.example.vangioi.mixin;

import com.example.vangioi.modules.ChestDropAllButton;
import com.example.vangioi.modules.GuiDumperModule;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.ScreenHandlerProvider;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Chen them nut "Drop All" vao moi man hinh container (HandledScreen), tuong tu cach
 * Meteor Client goc chen nut "Steal"/"Dump" cho module InventoryTweaks.
 *
 * Day la mixin RIENG cua addon (khong sua file mixin core cua Meteor Client) - nho vay khong
 * xung dot voi HandledScreenMixin goc du ca hai deu inject vao method "init".
 *
 * QUAN TRONG: phai them "ChestDropAllScreenMixin" vao danh sach "client" trong file
 * <ten-addon>.mixins.json cua ban thi Fabric moi ap dung mixin nay.
 */
@Mixin(HandledScreen.class)
public abstract class ChestDropAllScreenMixin<T extends ScreenHandler> extends Screen implements ScreenHandlerProvider<T> {

    @Shadow
    protected int x;

    @Shadow
    protected int y;

    @Shadow
    protected int backgroundHeight;

    @Shadow
    public abstract T getScreenHandler();

    protected ChestDropAllScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void vangioi$onInit(CallbackInfo info) {
        ScreenHandler handler = getScreenHandler();

        ChestDropAllButton dropAllModule = Modules.get().get(ChestDropAllButton.class);
        if (dropAllModule != null && dropAllModule.canShowButton(handler)) {
            addDrawableChild(
                new ButtonWidget.Builder(Text.literal("Drop All"), button -> dropAllModule.dropAll(handler))
                    .position(x, y + backgroundHeight + 4)
                    .size(60, 20)
                    .build()
            );
        }

        GuiDumperModule dumperModule = Modules.get().get(GuiDumperModule.class);
        if (dumperModule != null && dumperModule.canShowButton(handler)) {
            addDrawableChild(
                new ButtonWidget.Builder(Text.literal("Dump GUI"), button -> dumperModule.dumpCurrentGui(handler))
                    .position(x + 66, y + backgroundHeight + 4)
                    .size(72, 20)
                    .build()
            );
        }
    }
}
