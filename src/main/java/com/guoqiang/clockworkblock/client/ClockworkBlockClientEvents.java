package com.guoqiang.clockworkblock.client;

import com.guoqiang.clockworkblock.ClockworkBlockMod;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = ClockworkBlockMod.MOD_ID, value = Dist.CLIENT)
public class ClockworkBlockClientEvents {

    private ClockworkBlockClientEvents() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ShieldBlockRenderer.tick();
    }
}
