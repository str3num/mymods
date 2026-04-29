package com.guoqiang.clockworkblock;

import com.guoqiang.clockworkblock.content.ClockworkBlockEntity;
import com.simibubi.create.api.stress.BlockStressValues;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod(ClockworkBlockMod.MOD_ID)
public class ClockworkBlockMod {
    public static final String MOD_ID = "clockworkblock";

    public ClockworkBlockMod(IEventBus modEventBus) {
        ClockworkBlocks.register(modEventBus);
        ClockworkBlockEntityTypes.register(modEventBus);
        ClockworkDataComponents.register(modEventBus);
        ClockworkCreativeModeTabs.register(modEventBus);
        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> BlockStressValues.RPM.register(
            ClockworkBlocks.CLOCKWORK_BLOCK.get(),
            new BlockStressValues.GeneratedRpm(ClockworkBlockEntity.DEFAULT_OUTPUT_SPEED, true)
        ));
    }
}
