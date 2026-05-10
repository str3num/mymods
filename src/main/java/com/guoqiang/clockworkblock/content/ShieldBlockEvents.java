package com.guoqiang.clockworkblock.content;

import com.guoqiang.clockworkblock.ClockworkBlockMod;
import com.simibubi.create.content.kinetics.fan.NozzleBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = ClockworkBlockMod.MOD_ID)
public class ShieldBlockEvents {

    private ShieldBlockEvents() {}

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);

        if (!(state.getBlock() instanceof NozzleBlock))
            return;

        if (!(event.getItemStack().getItem() instanceof DyeItem dye))
            return;

        // Nozzle faces the same direction as the shield's FACING
        Direction facing = state.getValue(NozzleBlock.FACING);
        BlockPos behind = pos.relative(facing.getOpposite());
        if (level.getBlockEntity(behind) instanceof ShieldBlockEntity shield) {
            if (!level.isClientSide) {
                shield.setParticleColor(dye.getDyeColor());
            }
            event.setCanceled(true);
        }
    }
}
