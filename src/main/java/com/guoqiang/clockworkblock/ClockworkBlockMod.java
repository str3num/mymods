package com.guoqiang.clockworkblock;

import com.guoqiang.clockworkblock.client.ShieldBlockScreen;
import com.guoqiang.clockworkblock.client.ShieldFanVisual;
import com.guoqiang.clockworkblock.content.ClockworkBlockEntity;
import com.simibubi.create.api.stress.BlockStressValues;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@Mod(ClockworkBlockMod.MOD_ID)
public class ClockworkBlockMod {
    public static final String MOD_ID = "clockworkblock";

    public ClockworkBlockMod(IEventBus modEventBus) {
        ClockworkBlocks.register(modEventBus);
        ClockworkBlockEntityTypes.register(modEventBus);
        ClockworkDataComponents.register(modEventBus);
        ClockworkCreativeModeTabs.register(modEventBus);
        ClockworkMenuTypes.register(modEventBus);
        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> BlockStressValues.RPM.register(
            ClockworkBlocks.CLOCKWORK_BLOCK.get(),
            new BlockStressValues.GeneratedRpm(ClockworkBlockEntity.DEFAULT_OUTPUT_SPEED, true)
        ));
    }

    @EventBusSubscriber(modid = MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static class ClientEvents {
        @SubscribeEvent
        public static void registerScreens(RegisterMenuScreensEvent event) {
            event.register(ClockworkMenuTypes.SHIELD_BLOCK.get(), ShieldBlockScreen::new);
        }

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer.builder(
                        ClockworkBlockEntityTypes.SHIELD_BLOCK.get())
                    .factory(ShieldFanVisual::new)
                    .neverSkipVanillaRender()
                    .apply();
            });
        }
    }
}
