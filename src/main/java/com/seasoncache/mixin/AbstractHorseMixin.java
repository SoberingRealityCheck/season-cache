package com.seasoncache.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.seasoncache.SeasonCacheBlocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Horses step with the snow sound when snow sits above the block. Count seasonal snow. */
@Mixin(net.minecraft.world.entity.animal.equine.AbstractHorse.class)
public class AbstractHorseMixin {
    @WrapOperation(
            method = "playStepSound",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;is(Ljava/lang/Object;)Z"))
    private boolean seasoncache$seasonalCountsAsSnow(
            BlockState state, Object block, Operation<Boolean> original) {
        return original.call(state, block) || state.is(SeasonCacheBlocks.SEASONAL_SNOW);
    }
}
