package com.example.autocropfarmer.mixin;

import com.example.autocropfarmer.util.ActionBarBridge;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Bat text hien thi tren ActionBar (thanh chu nho phia tren hotbar) - Meteor Client KHONG co san
 * event cho cai nay (chi co ReceiveMessageEvent cho chat thuong), nen can 1 mixin nho de doc duoc.
 * Dung cho module AutoFish (doc thanh % minigame cau ca hien tren ActionBar).
 */
@Mixin(InGameHud.class)
public class InGameHudMixin {

    @Inject(method = "setOverlayMessage", at = @At("HEAD"))
    private void onSetOverlayMessage(Text message, boolean tinted, CallbackInfo ci) {
        ActionBarBridge.onActionBar(message);
    }
}
