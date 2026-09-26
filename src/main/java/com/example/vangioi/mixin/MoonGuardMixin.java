package com.example.vangioi.mixin;

import com.example.vangioi.util.MoonConfig;
import net.minecraft.client.MinecraftClient;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MoonGuardMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void enforceMoonOff(CallbackInfo ci) {
        if (!MoonConfig.isDisabled()) return;

        for (Module module : Modules.get().getAll()) {
            if (!module.getClass().getPackageName().startsWith("com.example.vangioi")) continue;
            if (module.isActive()) module.toggle();
        }
    }
}
