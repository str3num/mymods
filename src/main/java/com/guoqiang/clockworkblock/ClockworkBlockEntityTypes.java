package com.guoqiang.clockworkblock;

import com.guoqiang.clockworkblock.content.ClockworkBlockEntity;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ClockworkBlockEntityTypes {
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
        DeferredRegister.create(net.minecraft.core.registries.Registries.BLOCK_ENTITY_TYPE, ClockworkBlockMod.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ClockworkBlockEntity>> CLOCKWORK_BLOCK =
        BLOCK_ENTITY_TYPES.register(
            "clockwork_block",
            () -> BlockEntityType.Builder.of(ClockworkBlockEntity::new, ClockworkBlocks.CLOCKWORK_BLOCK.get()).build(null)
        );

    private ClockworkBlockEntityTypes() {
    }

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITY_TYPES.register(eventBus);
    }
}
