package com.seasoncache.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.seasoncache.SeasonCacheBlocks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Weather snow becomes seasonal snow.
 *
 * tickPrecipitation reads Blocks.SNOW twice. Ordinal 0 is the layer-stacking check
 * (state.is(SNOW)). Ordinal 1 is the else branch that places a fresh layer. We swap
 * only ordinal 1. Vanilla snow that weather meets still stacks as vanilla snow.
 * (With Serene Seasons, shouldSnow only passes for air, so stacking never happens.)
 *
 * Serene Seasons redirects getPrecipitationAt in this method. We don't touch that call.
 */
@Mixin(ServerLevel.class)
public class ServerLevelMixin {
    @ModifyExpressionValue(
            method = "tickPrecipitation",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/world/level/block/Blocks;SNOW:Lnet/minecraft/world/level/block/Block;",
                    ordinal = 1))
    private Block seasoncache$seasonalSnow(Block vanillaSnow) {
        return SeasonCacheBlocks.SEASONAL_SNOW;
    }
}
