package com.seasoncache.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Snow placed by weather or by the sweep. Same as vanilla snow in every way
 * except its id, so the season change can find it and remove only it.
 * No item. Pick-block and drops give vanilla snow items.
 *
 * Hack: the codec is vanilla's. It builds a vanilla SnowLayerBlock. Nothing
 * reads it for gameplay, only data generators.
 */
public class SeasonalSnowBlock extends SnowLayerBlock {
    public SeasonalSnowBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    /** Vanilla only accepts a full snow block of its own kind below. Accept both kinds. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        if (below.is(Blocks.SNOW) && below.getValue(LAYERS) == 8) return true;
        return super.canSurvive(state, level, pos);
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return new ItemStack(Items.SNOW);
    }
}
