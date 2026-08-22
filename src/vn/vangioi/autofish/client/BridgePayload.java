package vn.vangioi.autofish.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.codec.PacketCodec;

@Environment(EnvType.CLIENT)
public record BridgePayload(String data) implements CustomPayload {
    public static final CustomPayload.Id<BridgePayload> ID = new CustomPayload.Id<>(Identifier.of("vgaf", "bridge"));
    public static final PacketCodec<RegistryByteBuf, BridgePayload> CODEC = PacketCodec.tuple(PacketCodecs.STRING, (v0) -> {
        return v0.data();
    }, BridgePayload::new);

    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
