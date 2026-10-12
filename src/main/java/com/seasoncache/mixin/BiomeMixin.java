package com.seasoncache.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.seasoncache.SeasonCacheBlocks;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Biome.shouldSnow lets weather add a layer to existing snow via `state.is(Blocks.SNOW)`.
 * Count seasonal snow too, so the vanilla (no Serene Seasons) path stacks it.
 * Serene Seasons cancels this method at HEAD, so with SS this never runs.
 */
@Mixin(Biome.class)
public class BiomeMixin {
    @WrapOperation(
            method = "shouldSnow",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;is(Ljava/lang/Object;)Z"))
    private boolean seasoncache$seasonalCountsAsSnow(
            BlockState state, Object block, Operation<Boolean> original) {
        return original.call(state, block) || state.is(SeasonCacheBlocks.SEASONAL_SNOW);
    }
}
