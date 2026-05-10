package com.guoqiang.clockworkblock.client;

import static net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING;

import com.guoqiang.clockworkblock.content.ShieldBlock;
import com.guoqiang.clockworkblock.content.ShieldBlockEntity;
import com.guoqiang.clockworkblock.content.ShieldLinkBehaviour;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.datafixers.util.Pair;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBox;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxRenderer;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;

import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.outliner.Outliner;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
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

        // Render rotating shaft + fan (only when Flywheel is NOT active)
        if (Math.abs(be.getSpeed()) > 0 && !VisualizationManager.supportsVisualization(be.getLevel())) {
            Direction direction = state.getValue(FACING);
            VertexConsumer vb = buffer.getBuffer(RenderType.cutoutMipped());

            SuperByteBuffer shaftHalf =
                CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state, direction.getOpposite());
            SuperByteBuffer fanInner =
                CachedBuffers.partialFacing(AllPartialModels.ENCASED_FAN_INNER, state, direction.getOpposite());

            float time = AnimationTickHolder.getRenderTime(be.getLevel());
            float speed = be.getSpeed() * 5;
            if (speed > 0)
                speed = Mth.clamp(speed, 80, 64 * 20);
            if (speed < 0)
                speed = Mth.clamp(speed, -64 * 20, -80);
            float angle = (time * speed * 3 / 10f) % 360;
            angle = angle / 180f * (float) Math.PI;

            int maxLight = 0xF000F0;
            KineticBlockEntityRenderer.standardKineticRotationTransform(shaftHalf, be, maxLight).renderInto(ms, vb);
            KineticBlockEntityRenderer.kineticRotationTransform(fanInner, be, direction.getAxis(), angle, maxLight).renderInto(ms, vb);
        }

        // Render frequency items on the red face
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

        // Botton face (blue face) adjust box
        ShieldBottonAdjustSlot bottonTransform = new ShieldBottonAdjustSlot();
        int currentPhi = 45;
        if (mc.level.getBlockEntity(pos) instanceof ShieldBlockEntity be)
            currentPhi = be.getPhi();
        Component labelAdjust = Component.literal(currentPhi + "°");
        boolean hitBotton = bottonTransform.testHit(mc.level, pos, state, localHit);
        AABB bottonBounds = new AABB(Vec3.ZERO, Vec3.ZERO).inflate(0.35);
        ValueBox bottonBox = new ValueBox.TextValueBox(labelAdjust, bottonBounds, pos, state,
            Component.literal(currentPhi + "°"))
            .transform(bottonTransform)
            .passive(!hitBotton)
            .wideOutline();
        Outliner.getInstance()
            .showOutline(Pair.of("botton", pos), bottonBox)
            .highlightFace(blockHit.getDirection());
    }
}
