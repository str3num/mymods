package com.guoqiang.clockworkblock.content;

import java.util.*;

import com.guoqiang.clockworkblock.ClockworkBlockEntityTypes;
import com.guoqiang.clockworkblock.client.ShieldBlockFrequencySlot;
import com.simibubi.create.content.kinetics.fan.AirCurrent;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import dev.ryanhcode.sable.api.physics.force.ForceTotal;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.tuple.Pair;
import org.joml.Vector3d;

public class ShieldBlockEntity extends SmartBlockEntity implements BlockEntitySubLevelActor {

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
    private ShieldLinkBehaviour link;
    private final List<Entity> pushingEntities = new ArrayList<>();
    private boolean onSubLevel;

    private record PendingForce(ServerSubLevel subLevel, Vec3 worldHitPos, Vec3 pushDir, float strength) {}
    private final List<PendingForce> pendingSubLevelForces = new ArrayList<>();

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

        if (!onSubLevel) {
            onSubLevel = Sable.HELPER.getContaining(this) != null;
        }

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

        SharedScanState shared = getSharedState(parentLevel);
        shared.alliedSubLevels.add(subLevel);

        scanAndPush(subLevel, parentLevel, worldCenter, facing, shared);
    }

    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        if (pendingSubLevelForces.isEmpty())
            return;

        for (PendingForce pending : pendingSubLevelForces) {
            ServerSubLevel hit = pending.subLevel;
            if (hit.isRemoved())
                continue;

            QueuedForceGroup forceGroup = hit.getOrCreateQueuedForceGroup(ForceGroups.PROPULSION.get());
            ForceTotal forceTotal = forceGroup.getForceTotal();

            Vector3d impulseLoc = JOMLConversion.toJOML(pending.worldHitPos);
            Vector3d worldImpulse = JOMLConversion.toJOML(
                pending.pushDir.scale(pending.strength * 100.0));
            Vector3d localImpulse = hit.logicalPose().transformNormalInverse(worldImpulse);

            forceTotal.applyImpulseAtPoint(hit, impulseLoc, localImpulse);
        }
        pendingSubLevelForces.clear();
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
                              Direction facing, SharedScanState shared) {
        Vec3 facingVec = Vec3.atLowerCornerOf(facing.getNormal());
        double halfPhiRad = Math.toRadians(phi / 2.0);
        double cosHalfPhi = Math.cos(halfPhiRad);
        int scanRange = flow;

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

        // 4. Create PendingForce for ALL shared sub-levels from this shield's position
        for (ServerSubLevel other : shared.subLevels) {
            if (other == ownSubLevel || other.isRemoved())
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
            float pushStrength = flow / (2.0f * (float) distance);
            pendingSubLevelForces.add(
                new PendingForce(other, otherCenter, dir, pushStrength));
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

    private void tickClientOnly(Vec3 center, Direction facing) {
        if (flow <= 0)
            return;

        int scanRange = flow;

        if (level.random.nextInt(10) == 0) {
            Vec3 dir = Vec3.atLowerCornerOf(facing.getNormal());
            Vec3 start = VecHelper.offsetRandomly(center, level.random, scanRange * 0.4f);
            start = start.add(dir.scale(level.random.nextFloat() * scanRange * 0.6f));
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

    private void tickServer(Level scanLevel, Vec3 center, Direction facing) {
        SharedScanState shared = getSharedState(scanLevel);

        scanForEntities(scanLevel, center, facing, shared);

        // Push ALL shared entities from this shield's position
        for (Entity entity : shared.entities) {
            if (!entity.isAlive())
                continue;
            Vec3 diff = entity.position().subtract(center);
            double distance = diff.length();
            if (distance > flow || distance < minRange
                || entity.isShiftKeyDown()
                || AirCurrent.isPlayerCreativeFlying(entity))
                continue;
            applyPushForce(entity, diff, (float) distance);
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
        int scanRange = flow;

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

    private void applyPushForce(Entity entity, Vec3 diff, float distance) {
        float factor = (entity instanceof ItemEntity) ? 1f / 128f : 1f / 32f;
        float strength = flow / (2.0f * Math.max(distance, 0.01f));
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

    public void adjustPhi(int delta) {
        phi = Mth.clamp(phi + delta, 45, 145);
        setChanged();
        sendData();
    }

    public void adjustFlow(int delta) {
        flow = Mth.clamp(flow + delta, 1, 32);
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
        return flow;
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        compound.putInt("Phi", phi);
        compound.putInt("MinRange", minRange);
        compound.putInt("Flow", flow);
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        phi = Mth.clamp(compound.getInt("Phi"), 45, 145);
        minRange = compound.getInt("MinRange");
        if (compound.contains("Flow"))
            flow = Mth.clamp(compound.getInt("Flow"), 1, 32);
        else
            flow = Mth.clamp(compound.getInt("MaxRange"), 1, 32);
    }
}
