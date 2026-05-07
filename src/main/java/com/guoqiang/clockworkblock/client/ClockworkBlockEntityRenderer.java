package com.guoqiang.clockworkblock.client;

import com.guoqiang.clockworkblock.content.ClockworkBlock;
import com.guoqiang.clockworkblock.content.ClockworkBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;

import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.block.state.BlockState;

public class ClockworkBlockEntityRenderer extends KineticBlockEntityRenderer<ClockworkBlockEntity> {
    public ClockworkBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(ClockworkBlockEntity blockEntity, float partialTicks, PoseStack poseStack,
        MultiBufferSource buffer, int light, int overlay) {
        if (VisualizationManager.supportsVisualization(blockEntity.getLevel())) {
            return;
        }

        BlockState state = blockEntity.getBlockState();
        Direction direction = ClockworkBlock.getActiveShaftFace(state);
        Axis axis = direction.getAxis();
        BlockPos pos = blockEntity.getBlockPos();
        float time = AnimationTickHolder.getRenderTime(blockEntity.getLevel());
        float angle = (time * blockEntity.getSpeed() * 3f / 10) % 360;
        angle += getRotationOffsetForPosition(blockEntity, pos, axis);
        angle = angle / 180f * (float) Math.PI;

        SuperByteBuffer shaft = CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state, direction);
        kineticRotationTransform(shaft, blockEntity, axis, angle, light);
        shaft.renderInto(poseStack, buffer.getBuffer(RenderType.solid()));
    }
}
