package com.guoqiang.clockworkblock.client;

import com.guoqiang.clockworkblock.content.ShieldBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;

import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class ShieldBlockFrequencySlot extends ValueBoxTransform.Dual {

    public ShieldBlockFrequencySlot(boolean first) {
        super(first);
    }

    @Override
    public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
        Direction freqFace = ShieldBlock.getFrequencyFace(state);

        // Use negative offset so items render OUTSIDE the full-cube block face
        // (Redstone Link uses positive 3.01 because its thin block is on the other side)
        Vec3 location = freqFace.getAxis().isHorizontal()
            ? VecHelper.voxelSpace(8f, 5.5f, 0.5f)
            : VecHelper.voxelSpace(8f, 0.5f, 5.5f);

        if (isFirst()) {
            location = freqFace.getAxis().isHorizontal()
                ? location.add(0, 5 / 16f, 0)
                : location.add(0, 0, 5 / 16f);
        }

        if (freqFace.getAxis().isHorizontal()) {
            float yRot = AngleHelper.horizontalAngle(freqFace) + 180;
            location = VecHelper.rotateCentered(location, yRot, Direction.Axis.Y);
        } else {
            double rot = freqFace == Direction.DOWN ? 180 : 0;
            location = VecHelper.rotateCentered(location, rot, Direction.Axis.X);
        }
        return location;
    }

    @Override
    public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms) {
        Direction freqFace = ShieldBlock.getFrequencyFace(state);
        float yRot = freqFace.getAxis().isVertical() ? 0 : AngleHelper.horizontalAngle(freqFace) + 180;
        float xRot = freqFace == Direction.UP ? 90 : freqFace == Direction.DOWN ? 270 : 0;
        dev.engine_room.flywheel.lib.transform.TransformStack.of(ms)
            .rotateYDegrees(yRot)
            .rotateXDegrees(xRot);
    }

    @Override
    public float getScale() {
        return 0.4975f;
    }
}
