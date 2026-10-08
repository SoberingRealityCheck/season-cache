package com.seasoncache.network.payload;

import com.seasoncache.SeasonCacheMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ChunkStatesPayload(Identifier dimensionId, int epoch, long[] packedChunkStates) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ChunkStatesPayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(SeasonCacheMod.MOD_ID, "chunk_states"));
    public static final StreamCodec<FriendlyByteBuf, ChunkStatesPayload> CODEC = CustomPacketPayload.codec(ChunkStatesPayload::write, ChunkStatesPayload::new);

    public ChunkStatesPayload(FriendlyByteBuf buf) {
        this(buf.readIdentifier(), buf.readVarInt(), readPackedChunkStates(buf));
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeIdentifier(this.dimensionId);
        buf.writeVarInt(this.epoch);
        buf.writeVarInt(this.packedChunkStates.length);
        for (long packedChunkState : this.packedChunkStates) {
            buf.writeLong(packedChunkState);
        }
    }

    private static long[] readPackedChunkStates(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        long[] packed = new long[size];
        for (int i = 0; i < size; i++) {
            packed[i] = buf.readLong();
        }
        return packed;
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
