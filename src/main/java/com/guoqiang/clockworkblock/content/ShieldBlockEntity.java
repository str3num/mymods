package com.guoqiang.clockworkblock.content;

import java.util.*;

import com.guoqiang.clockworkblock.ClockworkBlockEntityTypes;
import com.guoqiang.clockworkblock.client.ShieldBlockFrequencySlot;
import com.simibubi.create.content.kinetics.fan.AirCurrent;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import dev.ryanhcode.sable.api.physics.force.QueuedForceGroup;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.tuple.Pair;
import org.joml.Vector3d;

public class ShieldBlockEntity extends KineticBlockEntity implements BlockEntitySubLevelActor {

    /** Network key: groups shields by (firstFrequency, secondFrequency) pair */
    private record FrequencyKey(Frequency first, Frequency second) {}

    /** Per-level, per-frequency shared scan state. Cleared each game tick. */
    private static final Map<Level, Map<FrequencyKey, SharedScanState>> LEVEL_FREQ_STATE = new WeakHashMap<>();

    private static class SharedScanState {
        final long tickStamp;
        final Set<Entity> entities = new HashSet<>();
        final Set<ServerSubLevel> subLevels = new HashSet<>();
        /** Sub-levels that contain a shield with this frequency — excluded from pushing */
        final Set<ServerSubLevel> alliedSubLevels = new HashSet<>();

        SharedScanState(long tickStamp) {
            this.tickStamp = tickStamp;
        }
    }

    private int phi = 90;
    private int minRange = 0;
    private int flow = 8;
    private int range = 8;
    private ShieldLinkBehaviour link;
    private final List<Entity> pushingEntities = new ArrayList<>();
    private boolean onSubLevel;
    private dev.ryanhcode.sable.sublevel.SubLevel cachedSubLevel;
    private float lastStressSpeed;
    private boolean updatingStress;

    private record PendingSubLevelTarget(ServerSubLevel target, Vec3 shieldWorldCenter) {}
    private final List<PendingSubLevelTarget> pendingSubLevelTargets = new ArrayList<>();
    private double pushDampingRatio = 0.3;
    private double pushAccelLimit = 500;

    public ShieldBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public ShieldBlockEntity(BlockPos pos, BlockState state) {
        this(ClockworkBlockEntityTypes.SHIELD_BLOCK.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        Pair<ValueBoxTransform, ValueBoxTransform> slots =
            ValueBoxTransform.Dual.makeSlots(ShieldBlockFrequencySlot::new);
        link = new ShieldLinkBehaviour(this, slots);
        behaviours.add(link);
    }

    @Override
    public void tick() {
        super.tick();

        if (level == null)
            return;

        // Update stress contribution when speed changes
        float curSpeed = Math.abs(getSpeed());
        if (curSpeed > 0 && curSpeed != lastStressSpeed && hasNetwork() && !updatingStress) {
            lastStressSpeed = curSpeed;
            updatingStress = true;
            getOrCreateNetwork().updateStressFor(this, calculateStressApplied());
            updatingStress = false;
        }

        if (!onSubLevel) {
            cachedSubLevel = Sable.HELPER.getContaining(this);
            onSubLevel = cachedSubLevel != null;
        }

        if (onSubLevel && cachedSubLevel != null) {
            Vec3 localCenter = VecHelper.getCenterOf(worldPosition);
            Vec3 worldCenter = Sable.HELPER.projectOutOfSubLevel(level, localCenter);
            tickClientOnly(worldCenter, getSubLevelWorldFacing(cachedSubLevel));
            return;
        }

        Direction facing = getBlockState().getValue(ShieldBlock.FACING);
        Vec3 center = VecHelper.getCenterOf(worldPosition);

        if (level.isClientSide) {
            tickClientOnly(center, Vec3.atLowerCornerOf(facing.getNormal()));
        } else if (Math.abs(getSpeed()) > 0) {
            tickServer(level, center, facing);
        }
    }

    @Override
    public void sable$tick(ServerSubLevel subLevel) {
        onSubLevel = true;

        Level parentLevel = subLevel.getLevel();
        if (parentLevel == null || parentLevel.isClientSide)
            return;

        BlockState state = getBlockState();
        if (!state.hasProperty(ShieldBlock.FACING))
            return;

        if (Math.abs(getSpeed()) <= 0)
            return;

        Vec3 facingVec = getSubLevelWorldFacing(subLevel);
        Vec3 localCenter = VecHelper.getCenterOf(worldPosition);
        Vec3 worldCenter = Sable.HELPER.projectOutOfSubLevel(level, localCenter);

        SharedScanState shared = getSharedState(parentLevel);
        shared.alliedSubLevels.add(subLevel);

        scanAndPush(subLevel, parentLevel, worldCenter, facingVec, shared);
    }

    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        // Push sub-level targets
        for (PendingSubLevelTarget pending : pendingSubLevelTargets) {
            ServerSubLevel target = pending.target;
            if (target.isRemoved())
                continue;
            applySubLevelPushForce(subLevel, target, pending.shieldWorldCenter, timeStep);
        }

        // Ground recoil: raycast in facing direction, offset start outside own block
        Vec3 facingVec = getSubLevelWorldFacing(subLevel);
        Vec3 localCenter = net.createmod.catnip.math.VecHelper.getCenterOf(worldPosition);
        net.minecraft.world.phys.Vec3 worldCenter = Sable.HELPER.projectOutOfSubLevel(
            subLevel.getLevel(), (net.minecraft.world.phys.Vec3) localCenter);
        Vec3 rayStart = worldCenter.add(facingVec.scale(0.6));
        Vec3 rayEnd = rayStart.add(facingVec.scale(range));
        net.minecraft.world.level.ClipContext clipCtx = new net.minecraft.world.level.ClipContext(
            rayStart, rayEnd, net.minecraft.world.level.ClipContext.Block.COLLIDER,
            net.minecraft.world.level.ClipContext.Fluid.NONE,
            net.minecraft.world.phys.shapes.CollisionContext.empty());
        BlockHitResult hit = subLevel.getLevel().clip(clipCtx);
        if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            double dist = rayStart.distanceTo(hit.getLocation());
            if (dist > 0.01 && dist <= range) {
                double strength = flow * (Math.cos(dist / range + Math.PI / 2) + 1.0);
                double baseForce = strength * 200;
                double mass = Math.max(subLevel.getMassTracker().getMass(), 0.001);

                // Damping (oppose velocity in recoil direction)
                if (pushDampingRatio > 0) {
                    Vector3d vel = new Vector3d();
                    handle.getLinearVelocity(vel);
                    double velAlong = -vel.x * facingVec.x - vel.y * facingVec.y - vel.z * facingVec.z;
                    if (baseForce > 1e-8) {
                        double dampCoeff = 2 * pushDampingRatio * Math.sqrt(mass * baseForce);
                        baseForce = Math.max(0, baseForce - dampCoeff * velAlong);
                    }
                }

                // Acceleration limit
                if (baseForce > mass * pushAccelLimit)
                    baseForce = mass * pushAccelLimit;

                // Apply recoil to own sub-level (opposite of facing direction)
                Vector3d recoil = new Vector3d(
                    -facingVec.x * baseForce * timeStep,
                    -facingVec.y * baseForce * timeStep,
                    -facingVec.z * baseForce * timeStep);
                Vector3d localRecoil = subLevel.logicalPose().transformNormalInverse(recoil);
                Vector3d shieldLocalPos = JOMLConversion.toJOML(
                    VecHelper.getCenterOf(worldPosition));
                subLevel.getOrCreateQueuedForceGroup(ForceGroups.MAGNETIC_FORCE.get())
                    .applyAndRecordPointForce(shieldLocalPos, localRecoil);
            }
        }
    }

    private void applySubLevelPushForce(ServerSubLevel selfSubLevel, ServerSubLevel target,
                                         Vec3 shieldWorldCenter, double timeStep) {
        // Target local position (plot-local coords)
        Vector3d targetLocalPos = JOMLConversion.toJOML(target.getPlot().getCenterBlock().getCenter());
        Vector3d targetWorldPos = new Vector3d();
        target.logicalPose().transformPosition(new Vector3d(targetLocalPos), targetWorldPos);
        Vec3 targetCenter = new Vec3(targetWorldPos.x, targetWorldPos.y, targetWorldPos.z);

        Vec3 diff = targetCenter.subtract(shieldWorldCenter);
        double distance = diff.length();
        if (distance > range || distance < 0.01)
            return;

        Vec3 dir = diff.normalize();
        double strength = flow * (Math.cos(distance / range + Math.PI / 2) + 1.0);
        if (strength <= 0)
            return;

        // Base force (world space)
        Vector3d worldForce = new Vector3d(dir.x * strength * 200, dir.y * strength * 200, dir.z * strength * 200);

        // Magnet-style damping
        RigidBodyHandle targetHandle = RigidBodyHandle.of(target);
        if (pushDampingRatio > 0 && targetHandle != null) {
            Vector3d vel = new Vector3d();
            targetHandle.getLinearVelocity(vel);
            double velAlongDir = vel.x * dir.x + vel.y * dir.y + vel.z * dir.z;
            double forceLen = worldForce.length();
            if (forceLen > 1e-8) {
                double mass = Math.max(target.getMassTracker().getMass(), 0.001);
                double dampCoeff = 2 * pushDampingRatio * Math.sqrt(mass * forceLen);
                worldForce.add(dir.x * -dampCoeff * velAlongDir,
                               dir.y * -dampCoeff * velAlongDir,
                               dir.z * -dampCoeff * velAlongDir);
            }
        }

        // Magnet-style acceleration limiting
        double mass = Math.max(target.getMassTracker().getMass(), 0.001);
        double accel = worldForce.length() / mass;
        if (accel > pushAccelLimit) {
            worldForce.mul(pushAccelLimit / accel);
        }

        // Apply to target
        Vector3d localTargetForce = target.logicalPose().transformNormalInverse(new Vector3d(worldForce));
        localTargetForce.mul(timeStep);
        target.getOrCreateQueuedForceGroup(ForceGroups.MAGNETIC_FORCE.get())
            .applyAndRecordPointForce(targetLocalPos, localTargetForce);

        // Apply opposite to shield's own sub-level (recoil)
        if (selfSubLevel != null && !selfSubLevel.isRemoved() && selfSubLevel != target) {
            Vector3d worldSelfForce = new Vector3d(worldForce).negate();
            Vector3d localSelfForce = selfSubLevel.logicalPose().transformNormalInverse(worldSelfForce);
            localSelfForce.mul(timeStep);
            Vector3d shieldLocalPos = JOMLConversion.toJOML(VecHelper.getCenterOf(worldPosition));
            selfSubLevel.getOrCreateQueuedForceGroup(ForceGroups.MAGNETIC_FORCE.get())
                .applyAndRecordPointForce(shieldLocalPos, localSelfForce);
        }
    }

    private SharedScanState getSharedState(Level level) {
        FrequencyKey key = new FrequencyKey(getFirstFrequency(), getSecondFrequency());
        long currentTick = level.getGameTime();
        Map<FrequencyKey, SharedScanState> freqMap =
            LEVEL_FREQ_STATE.computeIfAbsent(level, k -> new HashMap<>());
        SharedScanState state = freqMap.get(key);
        if (state == null || state.tickStamp != currentTick) {
            state = new SharedScanState(currentTick);
            freqMap.put(key, state);
        }
        return state;
    }

    private void scanAndPush(ServerSubLevel ownSubLevel, Level parentLevel, Vec3 center,
                              Vec3 facingVec, SharedScanState shared) {
        pendingSubLevelTargets.clear();
        double halfPhiRad = Math.toRadians(phi / 2.0);
        double cosHalfPhi = Math.cos(halfPhiRad);
        int scanRange = range;

        // 1. Scan regular entities in own cone → add to shared
        AABB bb = new AABB(center, center).inflate(scanRange);
        List<Entity> entities = parentLevel.getEntitiesOfClass(Entity.class, bb,
            e -> {
                Vec3 d = e.position().subtract(center);
                double dist = d.length();
                if (dist > scanRange || dist < minRange
                    || e.isShiftKeyDown()
                    || AirCurrent.isPlayerCreativeFlying(e))
                    return false;
                return d.normalize().dot(facingVec) >= cosHalfPhi;
            });

        for (Entity entity : entities) {
            shared.entities.add(entity);
        }

        // 2. Scan other sub-levels in own cone → add to shared (skip allied)
        if (parentLevel instanceof ServerLevel serverLevel) {
            ServerSubLevelContainer container =
                (ServerSubLevelContainer) SubLevelContainer.getContainer(serverLevel);
            if (container != null) {
                for (ServerSubLevel other : container.getAllSubLevels()) {
                    if (other == ownSubLevel || other.isRemoved())
                        continue;
                    if (shared.alliedSubLevels.contains(other))
                        continue;

                    org.joml.Vector3d jomlPos = new org.joml.Vector3d();
                    other.logicalPose().transformPosition(
                        JOMLConversion.toJOML(other.getPlot().getCenterBlock().getCenter()),
                        jomlPos);
                    Vec3 otherCenter = new Vec3(jomlPos.x, jomlPos.y, jomlPos.z);

                    Vec3 diff = otherCenter.subtract(center);
                    double distance = diff.length();
                    if (distance > scanRange || distance < minRange)
                        continue;
                    if (distance < 0.01)
                        continue;

                    Vec3 dir = diff.normalize();
                    if (dir.dot(facingVec) < cosHalfPhi)
                        continue;

                    shared.subLevels.add(other);
                }
            }
        }

        // 3. Push ALL shared entities from this shield's position
        for (Entity entity : shared.entities) {
            if (!entity.isAlive())
                continue;
            Vec3 diff = entity.position().subtract(center);
            double distance = diff.length();
            if (distance > scanRange || distance < minRange
                || entity.isShiftKeyDown()
                || AirCurrent.isPlayerCreativeFlying(entity))
                continue;
            applyPushForce(entity, diff, (float) distance);
        }

        // 4. Record sub-level targets for physics tick
        for (ServerSubLevel other : shared.subLevels) {
            if (other == ownSubLevel || other.isRemoved())
                continue;
            pendingSubLevelTargets.add(new PendingSubLevelTarget(other, center));
        }

        // 5. Maintain pushingEntities for client-side tracking (own cone only)
        for (Iterator<Entity> it = pushingEntities.iterator(); it.hasNext(); ) {
            if (!it.next().isAlive())
                it.remove();
        }
        for (Entity entity : entities) {
            if (!pushingEntities.contains(entity))
                pushingEntities.add(entity);
        }
    }

    private void tickClientOnly(Vec3 center, Vec3 facingVec) {
        if (flow <= 0)
            return;

        boolean powered = Math.abs(getSpeed()) > 0;
        int scanRange = range;

        if (powered) {
            // Shield boundary particles: scatter points on the spherical cap at distance=range
            if (scanRange > 0) {
                Vec3 ref = facingVec.y() < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
                Vec3 perpA = ref.cross(facingVec).normalize();
                Vec3 perpB = facingVec.cross(perpA);
                double halfPhiRad = Math.toRadians(phi / 2.0);

                for (int i = 0; i < 5; i++) {
                    double alpha = level.random.nextDouble() * halfPhiRad;
                    double beta = level.random.nextDouble() * 2 * Math.PI;
                    Vec3 dir = facingVec.scale(Math.cos(alpha))
                        .add(perpA.scale(Math.sin(alpha) * Math.cos(beta)))
                        .add(perpB.scale(Math.sin(alpha) * Math.sin(beta)));
                    Vec3 pos = center.add(dir.scale(scanRange));
                    level.addParticle(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 0, 0, 0);
                }
            }

            if (level.random.nextInt(10) == 0) {
                Vec3 start = VecHelper.offsetRandomly(center, level.random, scanRange * 0.4f);
                start = start.add(facingVec.scale(level.random.nextFloat() * scanRange * 0.6f));
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
                if (distance > scanRange || distance < minRange
                    || entity.isShiftKeyDown()
                    || AirCurrent.isPlayerCreativeFlying(entity)) {
                    it.remove();
                    continue;
                }
                applyPushForce(entity, diff, (float) distance);
            }
        }
    }

    private void tickServer(Level scanLevel, Vec3 center, Direction facing) {
        SharedScanState shared = getSharedState(scanLevel);

        scanForEntities(scanLevel, center, facing, shared);

        // Push ALL shared entities from this shield's position
        for (Entity entity : shared.entities) {
            if (!entity.isAlive())
                continue;
            Vec3 diff = entity.position().subtract(center);
            double distance = diff.length();
            if (distance > range || distance < minRange
                || entity.isShiftKeyDown()
                || AirCurrent.isPlayerCreativeFlying(entity))
                continue;
            applyPushForce(entity, diff, (float) distance);
        }

        // Push sub-levels from the main world (using RigidBodyHandle directly)
        if (scanLevel instanceof ServerLevel serverLevel && !onSubLevel) {
            ServerSubLevelContainer container =
                (ServerSubLevelContainer) SubLevelContainer.getContainer(serverLevel);
            if (container != null) {
                Vec3 facingVec = Vec3.atLowerCornerOf(facing.getNormal());
                double halfPhiRad = Math.toRadians(phi / 2.0);
                double cosHalfPhi = Math.cos(halfPhiRad);

                for (ServerSubLevel other : container.getAllSubLevels()) {
                    if (other.isRemoved())
                        continue;

                    org.joml.Vector3d otherLocal = JOMLConversion.toJOML(
                        other.getPlot().getCenterBlock().getCenter());
                    org.joml.Vector3d otherWorld = new org.joml.Vector3d();
                    other.logicalPose().transformPosition(new org.joml.Vector3d(otherLocal), otherWorld);
                    Vec3 otherCenter = new Vec3(otherWorld.x, otherWorld.y, otherWorld.z);

                    Vec3 diff = otherCenter.subtract(center);
                    double dist = diff.length();
                    if (dist > range || dist < minRange || dist < 0.01)
                        continue;

                    Vec3 dir = diff.normalize();
                    if (dir.dot(facingVec) < cosHalfPhi)
                        continue;

                    double strength = flow * (Math.cos(dist / range + Math.PI / 2) + 1.0);
                    if (strength <= 0)
                        continue;

                    RigidBodyHandle targetHandle = RigidBodyHandle.of(other);
                    if (targetHandle == null)
                        continue;

                    double mass = Math.max(other.getMassTracker().getMass(), 0.001);
                    double baseForce = strength * 200;

                    // Damping (like sub-level path)
                    if (pushDampingRatio > 0) {
                        org.joml.Vector3d vel = new org.joml.Vector3d();
                        targetHandle.getLinearVelocity(vel);
                        double velAlongDir = vel.x * dir.x + vel.y * dir.y + vel.z * dir.z;
                        if (baseForce > 1e-8) {
                            double dampCoeff = 2 * pushDampingRatio * Math.sqrt(mass * baseForce);
                            double dampForce = dampCoeff * velAlongDir;
                            baseForce = Math.max(0, baseForce - dampForce);
                        }
                    }

                    // Acceleration limit
                    double maxForce = mass * pushAccelLimit;
                    if (baseForce > maxForce)
                        baseForce = maxForce;

                    // Convert to local coordinates (like ejector pattern)
                    org.joml.Vector3d worldImpulse = new org.joml.Vector3d(
                        dir.x * baseForce * 0.05, dir.y * baseForce * 0.05, dir.z * baseForce * 0.05);
                    org.joml.Vector3d localImpulse = other.logicalPose().transformNormalInverse(worldImpulse);
                    Vec3 localPos = new Vec3(otherLocal.x, otherLocal.y, otherLocal.z);
                    Vec3 localForceVec = new Vec3(localImpulse.x, localImpulse.y, localImpulse.z);
                    targetHandle.applyImpulseAtPoint(localPos, localForceVec);
                }
            }
        }

        for (Iterator<Entity> it = pushingEntities.iterator(); it.hasNext(); ) {
            if (!it.next().isAlive())
                it.remove();
        }
    }

    private void scanForEntities(Level scanLevel, Vec3 center, Direction facing, SharedScanState shared) {
        Vec3 facingVec = Vec3.atLowerCornerOf(facing.getNormal());
        double halfPhiRad = Math.toRadians(phi / 2.0);
        double cosHalfPhi = Math.cos(halfPhiRad);
        boolean checkLineOfSight = !onSubLevel;
        int scanRange = range;

        AABB bb = new AABB(center, center).inflate(scanRange);
        for (Entity entity : scanLevel.getEntitiesOfClass(Entity.class, bb,
                e -> {
                    Vec3 d = e.position().subtract(center);
                    double dist = d.length();
                    if (dist > scanRange || dist < minRange
                        || e.isShiftKeyDown()
                        || AirCurrent.isPlayerCreativeFlying(e))
                        return false;
                    return d.normalize().dot(facingVec) >= cosHalfPhi;
                })) {

            if (checkLineOfSight && !canSee(scanLevel, entity, center)) {
                pushingEntities.remove(entity);
                continue;
            }

            if (!pushingEntities.contains(entity))
                pushingEntities.add(entity);
            shared.entities.add(entity);
        }

        for (Iterator<Entity> it = pushingEntities.iterator(); it.hasNext(); ) {
            if (!it.next().isAlive())
                it.remove();
        }
    }

    private Vec3 getSubLevelWorldFacing(dev.ryanhcode.sable.sublevel.SubLevel subLevel) {
        Direction localFacing = getBlockState().getValue(ShieldBlock.FACING);
        if (subLevel == null)
            return Vec3.atLowerCornerOf(localFacing.getNormal());
        Vec3 localVec = Vec3.atLowerCornerOf(localFacing.getNormal());
        org.joml.Vector3d worldVec = subLevel.logicalPose().orientation().transform(
            new org.joml.Vector3d(localVec.x, localVec.y, localVec.z));
        return new Vec3(worldVec.x, worldVec.y, worldVec.z);
    }

    private void applyPushForce(Entity entity, Vec3 diff, float distance) {
        float factor;
        float strength;
        if (entity instanceof net.minecraft.world.entity.LivingEntity) {
            factor = 1f / 4f;
            strength = flow / (2f * Math.max(distance, 0.01f));
        } else if (entity instanceof ItemEntity) {
            factor = 1f / 2f;
            strength = flow * (float)(Math.cos(distance / range + Math.PI / 2) + 1.0);
        } else {
            factor = 1f / 2f;
            strength = flow * (float)(Math.cos(distance / range + Math.PI / 2) + 1.0);
        }
        Vec3 pushVec = diff.normalize().scale(strength);
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

    // ---- Frequency management ----

    public ShieldLinkBehaviour getLink() {
        return link;
    }

    public FrequencyKey getNetworkKey() {
        if (link == null)
            return new FrequencyKey(Frequency.EMPTY, Frequency.EMPTY);
        return new FrequencyKey(link.frequencyFirst, link.frequencyLast);
    }

    public void setFrequency(boolean first, ItemStack stack) {
        if (link != null)
            link.setFrequency(first, stack);
    }

    public Frequency getFirstFrequency() {
        return link != null ? link.frequencyFirst : Frequency.EMPTY;
    }

    public Frequency getSecondFrequency() {
        return link != null ? link.frequencyLast : Frequency.EMPTY;
    }

    // ---- Parameter adjustment ----

    @Override
    public float calculateStressApplied() {
        float speed = Math.abs(getSpeed());
        float load = speed == 0 ? 0 : (float) (Math.pow(range, 3) * flow * 16) / speed;
        this.lastStressApplied = load;
        return load;
    }


    public void setPhi(int phi) {
        this.phi = Mth.clamp(phi, 45, 270);
        setChanged();
        sendData();
    }

    public void setFlow(int flow) {
        this.flow = Mth.clamp(flow, 1, 32);
        setChanged();
        sendData();
        if (level != null && !level.isClientSide && hasNetwork())
            getOrCreateNetwork().updateNetwork();
    }

    public void setRange(int range) {
        this.range = Mth.clamp(range, 1, 32);
        setChanged();
        sendData();
        if (level != null && !level.isClientSide && hasNetwork())
            getOrCreateNetwork().updateNetwork();
    }

    public int getPhi() {
        return phi;
    }

    public int getMinRange() {
        return minRange;
    }

    public int getFlow() {
        return flow;
    }

    public int getRange() {
        return range;
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        compound.putInt("Phi", phi);
        compound.putInt("MinRange", minRange);
        compound.putInt("Flow", flow);
        compound.putInt("Range", range);
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        phi = Mth.clamp(compound.getInt("Phi"), 45, 270);
        minRange = compound.getInt("MinRange");
        if (compound.contains("Flow"))
            flow = Mth.clamp(compound.getInt("Flow"), 1, 32);
        else
            flow = Mth.clamp(compound.getInt("MaxRange"), 1, 32);
        if (compound.contains("Range"))
            range = Mth.clamp(compound.getInt("Range"), 1, 32);
        else
            range = 8;
    }
}
