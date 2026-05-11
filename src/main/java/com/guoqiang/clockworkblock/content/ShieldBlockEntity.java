package com.guoqiang.clockworkblock.content;

import java.util.*;

import com.guoqiang.clockworkblock.ClockworkBlockEntityTypes;
import com.guoqiang.clockworkblock.client.ShieldBlockFrequencySlot;
import com.simibubi.create.content.kinetics.fan.AirCurrent;
import com.simibubi.create.content.kinetics.fan.IAirCurrentSource;
import com.simibubi.create.content.kinetics.fan.NozzleBlockEntity;
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
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.DyeColor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.tuple.Pair;
import org.joml.Vector3d;

public class ShieldBlockEntity extends KineticBlockEntity implements BlockEntitySubLevelActor, IAirCurrentSource {

    /** Network key: groups shields by (firstFrequency, secondFrequency) pair */
    private record FrequencyKey(Frequency first, Frequency second) {}

    /** Per-level, per-frequency shared scan state. Cleared each game tick. */
    private static final Map<Level, Map<FrequencyKey, SharedScanState>> LEVEL_FREQ_STATE = new WeakHashMap<>();

    /** One-tick-delayed sub-level frequency cache. Built this tick, used next tick for allied discovery. */
    private static Map<Level, Map<FrequencyKey, Set<ServerSubLevel>>> ALLY_CACHE = new HashMap<>();
    private static Map<Level, Map<FrequencyKey, Set<ServerSubLevel>>> ALLY_CACHE_NEXT = new HashMap<>();
    private static long allyCacheTick = -1;

    private static class SharedScanState {
        final long tickStamp;
        final Set<Entity> entities = new HashSet<>();
        final Set<ServerSubLevel> subLevels = new HashSet<>();
        /** Sub-levels that contain a shield with this frequency — excluded from pushing */
        final Set<ServerSubLevel> alliedSubLevels = new HashSet<>();

        boolean alliedDiscoveryDone;

        SharedScanState(long tickStamp) {
            this.tickStamp = tickStamp;
        }
    }

    private int phi = 45;
    private int minRange = 0;
    private int flow = 8;
    private int range = 8;
    private ShieldLinkBehaviour link;
    private final List<Entity> pushingEntities = new ArrayList<>();
    private boolean onSubLevel;
    private dev.ryanhcode.sable.sublevel.SubLevel cachedSubLevel;
    private float lastStressSpeed;
    private int lastRedstoneSignal = -1;
    private boolean clientActive;
    private boolean lastPoweredState = false;
    private Vec3 lastSubLevelImpactPos = Vec3.ZERO;
    private boolean hasSubLevelImpact = false;
    private DyeColor particleColor = null;

    private record PendingSubLevelTarget(ServerSubLevel target, Vec3 shieldWorldCenter) {}
    private final List<PendingSubLevelTarget> pendingSubLevelTargets = new ArrayList<>();

    public static class RippleEffect {
        public final Vec3 worldPos;
        public final float startTime;
        public final float maxRadius;
        public final int color;
        public RippleEffect(Vec3 worldPos, float startTime, float maxRadius, int color) {
            this.worldPos = worldPos; this.startTime = startTime; this.maxRadius = maxRadius; this.color = color;
        }
    }
    public final List<RippleEffect> ripples = new ArrayList<>();
    private double pushDampingRatio = 0.3;
    private double pushAccelLimit = 500;

    public ShieldBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public ShieldBlockEntity(BlockPos pos, BlockState state) {
        this(ClockworkBlockEntityTypes.SHIELD_BLOCK.get(), pos, state);
    }

    @Override
    public void initialize() {
        super.initialize();
        if (level != null && !level.isClientSide) {
            sendData();
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (level == null)
            return;

        // Promote previous tick's sub-level cache so tickServer can read it
        if (!level.isClientSide) {
            swapAllyCache(level);
        }

        // Register stress with the network when speed or redstone signal changes
        if (!level.isClientSide && hasNetwork()) {
            float speed = Math.abs(getSpeed());
            int rs = level.getBestNeighborSignal(worldPosition);
            if (speed > 0 && (speed != lastStressSpeed || rs != lastRedstoneSignal)) {
                lastStressSpeed = speed;
                lastRedstoneSignal = rs;
                getOrCreateNetwork().updateStressFor(this, calculateStressApplied());
            }
        }

        if (!onSubLevel) {
            cachedSubLevel = Sable.HELPER.getContaining(this);
            onSubLevel = cachedSubLevel != null;
        }

        if (onSubLevel && cachedSubLevel != null) {
            Vec3 localCenter = getPushCenterLocal();
            Vec3 worldCenter = Sable.HELPER.projectOutOfSubLevel(level, localCenter);
            tickClientOnly(worldCenter, getSubLevelWorldFacing(cachedSubLevel));
            return;
        }

        Direction facing = getBlockState().getValue(ShieldBlock.FACING);
        Vec3 center = getPushCenterLocal();

        if (level.isClientSide) {
            tickClientOnly(center, Vec3.atLowerCornerOf(facing.getNormal()));
        } else {
            boolean hasSignal = hasRedstoneSignal();
            boolean shouldBeActive = Math.abs(getSpeed()) > 0 && hasSignal;
            if (shouldBeActive != clientActive) {
                clientActive = shouldBeActive;
                sendData();
            }
            if (hasSignal != lastPoweredState) {
                lastPoweredState = hasSignal;
                BlockState state = getBlockState();
                if (state.hasProperty(ShieldBlock.POWERED))
                    level.setBlock(worldPosition, state.setValue(ShieldBlock.POWERED, hasSignal), 2);
            }
            if (shouldBeActive) {
                tickServer(level, center, facing);
            }
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

        FrequencyKey myKey = getNetworkKey();

        // Always cache sub-level for allied detection, even when inactive
        cacheForNextTick(parentLevel, myKey, subLevel);

        if (Math.abs(getSpeed()) <= 0)
            return;

        Vec3 facingVec = getSubLevelWorldFacing(subLevel);
        Vec3 localCenter = getPushCenterLocal();
        Vec3 worldCenter = Sable.HELPER.projectOutOfSubLevel(level, localCenter);

        SharedScanState shared = getSharedState(parentLevel);

        // Phase 1: discover ALL allied sub-levels from PREVIOUS tick's cache
        if (!shared.alliedDiscoveryDone) {
            shared.alliedDiscoveryDone = true;
            discoverAlliedSubLevels(parentLevel, myKey, shared);
        }

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
        Vec3 localCenter = getPushCenterLocal();
        Vec3 worldCenter = Sable.HELPER.projectOutOfSubLevel(
            subLevel.getLevel(), localCenter);
        Vec3 rayStart = worldCenter.add(facingVec.scale(0.6));
        Vec3 rayEnd = rayStart.add(facingVec.scale(getEffectiveRange()));
        net.minecraft.world.level.ClipContext clipCtx = new net.minecraft.world.level.ClipContext(
            rayStart, rayEnd, net.minecraft.world.level.ClipContext.Block.COLLIDER,
            net.minecraft.world.level.ClipContext.Fluid.NONE,
            net.minecraft.world.phys.shapes.CollisionContext.empty());
        BlockHitResult hit = subLevel.getLevel().clip(clipCtx);
        if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            double dist = rayStart.distanceTo(hit.getLocation());
            if (dist > 0.01 && dist <= getEffectiveRange()) {
                double strength = getEffectiveFlow() * (Math.cos(dist / getEffectiveRange() + Math.PI / 2) + 1.0);
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
                Vector3d shieldLocalPos = JOMLConversion.toJOML(getPushCenterLocal());
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
        int effRange = getEffectiveRange();
        if (distance > effRange || distance < 0.01)
            return;

        Vec3 dir = diff.normalize();
        double strength = getEffectiveFlow() * (Math.cos(distance / effRange + Math.PI / 2) + 1.0);
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
            Vector3d shieldLocalPos = JOMLConversion.toJOML(getPushCenterLocal());
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

    private void discoverAlliedSubLevels(Level parentLevel, FrequencyKey key, SharedScanState shared) {
        Map<FrequencyKey, Set<ServerSubLevel>> freqMap = ALLY_CACHE.get(parentLevel);
        if (freqMap == null) return;
        Set<ServerSubLevel> allies = freqMap.get(key);
        if (allies != null) {
            shared.alliedSubLevels.addAll(allies);
        }
    }

    private static void swapAllyCache(Level level) {
        long t = level.getGameTime();
        if (t != allyCacheTick) {
            allyCacheTick = t;
            ALLY_CACHE = ALLY_CACHE_NEXT;
            ALLY_CACHE_NEXT = new HashMap<>();
        }
    }

    private static void cacheForNextTick(Level level, FrequencyKey key, ServerSubLevel subLevel) {
        ALLY_CACHE_NEXT
            .computeIfAbsent(level, k -> new HashMap<>())
            .computeIfAbsent(key, k -> new HashSet<>())
            .add(subLevel);
    }

    private void scanAndPush(ServerSubLevel ownSubLevel, Level parentLevel, Vec3 center,
                              Vec3 facingVec, SharedScanState shared) {
        pendingSubLevelTargets.clear();
        double halfPhiRad = Math.toRadians(getEffectivePhi() / 2.0);
        double cosHalfPhi = Math.cos(halfPhiRad);
        int scanRange = getEffectiveRange();

        // 1. Scan regular entities in own cone → add to shared
        AABB bb = new AABB(center, center).inflate(scanRange);
        List<Entity> entities = parentLevel.getEntitiesOfClass(Entity.class, bb,
            e -> {
                Vec3 d = e.position().subtract(center);
                double dist = d.length();
                if (dist > scanRange || dist < minRange
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
                || AirCurrent.isPlayerCreativeFlying(entity))
                continue;
            applyPushForce(entity, diff, (float) distance);
        }

        // 4. Record sub-level targets for physics tick, and track nearest impact for client particles
        Vec3 nearestSubImpact = null;
        double nearestSubDist = Double.MAX_VALUE;
        for (ServerSubLevel other : shared.subLevels) {
            if (other == ownSubLevel || other.isRemoved())
                continue;

            org.joml.Vector3d jomlPos2 = new org.joml.Vector3d();
            other.logicalPose().transformPosition(
                JOMLConversion.toJOML(other.getPlot().getCenterBlock().getCenter()),
                jomlPos2);
            Vec3 otherCenter = new Vec3(jomlPos2.x, jomlPos2.y, jomlPos2.z);
            Vec3 diff = otherCenter.subtract(center);
            double dist = diff.length();
            if (dist < nearestSubDist) {
                nearestSubDist = dist;
                nearestSubImpact = center.add(diff.normalize().scale(scanRange));
            }

            pendingSubLevelTargets.add(new PendingSubLevelTarget(other, center));
        }

        boolean changed = false;
        if (nearestSubImpact != null) {
            if (!hasSubLevelImpact || !lastSubLevelImpactPos.equals(nearestSubImpact)) {
                lastSubLevelImpactPos = nearestSubImpact;
                hasSubLevelImpact = true;
                changed = true;
            }
        } else if (hasSubLevelImpact) {
            hasSubLevelImpact = false;
            changed = true;
        }
        if (changed)
            sendData();

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

    private final java.util.Map<java.util.UUID, Float> prevEntityDist = new java.util.HashMap<>();

    private void tickClientOnly(Vec3 center, Vec3 facingVec) {
        boolean powered = Math.abs(getSpeed()) > 0 && clientActive;
        if (!powered)
            return;

        // Entity boundary crossing detection for ripple effects
        float currentTime = level.getGameTime();
        int rRange = getEffectiveRange();

        // Sub-level impact ripple effects (synced from server)
        if (hasSubLevelImpact) {
            ripples.add(new RippleEffect(lastSubLevelImpactPos, currentTime, rRange * 3.0f / 32f, 0xFFAA55));
            hasSubLevelImpact = false;
        }
        if (rRange > 0) {
            double halfPhiRad = Math.toRadians(getEffectivePhi() / 2.0);
            double cosHalfPhi = Math.cos(halfPhiRad);
            float scanR = Math.max(rRange * 3, 8);
            java.util.Set<java.util.UUID> seenIds = new java.util.HashSet<>();
            for (Entity entity : level.getEntitiesOfClass(Entity.class,
                    new AABB(center, center).inflate(scanR), e -> e.isAlive())) {
                Vec3 diff = entity.position().subtract(center);
                double dist = diff.length();
                java.util.UUID id = entity.getUUID();
                seenIds.add(id);
                if (dist < 0.5) continue;
                Vec3 dir = diff.normalize();
                if (dir.dot(facingVec) < cosHalfPhi) continue;
                if (dist > rRange * 3) continue;

                float normDist = (float)(dist / rRange);
                Float prev = prevEntityDist.get(id);
                if (prev != null) {
                    boolean wasInside = prev < 1.0f;
                    boolean isInside = dist < rRange;
                    if (wasInside != isInside) {
                        Vec3 impact = center.add(dir.scale(rRange));
                        ripples.add(new RippleEffect(impact, currentTime, rRange * 3.0f / 32f, 0xAADDFF));
                    }
                }
                if (dist < rRange * 3)
                    prevEntityDist.put(id, normDist);
            }
            prevEntityDist.keySet().removeIf(id -> !seenIds.contains(id));
        }

        // Per-tick: spawn expanding ring particles with outward velocity
        for (java.util.Iterator<RippleEffect> rit = ripples.iterator(); rit.hasNext(); ) {
            RippleEffect ripple = rit.next();
            float age = currentTime - ripple.startTime;
            if (age > 3.0f) { rit.remove(); continue; }
            if ((int)age % 10 == 0) {
                Vec3 radialDir = ripple.worldPos.subtract(center).normalize();
                Vec3 refUp = Math.abs(radialDir.y) < 0.9f ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
                Vec3 tanA = refUp.cross(radialDir).normalize();
                Vec3 tanB = radialDir.cross(tanA);
                float speed = 12f * rRange / 32f;
                for (int p = 0; p < 30; p++) {
                    double angle = level.random.nextDouble() * 2 * Math.PI;
                    double ox = Math.cos(angle) * (0.35f + (float)(level.random.nextDouble() - 0.5) * 0.1f);
                    double oz = Math.sin(angle) * (0.35f + (float)(level.random.nextDouble() - 0.5) * 0.1f);
                    float px = (float)(ripple.worldPos.x + tanA.x * ox + tanB.x * oz);
                    float py = (float)(ripple.worldPos.y + tanA.y * ox + tanB.y * oz);
                    float pz = (float)(ripple.worldPos.z + tanA.z * ox + tanB.z * oz);
                    float vx = (float)(tanA.x * Math.cos(angle) + tanB.x * Math.sin(angle)) * speed;
                    float vy = (float)(tanA.y * Math.cos(angle) + tanB.y * Math.sin(angle)) * speed;
                    float vz = (float)(tanA.z * Math.cos(angle) + tanB.z * Math.sin(angle)) * speed;
                    addColoredParticle(px, py, pz, vx * 0.05f, vy * 0.05f, vz * 0.05f, true);
                }
            }
        }

        int scanRange = getEffectiveRange();

        // Shield boundary particles: scatter points on the spherical cap at distance=range
        if (scanRange > 0) {
            Vec3 ref = facingVec.y() < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
            Vec3 perpA = ref.cross(facingVec).normalize();
            Vec3 perpB = facingVec.cross(perpA);
            double halfPhiRad = Math.toRadians(getEffectivePhi() / 2.0);
            float capFactor = scanRange * scanRange * (float)(1.0 - Math.cos(halfPhiRad));
            int particleCount = Math.max(1, Math.round(capFactor / 21.85f));

            for (int i = 0; i < particleCount; i++) {
                double alpha = level.random.nextDouble() * halfPhiRad;
                double beta = level.random.nextDouble() * 2 * Math.PI;
                Vec3 dir = facingVec.scale(Math.cos(alpha))
                    .add(perpA.scale(Math.sin(alpha) * Math.cos(beta)))
                    .add(perpB.scale(Math.sin(alpha) * Math.sin(beta)));
                Vec3 pos = center.add(dir.scale(scanRange));
                Vec3 rayStart = center.add(dir.scale(0.6));
                if (canSeePos(level, rayStart, pos))
                    addColoredParticle(pos.x, pos.y, pos.z, 0, 0, 0, true);
            }
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
                || AirCurrent.isPlayerCreativeFlying(entity)) {
                it.remove();
                continue;
            }
            applyPushForce(entity, diff, (float) distance);
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
            if (distance > getEffectiveRange() || distance < minRange
                || AirCurrent.isPlayerCreativeFlying(entity))
                continue;
            applyPushForce(entity, diff, (float) distance);
        }

        // Push sub-levels from the main world (using RigidBodyHandle directly)
        Vec3 nearestSubImpact = null;
        double nearestSubDist = Double.MAX_VALUE;
        if (scanLevel instanceof ServerLevel serverLevel && !onSubLevel) {
            ServerSubLevelContainer container =
                (ServerSubLevelContainer) SubLevelContainer.getContainer(serverLevel);
            if (container != null) {
                Vec3 facingVec = Vec3.atLowerCornerOf(facing.getNormal());
                double halfPhiRad = Math.toRadians(getEffectivePhi() / 2.0);
                double cosHalfPhi = Math.cos(halfPhiRad);

                for (ServerSubLevel other : container.getAllSubLevels()) {
                    if (other.isRemoved())
                        continue;

                    // Skip allied sub-levels (contain a shield with matching frequency)
                    Map<FrequencyKey, Set<ServerSubLevel>> freqMap = ALLY_CACHE.get(scanLevel);
                    if (freqMap != null) {
                        Set<ServerSubLevel> allies = freqMap.get(getNetworkKey());
                        if (allies != null && allies.contains(other))
                            continue;
                    }

                    org.joml.Vector3d otherLocal = JOMLConversion.toJOML(
                        other.getPlot().getCenterBlock().getCenter());
                    org.joml.Vector3d otherWorld = new org.joml.Vector3d();
                    other.logicalPose().transformPosition(new org.joml.Vector3d(otherLocal), otherWorld);
                    Vec3 otherCenter = new Vec3(otherWorld.x, otherWorld.y, otherWorld.z);

                    Vec3 diff = otherCenter.subtract(center);
                    double dist = diff.length();
                    if (dist > getEffectiveRange() || dist < minRange || dist < 0.01)
                        continue;

                    Vec3 dir = diff.normalize();
                    if (dir.dot(facingVec) < cosHalfPhi)
                        continue;

                    // Track nearest sub-level impact for client particles
                    if (dist < nearestSubDist) {
                        nearestSubDist = dist;
                        nearestSubImpact = center.add(dir.scale(getEffectiveRange()));
                    }

                    double strength = getEffectiveFlow() * (Math.cos(dist / getEffectiveRange() + Math.PI / 2) + 1.0);
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

        boolean changed = false;
        if (nearestSubImpact != null) {
            if (!hasSubLevelImpact || !lastSubLevelImpactPos.equals(nearestSubImpact)) {
                lastSubLevelImpactPos = nearestSubImpact;
                hasSubLevelImpact = true;
                changed = true;
            }
        } else if (hasSubLevelImpact) {
            hasSubLevelImpact = false;
            changed = true;
        }
        if (changed)
            sendData();

        for (Iterator<Entity> it = pushingEntities.iterator(); it.hasNext(); ) {
            if (!it.next().isAlive())
                it.remove();
        }
    }

    private void scanForEntities(Level scanLevel, Vec3 center, Direction facing, SharedScanState shared) {
        Vec3 facingVec = Vec3.atLowerCornerOf(facing.getNormal());
        double halfPhiRad = Math.toRadians(getEffectivePhi() / 2.0);
        double cosHalfPhi = Math.cos(halfPhiRad);
        boolean checkLineOfSight = !onSubLevel;
        int scanRange = getEffectiveRange();

        AABB bb = new AABB(center, center).inflate(scanRange);
        for (Entity entity : scanLevel.getEntitiesOfClass(Entity.class, bb,
                e -> {
                    Vec3 d = e.position().subtract(center);
                    double dist = d.length();
                    if (dist > scanRange || dist < minRange
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
        if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
            factor = 1f / 4f;
            strength = getEffectiveFlow() / (2f * Math.max(distance, 0.01f));
            if (living.isShiftKeyDown())
                factor *= 0.25f;
            if (living.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET).getItem()
                    instanceof com.simibubi.create.content.equipment.armor.DivingBootsItem)
                factor *= 0.25f;
        } else if (entity instanceof ItemEntity) {
            factor = 1f / 2f;
            strength = getEffectiveFlow() * (float)(Math.cos(distance / getEffectiveRange() + Math.PI / 2) + 1.0);
        } else {
            factor = 1f / 2f;
            strength = getEffectiveFlow() * (float)(Math.cos(distance / getEffectiveRange() + Math.PI / 2) + 1.0);
        }
        Vec3 pushVec = diff.normalize().scale(strength);
        entity.setDeltaMovement(entity.getDeltaMovement().add(pushVec.scale(factor)));
        entity.fallDistance = 0;
        entity.hurtMarked = true;
    }

    /** Returns the center of the nozzle if one is placed on the front face, otherwise shield center. */
    private Vec3 getPushCenterLocal() {
        BlockState state = getBlockState();
        if (!state.hasProperty(ShieldBlock.FACING))
            return VecHelper.getCenterOf(worldPosition);
        BlockPos frontPos = worldPosition.relative(state.getValue(ShieldBlock.FACING));
        if (level != null && level.getBlockEntity(frontPos) instanceof NozzleBlockEntity) {
            return VecHelper.getCenterOf(frontPos);
        }
        return VecHelper.getCenterOf(worldPosition);
    }

    // ---- IAirCurrentSource ----

    @Override
    public AirCurrent getAirCurrent() { return null; }

    @Override
    public Level getAirCurrentWorld() { return level; }

    @Override
    public BlockPos getAirCurrentPos() { return worldPosition; }

    @Override
    public Direction getAirflowOriginSide() {
        return getBlockState().getValue(ShieldBlock.FACING);
    }

    @Override
    public Direction getAirFlowDirection() {
        float speed = getSpeed();
        if (speed == 0) return null;
        return getBlockState().getValue(ShieldBlock.FACING);
    }

    @Override
    public boolean isSourceRemoved() { return isRemoved(); }

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

    private boolean canSeePos(Level level, Vec3 from, Vec3 to) {
        ClipContext ctx = new ClipContext(
            from, to,
            ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE,
            net.minecraft.world.phys.shapes.CollisionContext.empty()
        );
        BlockPos hit = level.clip(ctx).getBlockPos();
        return hit.equals(BlockPos.containing(to));
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
        float load = speed == 0 ? 0 : (float) (Math.pow(speed, 2) * getEffectiveFlow() / 32);
        this.lastStressApplied = load;
        return load;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        float stressAtBase = calculateStressApplied();
        float speed = Math.abs(getSpeed());

        tooltip.add(Component.translatable("tooltip.clockworkblock.kinetic_stats")
            .withStyle(ChatFormatting.GOLD));

        tooltip.add(Component.translatable("tooltip.clockworkblock.stress_impact")
            .withStyle(ChatFormatting.GRAY));

        float stressTotal = stressAtBase * Math.abs(getTheoreticalSpeed());

        MutableComponent stressLine = Component.literal(
                String.format("%.1f SU ", stressTotal))
            .withStyle(speed == 0 ? ChatFormatting.DARK_GRAY : ChatFormatting.AQUA)
            .append(Component.translatable("tooltip.clockworkblock.at_current_speed")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(stressLine);

        tooltip.add(Component.translatable("tooltip.clockworkblock.shield_strength",
                getEffectiveFlow() / 2)
            .withStyle(ChatFormatting.GRAY));

        tooltip.add(Component.translatable("tooltip.clockworkblock.shield_radius",
                getEffectiveRange())
            .withStyle(ChatFormatting.GRAY));

        return true;
    }

    public void setPhi(int phi) {
        if (!hasNozzle())
            return;
        this.phi = Mth.clamp(phi, 45, 270);
        setChanged();
        sendData();
    }

    /** Phi is fixed at 45° without a nozzle; adjustable 45-270° with nozzle. */
    public int getEffectivePhi() {
        return hasNozzle() ? phi : 45;
    }

    private boolean hasNozzle() {
        if (level == null)
            return false;
        BlockState state = getBlockState();
        if (!state.hasProperty(ShieldBlock.FACING))
            return false;
        return level.getBlockEntity(worldPosition.relative(state.getValue(ShieldBlock.FACING)))
                instanceof NozzleBlockEntity;
    }

    public void setFlow(int flow) {
        this.flow = Mth.clamp(flow, 1, 32);
        setChanged();
        sendData();
        if (level != null && !level.isClientSide && hasNetwork())
            getOrCreateNetwork().updateStressFor(this, calculateStressApplied());
    }

    public void setRange(int range) {
        this.range = Mth.clamp(range, 1, 32);
        setChanged();
        sendData();
        if (level != null && !level.isClientSide && hasNetwork())
            getOrCreateNetwork().updateStressFor(this, calculateStressApplied());
    }

    public void setParticleColor(DyeColor color) {
        this.particleColor = color;
        setChanged();
        sendData();
    }

    public int getPhi() {
        return phi;
    }

    public int getMinRange() {
        return minRange;
    }

    public int getFlow() {
        return getEffectiveFlow();
    }

    /** Effective flow: larger of wireless redstone and wired redstone signal × 2. */
    public int getEffectiveFlow() {
        int wireless = 0;
        int wired = 0;
        if (level != null) {
            try {
                com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler handler =
                    com.simibubi.create.Create.REDSTONE_LINK_NETWORK_HANDLER;
                if (handler != null) {
                    var freqMap = handler.networksIn(level);
                    if (freqMap != null) {
                        net.createmod.catnip.data.Couple<com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency> freqKey =
                            net.createmod.catnip.data.Couple.create(getFirstFrequency(), getSecondFrequency());
                        var entry = freqMap.get(freqKey);
                        if (entry != null) {
                            for (com.simibubi.create.content.redstone.link.IRedstoneLinkable linkable : entry) {
                                wireless = Math.max(wireless, linkable.getTransmittedStrength());
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
            wired = level.getBestNeighborSignal(worldPosition);
        }
        return Math.max(wireless, wired) * 2;
    }

    /** Whether this shield has a redstone signal (wired or wireless). */
    private boolean hasRedstoneSignal() {
        return getEffectiveFlow() > 0;
    }

    public int getRange() {
        return getEffectiveRange();
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        Pair<ValueBoxTransform, ValueBoxTransform> slots =
            ValueBoxTransform.Dual.makeSlots(ShieldBlockFrequencySlot::new);
        link = new ShieldLinkBehaviour(this, slots);
        behaviours.add(link);

        // Phi (cone angle) scroll value box on the blue face (like ClockworkBlock)
        var phiValue = new com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour(
            com.simibubi.create.foundation.utility.CreateLang.translateDirect("message.clockworkblock.shield_phi"),
            this, new ShieldAngleBox());
        phiValue.between(45, 270);
        phiValue.value = phi;
        phiValue.withFormatter(v -> v + "°");
        phiValue.withCallback(v -> {
            if (!hasNozzle())
                return;
            phi = v;
            setChanged();
            sendData();
        });
        behaviours.add(phiValue);
    }

    private static class ShieldAngleBox extends com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform.Sided {
        @Override
        protected Vec3 getSouthLocation() {
            return net.createmod.catnip.math.VecHelper.voxelSpace(8, 8, 12.5);
        }

        @Override
        protected boolean isSideActive(BlockState state, Direction direction) {
            return direction == ShieldBlock.getAngleFace(state);
        }
    }

    /** Effective range: input speed / 8, clamped to [1, 32]. */
    public int getEffectiveRange() {
        return Mth.clamp((int)(Math.abs(getSpeed()) / 8), 1, 32);
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        compound.putInt("Phi", phi);
        compound.putInt("MinRange", minRange);
        compound.putInt("Flow", flow);
        compound.putInt("Range", range);
        compound.putBoolean("Active", clientActive);
        compound.putBoolean("HasSubImpact", hasSubLevelImpact);
        if (hasSubLevelImpact) {
            compound.putDouble("SubImpactX", lastSubLevelImpactPos.x);
            compound.putDouble("SubImpactY", lastSubLevelImpactPos.y);
            compound.putDouble("SubImpactZ", lastSubLevelImpactPos.z);
        }
        if (particleColor != null)
            compound.putInt("ParticleColor", particleColor.getId());
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
        clientActive = compound.getBoolean("Active");
        hasSubLevelImpact = compound.getBoolean("HasSubImpact");
        if (hasSubLevelImpact) {
            double ix = compound.getDouble("SubImpactX");
            double iy = compound.getDouble("SubImpactY");
            double iz = compound.getDouble("SubImpactZ");
            lastSubLevelImpactPos = new Vec3(ix, iy, iz);
        }
        if (compound.contains("ParticleColor"))
            particleColor = DyeColor.byId(compound.getInt("ParticleColor"));
        else
            particleColor = null;
    }

    private void addColoredParticle(double x, double y, double z,
                                     double vx, double vy, double vz, boolean glow) {
        if (particleColor != null) {
            int argb = particleColor.getTextColor();
            float r = ((argb >> 16) & 0xFF) / 255.0f;
            float g = ((argb >> 8) & 0xFF) / 255.0f;
            float b = (argb & 0xFF) / 255.0f;
            level.addParticle(new DustParticleOptions(new org.joml.Vector3f(r, g, b),
                glow ? 1.5f : 1.0f), x, y, z, vx, vy, vz);
        } else if (glow) {
            level.addParticle(ParticleTypes.END_ROD, x, y, z, vx, vy, vz);
        } else {
            level.addParticle(ParticleTypes.POOF, x, y, z, vx, vy, vz);
        }
    }
}
