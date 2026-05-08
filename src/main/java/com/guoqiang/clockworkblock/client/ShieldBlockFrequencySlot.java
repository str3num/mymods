package com.guoqiang.clockworkblock.client;

import com.guoqiang.clockworkblock.content.ShieldBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;

import dev.engine_room.flywheel.lib.transform.TransformStack;
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
        Vec3 location = VecHelper.voxelSpace(8f, 3.01f, 5.5f);

        if (freqFace.getAxis().isHorizontal()) {
            location = VecHelper.voxelSpace(8f, 5.5f, 3.01f);
            if (isFirst())
                location = location.add(0, 5 / 16f, 0);
            return rotateHorizontally(freqFace, location);
        }

        if (isFirst())
            location = location.add(0, 0, 5 / 16f);
        double rot = freqFace == Direction.DOWN ? 180 : 0;
        location = VecHelper.rotateCentered(location, rot, Direction.Axis.X);
        return location;
    }

    @Override
    public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms) {
        Direction freqFace = ShieldBlock.getFrequencyFace(state);
        float yRot = freqFace.getAxis().isVertical() ? 0 : AngleHelper.horizontalAngle(freqFace) + 180;
        float xRot = freqFace == Direction.UP ? 90 : freqFace == Direction.DOWN ? 270 : 0;
        TransformStack.of(ms).rotateYDegrees(yRot).rotateXDegrees(xRot);
    }

    @Override
    public float getScale() {
        return 0.4975f;
    }

    /** Rotate around Y axis to match the given face. Mirrors the base class logic but for arbitrary face. */
    private Vec3 rotateHorizontally(Direction face, Vec3 location) {
        float yRot = AngleHelper.horizontalAngle(face);
        return VecHelper.rotateCentered(location, yRot, Direction.Axis.Y);
    }
}
