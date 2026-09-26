package com.example.vangioi.mixin;

import com.example.vangioi.util.MoonController;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ChatScreen.class, priority = 1200)
public abstract class MoonEnterMixin {
    @Shadow protected TextFieldWidget chatField;

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void interceptMoonEnter(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (keyCode != GLFW.GLFW_KEY_ENTER && keyCode != GLFW.GLFW_KEY_KP_ENTER) return;

        String command = chatField.getText().trim().toLowerCase(java.util.Locale.ROOT);
        if (command.equals("moon")) {
            MoonController.open();
            cir.setReturnValue(true);
        } else if (command.equals("moonoff")) {
            MoonController.disableAll();
            cir.setReturnValue(true);
        }
    }
}
