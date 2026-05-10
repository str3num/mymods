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
        tooltip.add(Component.translatable("tooltip." + ClockworkBlockMod.MOD_ID + ".shield_stress")
            .withStyle(ChatFormatting.GRAY));
    }
}
