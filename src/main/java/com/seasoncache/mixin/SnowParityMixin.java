package com.seasoncache.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.seasoncache.SeasonCacheBlocks;
import net.minecraft.world.level.block.SpreadingSnowyBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Grass and mycelium treat a 1 layer snow block above as "light gets through, stay alive".
 * That check is `is(Blocks.SNOW)`, so seasonal snow would miss it. Widen it.
 */
@Mixin(SpreadingSnowyBlock.class)
public class SnowParityMixin {
    @WrapOperation(
            method = "canStayAlive",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;is(Ljava/lang/Object;)Z"))
    private static boolean seasoncache$seasonalCountsAsSnow(
            BlockState above, Object block, Operation<Boolean> original) {
        return original.call(above, block) || above.is(SeasonCacheBlocks.SEASONAL_SNOW);
    }
}
