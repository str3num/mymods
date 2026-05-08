package com.guoqiang.clockworkblock.content;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.kinetics.fan.AirCurrent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class ShieldBlockMovementBehaviour implements MovementBehaviour {

    @Override
    public void tick(MovementContext context) {
        if (context.world == null || context.world.isClientSide)
            return;

        Vec3 center = getWorldCenter(context);
        if (center == null)
            return;

        BlockState state = context.state;
        if (!state.hasProperty(ShieldBlock.FACING))
            return;

        Direction facing = state.getValue(ShieldBlock.FACING);
        CompoundTag data = context.blockEntityData;

        int phi = Mth.clamp(data.getInt("Phi"), 45, 270);
        int flow;
        if (data.contains("Flow"))
            flow = Mth.clamp(data.getInt("Flow"), 1, 32);
        else
            flow = Mth.clamp(data.getInt("MaxRange"), 1, 32);
        int range;
        if (data.contains("Range"))
            range = Mth.clamp(data.getInt("Range"), 1, 32);
        else
            range = 8;

        @SuppressWarnings("unchecked")
        List<Entity> pushing = (List<Entity>) context.temporaryData;
        if (pushing == null) {
            pushing = new ArrayList<>();
            context.temporaryData = pushing;
        }

        scanAndPush(context.world, center, facing, phi, flow, range, pushing);
    }

    private Vec3 getWorldCenter(MovementContext context) {
        if (context.position != null)
            return context.position.add(0.5, 0.5, 0.5);
        if (context.contraption != null && context.contraption.entity != null)
            return context.contraption.entity.position()
                .add(Vec3.atLowerCornerOf(context.localPos))
                .add(0.5, 0.5, 0.5);
        return null;
    }

    private void scanAndPush(net.minecraft.world.level.Level world, Vec3 center,
                             Direction facing, int phi, int flow, int range,
                             List<Entity> pushing) {
        Vec3 facingVec = Vec3.atLowerCornerOf(facing.getNormal());
        double halfPhiRad = Math.toRadians(phi / 2.0);
        double cosHalfPhi = Math.cos(halfPhiRad);
        int scanRange = range;

        AABB bb = new AABB(center, center).inflate(scanRange);
        for (Entity entity : world.getEntitiesOfClass(Entity.class, bb,
                e -> {
                    Vec3 d = e.position().subtract(center);
                    double dist = d.length();
                    if (dist > scanRange || dist < 0.5
                        || e.isShiftKeyDown()
                        || AirCurrent.isPlayerCreativeFlying(e))
                        return false;
                    return d.normalize().dot(facingVec) >= cosHalfPhi;
                })) {

            if (!canSee(world, entity, center))
                continue;

            if (!pushing.contains(entity))
                pushing.add(entity);
        }

        for (Iterator<Entity> it = pushing.iterator(); it.hasNext(); ) {
            Entity entity = it.next();
            if (!entity.isAlive()) {
                it.remove();
                continue;
            }
            Vec3 diff = entity.position().subtract(center);
            double distance = diff.length();
            if (distance > scanRange || distance < 0.5
                || entity.isShiftKeyDown()
                || AirCurrent.isPlayerCreativeFlying(entity)) {
                it.remove();
                continue;
            }
            float factor;
            float strength;
            if (entity instanceof net.minecraft.world.entity.LivingEntity) {
                factor = 1f / 4f;
                strength = flow / (2f * Math.max((float) distance, 0.01f));
            } else if (entity instanceof ItemEntity) {
                factor = 1f / 128f;
                strength = flow * (float)(Math.cos(distance / range + Math.PI / 2) + 1.0);
            } else {
                factor = 1f / 32f;
                strength = flow * (float)(Math.cos(distance / range + Math.PI / 2) + 1.0);
            }
            Vec3 pushVec = diff.normalize().scale(strength);
            entity.setDeltaMovement(entity.getDeltaMovement().add(pushVec.scale(factor)));
            entity.fallDistance = 0;
            entity.hurtMarked = true;
        }
    }

    private boolean canSee(net.minecraft.world.level.Level world, Entity entity, Vec3 center) {
        ClipContext clipContext = new ClipContext(
            entity.position(),
            center,
            ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE,
            entity
        );
        BlockPos hit = world.clip(clipContext).getBlockPos();
        return hit.equals(BlockPos.containing(center));
    }
}
