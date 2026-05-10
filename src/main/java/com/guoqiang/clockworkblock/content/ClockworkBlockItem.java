package com.guoqiang.clockworkblock.content;

import java.util.List;

import com.guoqiang.clockworkblock.ClockworkDataComponents;
import com.guoqiang.clockworkblock.ClockworkBlockMod;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

public class ClockworkBlockItem extends BlockItem {
    private static final int BAR_COLOR = 0xD98C2B;

    public ClockworkBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        float fill = getStoredEnergy(stack) / (float) ClockworkBlockEntity.MAX_ENERGY;
        return Math.round(13.0F * Mth.clamp(fill, 0.0F, 1.0F));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return BAR_COLOR;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable(
                "tooltip." + ClockworkBlockMod.MOD_ID + ".energy",
                getStoredEnergy(stack),
                ClockworkBlockEntity.MAX_ENERGY
            )
            .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable(
                "tooltip." + ClockworkBlockMod.MOD_ID + ".output_speed",
                getOutputSpeed(stack)
            )
            .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip." + ClockworkBlockMod.MOD_ID + ".stress_input")
            .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip." + ClockworkBlockMod.MOD_ID + ".stress_output")
            .withStyle(ChatFormatting.GRAY));
    }

    public static int getStoredEnergy(ItemStack stack) {
        return stack.getOrDefault(ClockworkDataComponents.STORED_ENERGY.get(), 0);
    }

    public static int getOutputSpeed(ItemStack stack) {
        return stack.getOrDefault(ClockworkDataComponents.OUTPUT_SPEED.get(), ClockworkBlockEntity.DEFAULT_OUTPUT_SPEED);
    }

    public static int getPower(ItemStack stack) {
        return stack.getOrDefault(
            ClockworkDataComponents.POWER.get(),
            stack.getOrDefault(ClockworkDataComponents.RESISTANCE.get(), ClockworkBlockEntity.DEFAULT_POWER)
        );
    }
}
