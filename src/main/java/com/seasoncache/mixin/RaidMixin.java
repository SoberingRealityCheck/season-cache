package com.seasoncache.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.seasoncache.SeasonCacheBlocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Raiders may spawn on snow. Count seasonal snow as snow. */
@Mixin(net.minecraft.world.entity.raid.Raid.class)
public class RaidMixin {
    @WrapOperation(
            method = "findRandomSpawnPos",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;is(Ljava/lang/Object;)Z"))
    private boolean seasoncache$seasonalCountsAsSnow(
            BlockState state, Object block, Operation<Boolean> original) {
        return original.call(state, block) || state.is(SeasonCacheBlocks.SEASONAL_SNOW);
    }
}
