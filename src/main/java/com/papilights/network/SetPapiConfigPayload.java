package com.papilights.network;

import com.papilights.PapiLights;
import com.papilights.block.PapiLightBlockEntity;
import com.papilights.menu.PapiConfigMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client to server: apply a new glideslope / spread to an array.
 *
 * <p>Accepted only while the sending player actually has that array's config menu open, and the
 * values are clamped server-side in {@link PapiLightBlockEntity#configureArray}.
 */
public record SetPapiConfigPayload(BlockPos leaderPos, double glideslopeDeg, double spreadDeg)
        implements CustomPacketPayload {

    public static final Type<SetPapiConfigPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PapiLights.MOD_ID, "set_config"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetPapiConfigPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, SetPapiConfigPayload::leaderPos,
                    ByteBufCodecs.DOUBLE, SetPapiConfigPayload::glideslopeDeg,
                    ByteBufCodecs.DOUBLE, SetPapiConfigPayload::spreadDeg,
                    SetPapiConfigPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetPapiConfigPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player().containerMenu instanceof PapiConfigMenu menu)) {
                return;
            }
            if (!menu.getLeaderPos().equals(payload.leaderPos())
                    || !menu.stillValid(context.player())) {
                return;
            }
            PapiLightBlockEntity leader = menu.resolveLeader(context.player());
            if (leader != null) {
                leader.configureArray(payload.glideslopeDeg(), payload.spreadDeg());
            }
        });
    }
}
