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

public class ShieldBottonAdjustSlot extends ValueBoxTransform {

    @Override
    public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
        Direction angleFace = ShieldBlock.getAngleFace(state);
        Vec3 location = angleFace.getAxis().isHorizontal()
            ? VecHelper.voxelSpace(8, 8, 15.5)
            : VecHelper.voxelSpace(8, 15.5, 8);

        if (angleFace.getAxis().isHorizontal()) {
            float yRot = AngleHelper.horizontalAngle(angleFace);
            location = VecHelper.rotateCentered(location, yRot, Direction.Axis.Y);
        } else {
            double xRot = angleFace == Direction.DOWN ? 180 : 0;
            location = VecHelper.rotateCentered(location, xRot, Direction.Axis.X);
        }
        return location;
    }

    @Override
    public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms) {
        Direction angleFace = ShieldBlock.getAngleFace(state);
        float yRot = angleFace.getAxis().isVertical() ? 0 : AngleHelper.horizontalAngle(angleFace) + 180;
        float xRot = angleFace == Direction.UP ? 90 : angleFace == Direction.DOWN ? 270 : 0;
        dev.engine_room.flywheel.lib.transform.TransformStack.of(ms)
            .rotateYDegrees(yRot)
            .rotateXDegrees(xRot);
    }

    @Override
    public float getScale() {
        return 0.5f;
    }
}
