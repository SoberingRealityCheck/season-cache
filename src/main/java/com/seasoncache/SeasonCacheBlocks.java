package com.seasoncache;

import com.seasoncache.block.SeasonalSnowBlock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Blocks this mod adds. Needs the mod on both sides (the pack already does). */
public final class SeasonCacheBlocks {
    private SeasonCacheBlocks() {}

    public static final ResourceKey<Block> SEASONAL_SNOW_KEY = ResourceKey.create(
            Registries.BLOCK, Identifier.fromNamespaceAndPath("seasoncache", "seasonal_snow"));

    public static Block SEASONAL_SNOW;

    public static void register() {
        BlockBehaviour.Properties props = BlockBehaviour.Properties.ofFullCopy(Blocks.SNOW).setId(SEASONAL_SNOW_KEY);
        SEASONAL_SNOW = Registry.register(BuiltInRegistries.BLOCK, SEASONAL_SNOW_KEY, new SeasonalSnowBlock(props));
    }
}
