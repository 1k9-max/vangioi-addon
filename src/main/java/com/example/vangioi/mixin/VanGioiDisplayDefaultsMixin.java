package com.example.vangioi.mixin;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.config.Config;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MeteorClient.class)
public class VanGioiDisplayDefaultsMixin {
    @Inject(method = "onInitializeClient", at = @At("TAIL"))
    private void disableTitleCreditsAndSplashes(CallbackInfo ci) {
        Config.get().titleScreenCredits.set(false);
        Config.get().titleScreenSplashes.set(false);
    }
}
