package com.guoqiang.clockworkblock.content;

import java.util.List;

import com.guoqiang.clockworkblock.ClockworkBlockEntityTypes;

import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class ClockworkBlockEntity extends GeneratingKineticBlockEntity {
    public static final int MAX_POWER = 65536;
    public static final int POWER_PER_RPM = 256;
    public static final int MAX_ENERGY = MAX_POWER * 60 * 20;
    public static final int DEFAULT_OUTPUT_SPEED = 64;
    public static final int DEFAULT_POWER = POWER_PER_RPM;
    public static final int[] OUTPUT_SPEED_STEPS = {16, 32, 64, 128};
    private int storedEnergy;
    private int outputSpeed = DEFAULT_OUTPUT_SPEED;
    private int configuredPower = DEFAULT_POWER;
    private boolean lastRedstoneState;
    private boolean lastLoadState = true;

    public ClockworkBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public ClockworkBlockEntity(BlockPos pos, BlockState state) {
        this(ClockworkBlockEntityTypes.CLOCKWORK_BLOCK.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
    }

    @Override
    public void initialize() {
        super.initialize();
        syncPoweredState(hasRedstoneSignal());
        lastRedstoneState = hasRedstoneSignal();
        lastLoadState = isChargingLoadActive();
        if (shouldDischarge()) {
            updateGeneratedRotation();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) {
            return;
        }

        boolean redstoneState = hasRedstoneSignal();
        if (redstoneState != lastRedstoneState) {
            lastRedstoneState = redstoneState;
            syncPoweredState(redstoneState);
            updateGeneratedRotation();
        }

        int previousEnergy = storedEnergy;
        if (shouldDischarge()) {
            int dischargeCost = getDischargeCost();
            if (dischargeCost > 0) {
                storedEnergy = Math.max(0, storedEnergy - dischargeCost);
            }
            if (storedEnergy == 0) {
                updateGeneratedRotation();
            }
        } else if (!isEnergyFull()) {
            storedEnergy = Math.min(MAX_ENERGY, storedEnergy + getChargeAmount());
        }

        boolean loadState = isChargingLoadActive();
        if (loadState != lastLoadState) {
            lastLoadState = loadState;
            updateStressValues();
        }

        if (storedEnergy != previousEnergy) {
            setChanged();
            sendData();
        }
    }

    @Override
    public float getGeneratedSpeed() {
        if (!shouldDischarge()) {
            return 0;
        }
        Direction facing = getBlockState().getValue(ClockworkBlock.FACING);
        return convertToDirection(outputSpeed, facing);
    }

    @Override
    public float calculateAddedStressCapacity() {
        float capacity = shouldDischarge() ? getGeneratedStressCapacity() : 0;
        lastCapacityProvided = capacity;
        return capacity;
    }

    @Override
    public float calculateStressApplied() {
        float impact = 0;
        if (isChargingLoadActive()) {
            impact = Math.abs(getSpeed()) * 512;
        }
        lastStressApplied = impact;
        return impact;
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        compound.putInt("StoredEnergy", storedEnergy);
        compound.putInt("OutputSpeed", outputSpeed);
        compound.putBoolean("LastRedstoneState", lastRedstoneState);
        super.write(compound, registries, clientPacket);
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        storedEnergy = compound.getInt("StoredEnergy");
        outputSpeed = sanitizeOutputSpeed(compound.getInt("OutputSpeed"));
        lastRedstoneState = compound.getBoolean("LastRedstoneState");
        super.read(compound, registries, clientPacket);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        boolean added = false;

        float stressAtBase = calculateStressApplied();
        if (!Mth.equal(stressAtBase, 0)) {
            CreateLang.translate("gui.goggles.kinetic_stats")
                .forGoggles(tooltip);

            CreateLang.translate("tooltip.stressImpact")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);

            float stressTotal = stressAtBase * Math.abs(getTheoreticalSpeed());

            CreateLang.number(stressTotal)
                .translate("generic.unit.stress")
                .style(ChatFormatting.AQUA)
                .space()
                .add(CreateLang.translate("gui.goggles.at_current_speed")
                    .style(ChatFormatting.DARK_GRAY))
                .forGoggles(tooltip, 1);

            added = true;
        }

        CreateLang.translate("tooltip.clockworkblock.stored_energy")
            .style(ChatFormatting.GRAY)
            .forGoggles(tooltip);

        CreateLang.builder()
            .add(CreateLang.number(storedEnergy).style(ChatFormatting.GOLD))
            .text(ChatFormatting.GRAY, " / ")
            .add(CreateLang.number(MAX_ENERGY).style(ChatFormatting.DARK_GRAY))
            .text(ChatFormatting.DARK_GRAY, " SU·tick")
            .forGoggles(tooltip, 1);

        CreateLang.translate("tooltip.clockworkblock.power_label")
            .style(ChatFormatting.GRAY)
            .forGoggles(tooltip);

        CreateLang.builder()
            .add(CreateLang.number(getPower()).style(ChatFormatting.AQUA))
            .text(ChatFormatting.DARK_GRAY, " SU")
            .forGoggles(tooltip, 1);

        return true;
    }

    public int getStoredEnergy() {
        return storedEnergy;
    }

    public void setStoredEnergy(int storedEnergy) {
        this.storedEnergy = Mth.clamp(storedEnergy, 0, MAX_ENERGY);
        lastLoadState = isChargingLoadActive();
        setChanged();
    }

    public int getOutputSpeed() {
        return outputSpeed;
    }

    public int getPower() {
        return configuredPower;
    }

    public void setOutputSpeed(int outputSpeed) {
        int sanitized = sanitizeOutputSpeed(outputSpeed);
        if (this.outputSpeed == sanitized) {
            return;
        }
        this.outputSpeed = sanitized;
        if (level != null && !level.isClientSide) {
            updateGeneratedRotation();
            sendData();
        }
    }

    public void setPower(int power) {
        configuredPower = Mth.clamp(power, POWER_PER_RPM, MAX_POWER);
    }

    public int getComparatorOutput() {
        return Mth.floor((storedEnergy / (float) MAX_ENERGY) * 15.0F);
    }

    private boolean shouldDischarge() {
        return hasRedstoneSignal() && storedEnergy > 0;
    }

    private boolean isChargingLoadActive() {
        return !hasRedstoneSignal() && !isEnergyFull() && hasNetwork()
            && Math.abs(getSpeed()) > 0;
    }

    private boolean isEnergyFull() {
        return storedEnergy >= MAX_ENERGY;
    }

    private boolean hasRedstoneSignal() {
        return level != null && level.hasNeighborSignal(worldPosition);
    }

    private int getChargeAmount() {
        return isChargingLoadActive() ? getPower() : 0;
    }

    private int getDischargeCost() {
        if (!hasNetwork() || !shouldDischarge()) {
            return 0;
        }
        return Math.max(1, Mth.ceil(stress));
    }

    private float getGeneratedStressCapacity() {
        float speed = Math.abs(outputSpeed);
        if (speed <= 0) {
            return 0;
        }
        return MAX_ENERGY / speed;
    }

    private void updateStressValues() {
        if (!hasNetwork()) {
            return;
        }
        getOrCreateNetwork().updateStressFor(this, calculateStressApplied());
        getOrCreateNetwork().updateStress();
        sendData();
    }

    private void syncPoweredState(boolean powered) {
        BlockState state = getBlockState();
        if (state.getValue(ClockworkBlock.POWERED) == powered) {
            return;
        }
        setBlockState(state.setValue(ClockworkBlock.POWERED, powered));
    }

    private int sanitizeOutputSpeed(int outputSpeed) {
        for (int value : OUTPUT_SPEED_STEPS) {
            if (value == outputSpeed) {
                return value;
            }
        }
        return DEFAULT_OUTPUT_SPEED;
    }
}
