package com.guoqiang.clockworkblock;

import java.util.function.Supplier;

import com.guoqiang.clockworkblock.content.ClockworkBlock;
import com.guoqiang.clockworkblock.content.ClockworkBlockItem;
import com.guoqiang.clockworkblock.content.ShieldBlock;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ClockworkBlocks {
    public static final DeferredRegister.Blocks BLOCKS =
        DeferredRegister.createBlocks(ClockworkBlockMod.MOD_ID);
    public static final DeferredRegister.Items ITEMS =
        DeferredRegister.createItems(ClockworkBlockMod.MOD_ID);

    public static final DeferredHolder<Block, ClockworkBlock> CLOCKWORK_BLOCK = BLOCKS.register(
        "clockwork_block",
        () -> new ClockworkBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_ORANGE)
            .strength(3.5F)
            .sound(SoundType.COPPER))
    );

    public static final DeferredHolder<Item, BlockItem> CLOCKWORK_BLOCK_ITEM = ITEMS.register(
        "clockwork_block",
        blockItem("clockwork_block", CLOCKWORK_BLOCK, () -> new ClockworkBlockItem(
            CLOCKWORK_BLOCK.get(),
            new Item.Properties().stacksTo(1)
        ))
    );

    public static final DeferredHolder<Block, ShieldBlock> SHIELD_BLOCK = BLOCKS.register(
        "shield_block",
        () -> new ShieldBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_LIGHT_BLUE)
            .strength(3.5F)
            .sound(SoundType.METAL))
    );

    public static final DeferredHolder<Item, BlockItem> SHIELD_BLOCK_ITEM = ITEMS.register(
        "shield_block",
        () -> new BlockItem(SHIELD_BLOCK.get(), new Item.Properties())
    );

    private ClockworkBlocks() {
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
        ITEMS.register(eventBus);
    }

    private static <T extends BlockItem> Supplier<T> blockItem(String ignoredName, DeferredHolder<Block, ? extends Block> block, Supplier<T> factory) {
        return factory;
    }
}
