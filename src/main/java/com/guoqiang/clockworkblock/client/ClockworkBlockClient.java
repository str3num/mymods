package com.guoqiang.clockworkblock.client;

import com.guoqiang.clockworkblock.ClockworkBlockEntityTypes;
import com.guoqiang.clockworkblock.ClockworkBlockMod;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = ClockworkBlockMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClockworkBlockClient {

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ClockworkBlockEntityTypes.CLOCKWORK_BLOCK.get(), ClockworkBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(ClockworkBlockEntityTypes.SHIELD_BLOCK.get(), ShieldBlockRenderer::new);
    }

    @EventBusSubscriber(modid = ClockworkBlockMod.MOD_ID, value = Dist.CLIENT)
    public static class ClientEvents {
        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            ShieldBlockRenderer.tick();
        }
    }
}
