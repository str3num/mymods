package com.guoqiang.clockworkblock;

import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

public class ClockworkCreativeModeTabs {
    private ClockworkCreativeModeTabs() {
    }

    public static void register(IEventBus eventBus) {
    }

    @EventBusSubscriber(modid = ClockworkBlockMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
    public static class Handler {
        @SubscribeEvent
        public static void onBuildTab(BuildCreativeModeTabContentsEvent event) {
            if (event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS) {
                event.accept(ClockworkBlocks.CLOCKWORK_BLOCK_ITEM.get());
                event.accept(ClockworkBlocks.SHIELD_BLOCK_ITEM.get());
            }
        }
    }
}
