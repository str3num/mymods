package com.guoqiang.clockworkblock.content;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.guoqiang.clockworkblock.ClockworkBlockEntityTypes;
import com.simibubi.create.content.kinetics.fan.AirCurrent;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class ShieldBlockEntity extends SmartBlockEntity implements BlockEntitySubLevelActor {

    private int phi = 90;
    private int minRange = 0;
    private int maxRange = 8;
    private final List<Entity> pushingEntities = new ArrayList<>();
    private boolean onSubLevel;

    public ShieldBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public ShieldBlockEntity(BlockPos pos, BlockState state) {
        this(ClockworkBlockEntityTypes.SHIELD_BLOCK.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    @Override
    public void tick() {
        super.tick();

        if (level == null)
            return;

        // Detect if we're on a Sable sub-level on first tick
        if (!onSubLevel) {
            onSubLevel = Sable.HELPER.getContaining(this) != null;
        }

        // When on a Sable sub-level, the block entity exists in the main world
        // at the plot position (not where the contraption actually is).
        // Entity scanning is handled by sable$tick() instead.
        if (onSubLevel) {
            tickClientOnly(VecHelper.getCenterOf(worldPosition),
                getBlockState().getValue(ShieldBlock.FACING));
            return;
        }

        Direction facing = getBlockState().getValue(ShieldBlock.FACING);
        Vec3 center = VecHelper.getCenterOf(worldPosition);

        if (level.isClientSide) {
            tickClientOnly(center, facing);
        } else {
            tickServer(level, center, facing);
        }
    }

    // Called once per game tick when this block entity is on a Sable sub-level.
    @Override
    public void sable$tick(ServerSubLevel subLevel) {
        onSubLevel = true;

        Level parentLevel = subLevel.getLevel();
        if (parentLevel == null || parentLevel.isClientSide)
            return;

        BlockState state = getBlockState();
        if (!state.hasProperty(ShieldBlock.FACING))
            return;

        Direction facing = state.getValue(ShieldBlock.FACING);
        Vec3 localCenter = VecHelper.getCenterOf(worldPosition);
        Vec3 worldCenter = Sable.HELPER.projectOutOfSubLevel(level, localCenter);

        tickServer(parentLevel, worldCenter, facing);
    }

    private void tickClientOnly(Vec3 center, Direction facing) {
        if (maxRange <= 0)
            return;

        if (level.random.nextInt(10) == 0) {
            Vec3 dir = Vec3.atLowerCornerOf(facing.getNormal());
            Vec3 start = VecHelper.offsetRandomly(center, level.random, maxRange * 0.4f);
            start = start.add(dir.scale(level.random.nextFloat() * maxRange * 0.6f));
            Vec3 motion = center.subtract(start).normalize().scale(0.3f);
            level.addParticle(ParticleTypes.POOF, start.x, start.y, start.z, motion.x, motion.y, motion.z);
        }

        for (Iterator<Entity> it = pushingEntities.iterator(); it.hasNext(); ) {
            Entity entity = it.next();
            if (!entity.isAlive()) {
                it.remove();
                continue;
            }
            Vec3 diff = entity.position().subtract(center);
            double distance = diff.length();
            if (distance > maxRange || distance < minRange
                || entity.isShiftKeyDown()
                || AirCurrent.isPlayerCreativeFlying(entity)) {
                it.remove();
                continue;
            }
            applyPushForce(entity, diff, (float) distance);
        }
    }

    private void tickServer(Level scanLevel, Vec3 center, Direction facing) {
        scanForEntities(scanLevel, center, facing);

        for (Iterator<Entity> it = pushingEntities.iterator(); it.hasNext(); ) {
            Entity entity = it.next();
            if (!entity.isAlive()) {
                it.remove();
                continue;
            }
            Vec3 diff = entity.position().subtract(center);
            double distance = diff.length();
            if (distance > maxRange || distance < minRange
                || entity.isShiftKeyDown()
                || AirCurrent.isPlayerCreativeFlying(entity)) {
                it.remove();
                continue;
            }
            applyPushForce(entity, diff, (float) distance);
        }
    }

    private void scanForEntities(Level scanLevel, Vec3 center, Direction facing) {
        Vec3 facingVec = Vec3.atLowerCornerOf(facing.getNormal());
        double halfPhiRad = Math.toRadians(phi / 2.0);
        double cosHalfPhi = Math.cos(halfPhiRad);
        // Skip line-of-sight check when on sub-level: the block doesn't exist
        // in parent world coordinates, so raycasts would always miss.
        boolean checkLineOfSight = !onSubLevel;

        AABB bb = new AABB(center, center).inflate(maxRange);
        for (Entity entity : scanLevel.getEntitiesOfClass(Entity.class, bb,
                e -> {
                    Vec3 d = e.position().subtract(center);
                    double dist = d.length();
                    if (dist > maxRange || dist < minRange
                        || e.isShiftKeyDown()
                        || AirCurrent.isPlayerCreativeFlying(e))
                        return false;
                    return d.normalize().dot(facingVec) >= cosHalfPhi;
                })) {

            if (checkLineOfSight && !canSee(scanLevel, entity, center)) {
                pushingEntities.remove(entity);
                continue;
            }

            if (!pushingEntities.contains(entity)) {
                pushingEntities.add(entity);
            }
        }

        for (Iterator<Entity> it = pushingEntities.iterator(); it.hasNext(); ) {
            Entity entity = it.next();
            if (!entity.isAlive()) {
                it.remove();
            }
        }
    }

    private void applyPushForce(Entity entity, Vec3 diff, float distance) {
        float factor = (entity instanceof ItemEntity) ? 1f / 128f : 1f / 32f;
        Vec3 pushVec = diff.normalize().scale(Math.max(maxRange - distance, 0.01));
        entity.setDeltaMovement(entity.getDeltaMovement().add(pushVec.scale(factor)));
        entity.fallDistance = 0;
        entity.hurtMarked = true;
    }

    private boolean canSee(Level scanLevel, Entity entity, Vec3 center) {
        ClipContext context = new ClipContext(
            entity.position(),
            center,
            ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE,
            entity
        );
        BlockPos hit = scanLevel.clip(context).getBlockPos();
        return hit.equals(BlockPos.containing(center));
    }

    public void adjustPhi(int delta) {
        phi = Mth.clamp(phi + delta, 45, 145);
        setChanged();
        sendData();
    }

    public void adjustMaxRange(int delta) {
        maxRange = Mth.clamp(maxRange + delta, 1, 32);
        setChanged();
        sendData();
    }

    public int getPhi() {
        return phi;
    }

    public int getMinRange() {
        return minRange;
    }

    public int getMaxRange() {
        return maxRange;
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        compound.putInt("Phi", phi);
        compound.putInt("MinRange", minRange);
        compound.putInt("MaxRange", maxRange);
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        phi = Mth.clamp(compound.getInt("Phi"), 45, 145);
        minRange = compound.getInt("MinRange");
        maxRange = Mth.clamp(compound.getInt("MaxRange"), 1, 32);
    }
}
