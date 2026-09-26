package com.example.vangioi.mixin;

import com.example.vangioi.util.MoonConfig;
import meteordevelopment.meteorclient.MeteorClient;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;

@Mixin(MeteorClient.class)
public class MeteorFolderMixin {
    @Shadow @Final @Mutable public static File FOLDER;

    @Inject(method = "onInitializeClient", at = @At("HEAD"))
    private void moveMeteorDataToDocuments(CallbackInfo ci) {
        FOLDER = MoonConfig.meteorDirectory().toFile();
    }
}
