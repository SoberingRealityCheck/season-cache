package com.seasoncache.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.seasoncache.SeasonCacheBlocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A fox that pounces into a snow block faceplants. Seasonal snow must do the same. */
@Mixin(targets = "net.minecraft.world.entity.animal.fox.Fox$FoxPounceGoal")
public class FoxPounceGoalMixin {
    @WrapOperation(
            method = "tick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;is(Ljava/lang/Object;)Z"))
    private boolean seasoncache$seasonalCountsAsSnow(
            BlockState state, Object block, Operation<Boolean> original) {
        return original.call(state, block) || state.is(SeasonCacheBlocks.SEASONAL_SNOW);
    }
}
