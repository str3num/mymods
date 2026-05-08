package com.guoqiang.clockworkblock.client;

import com.guoqiang.clockworkblock.content.ShieldBlock;
import com.guoqiang.clockworkblock.content.ShieldBlockEntity;
import com.guoqiang.clockworkblock.content.ShieldLinkBehaviour;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.datafixers.util.Pair;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBox;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxRenderer;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;

import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class ShieldBlockRenderer implements BlockEntityRenderer<ShieldBlockEntity> {

    public ShieldBlockRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ShieldBlockEntity be, float partialTick, PoseStack ms,
                       MultiBufferSource buffer, int light, int overlay) {
        if (be == null || be.isRemoved() || be.getLevel() == null)
            return;

        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof ShieldBlock))
            return;

        ShieldLinkBehaviour link = be.getLink();
        if (link == null)
            return;

        for (boolean first : Iterate.trueAndFalse) {
            ValueBoxTransform transform = first ? link.firstSlot : link.secondSlot;
            ItemStack stack = first ? link.frequencyFirst.getStack() : link.frequencyLast.getStack();

            ms.pushPose();
            transform.transform(be.getLevel(), be.getBlockPos(), state, ms);
            ValueBoxRenderer.renderItemIntoValueBox(stack, ms, buffer, 0xF000F0, overlay);
            ms.popPose();
        }
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null)
            return;

        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult blockHit))
            return;

        BlockPos pos = blockHit.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);
        if (!(state.getBlock() instanceof ShieldBlock))
            return;

        ShieldLinkBehaviour link = BlockEntityBehaviour.get(mc.level, pos, ShieldLinkBehaviour.TYPE);
        if (link == null)
            return;

        Component labelFirst = Component.translatable(
            "block.clockworkblock.shield_block.frequency_first");
        Component labelSecond = Component.translatable(
            "block.clockworkblock.shield_block.frequency_second");

        Vec3 localHit = blockHit.getLocation().subtract(Vec3.atLowerCornerOf(pos));

        for (boolean first : Iterate.trueAndFalse) {
            ValueBoxTransform transform = first ? link.firstSlot : link.secondSlot;
            Frequency freq = first ? link.frequencyFirst : link.frequencyLast;

            boolean hitSlot = transform.testHit(mc.level, pos, state, localHit);

            Component label = first ? labelFirst : labelSecond;
            AABB bounds = new AABB(Vec3.ZERO, Vec3.ZERO).inflate(0.25);
            ValueBox box = new ValueBox(label, bounds, pos)
                .transform(transform)
                .passive(!hitSlot);
            if (!freq.getStack().isEmpty())
                box.wideOutline();

            Outliner.getInstance()
                .showOutline(Pair.of(first, pos), box)
                .highlightFace(blockHit.getDirection());
        }
    }
}
