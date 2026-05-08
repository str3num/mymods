package com.guoqiang.clockworkblock.client;

import com.guoqiang.clockworkblock.content.ShieldBlock;
import com.guoqiang.clockworkblock.content.ShieldBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBox;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxRenderer;

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

    private final ShieldBlockFrequencySlot firstSlot = new ShieldBlockFrequencySlot(true);
    private final ShieldBlockFrequencySlot secondSlot = new ShieldBlockFrequencySlot(false);

    public ShieldBlockRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ShieldBlockEntity be, float partialTick, PoseStack ms,
                       MultiBufferSource buffer, int light, int overlay) {
        if (be.getLevel() == null)
            return;

        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof ShieldBlock))
            return;

        BlockPos pos = be.getBlockPos();

        renderSlot(be.getFirstFrequency(), firstSlot, be, pos, state, ms, buffer, light, overlay);
        renderSlot(be.getSecondFrequency(), secondSlot, be, pos, state, ms, buffer, light, overlay);
    }

    private void renderSlot(Frequency freq, ShieldBlockFrequencySlot slot, ShieldBlockEntity be,
                            BlockPos pos, BlockState state, PoseStack ms,
                            MultiBufferSource buffer, int light, int overlay) {
        ItemStack stack = freq.getStack();
        if (stack.isEmpty())
            return;

        ms.pushPose();
        slot.transform(be.getLevel(), pos, state, ms);
        ValueBoxRenderer.renderItemIntoValueBox(stack, ms, buffer, light, overlay);
        ms.popPose();
    }

    /** Called every client tick to show interactive slot outlines when the player looks at the block. */
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

        if (!(mc.level.getBlockEntity(pos) instanceof ShieldBlockEntity be))
            return;

        Vec3 localHit = blockHit.getLocation().subtract(Vec3.atLowerCornerOf(pos));
        ShieldBlockFrequencySlot slot1 = new ShieldBlockFrequencySlot(true);
        ShieldBlockFrequencySlot slot2 = new ShieldBlockFrequencySlot(false);

        for (int i = 0; i < 2; i++) {
            boolean isFirst = (i == 0);
            ShieldBlockFrequencySlot slot = isFirst ? slot1 : slot2;
            Frequency freq = isFirst ? be.getFirstFrequency() : be.getSecondFrequency();

            boolean hitSlot = slot.testHit(mc.level, pos, state, localHit);

            Component label = Component.translatable(isFirst
                ? "block.clockworkblock.shield_block.frequency_first"
                : "block.clockworkblock.shield_block.frequency_second");
            AABB bounds = new AABB(Vec3.ZERO, Vec3.ZERO).inflate(0.25);
            ValueBox box = new ValueBox(label, bounds, pos).transform(slot).passive(!hitSlot);
            if (!freq.getStack().isEmpty())
                box.wideOutline();

            Outliner.getInstance().showOutline(box, box).highlightFace(blockHit.getDirection());
        }
    }
}
