package com.guoqiang.clockworkblock;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ClockworkCreativeModeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ClockworkBlockMod.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CLOCKWORK_TAB =
        CREATIVE_TABS.register("clockworkblock", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.clockworkblock"))
            .icon(() -> ClockworkBlocks.CLOCKWORK_BLOCK_ITEM.get().getDefaultInstance())
            .displayItems((params, output) -> {
                // Leave the first 10 slots for vanilla items if added later
                output.accept(ClockworkBlocks.CLOCKWORK_BLOCK_ITEM.get());
                output.accept(ClockworkBlocks.SHIELD_BLOCK_ITEM.get());
            })
            .build());

    private ClockworkCreativeModeTabs() {
    }

    public static void register(IEventBus eventBus) {
        CREATIVE_TABS.register(eventBus);
    }
}
