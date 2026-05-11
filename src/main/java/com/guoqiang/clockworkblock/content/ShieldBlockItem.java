package com.guoqiang.clockworkblock.content;

import java.util.List;

import com.guoqiang.clockworkblock.ClockworkBlockMod;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

public class ShieldBlockItem extends BlockItem {

    public ShieldBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        if (flag.hasShiftDown()) {
            tooltip.add(Component.translatable("tooltip." + ClockworkBlockMod.MOD_ID + ".shield_cone_title")
                .withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable("tooltip." + ClockworkBlockMod.MOD_ID + ".shield_cone_phi")
                .withStyle(ChatFormatting.WHITE));
            tooltip.add(Component.translatable("tooltip." + ClockworkBlockMod.MOD_ID + ".shield_cone_range")
                .withStyle(ChatFormatting.WHITE));
            tooltip.add(Component.translatable("tooltip." + ClockworkBlockMod.MOD_ID + ".shield_cone_flow")
                .withStyle(ChatFormatting.WHITE));
            tooltip.add(Component.translatable("tooltip." + ClockworkBlockMod.MOD_ID + ".shield_cone_los")
                .withStyle(ChatFormatting.WHITE));
            tooltip.add(Component.translatable("tooltip." + ClockworkBlockMod.MOD_ID + ".shield_freq_title")
                .withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable("tooltip." + ClockworkBlockMod.MOD_ID + ".shield_freq_detail")
                .withStyle(ChatFormatting.WHITE));
            tooltip.add(Component.translatable("tooltip." + ClockworkBlockMod.MOD_ID + ".shield_freq_redstone")
                .withStyle(ChatFormatting.WHITE));
        } else {
            tooltip.add(Component.translatable("tooltip." + ClockworkBlockMod.MOD_ID + ".shield_stress")
                .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip." + ClockworkBlockMod.MOD_ID + ".shield_strength_info")
                .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip." + ClockworkBlockMod.MOD_ID + ".shield_radius_info")
                .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip." + ClockworkBlockMod.MOD_ID + ".shield_nozzle_hint")
                .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal(""));
            tooltip.add(Component.translatable("tooltip." + ClockworkBlockMod.MOD_ID + ".hold_shift")
                .withStyle(ChatFormatting.GOLD));
        }
    }
}
