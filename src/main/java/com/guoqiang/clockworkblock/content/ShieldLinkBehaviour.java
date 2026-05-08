package com.guoqiang.clockworkblock.content;

import org.apache.commons.lang3.tuple.Pair;

import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;

import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class ShieldLinkBehaviour extends BlockEntityBehaviour {

    public static final BehaviourType<ShieldLinkBehaviour> TYPE = new BehaviourType<>();

    public Frequency frequencyFirst;
    public Frequency frequencyLast;
    public ValueBoxTransform firstSlot;
    public ValueBoxTransform secondSlot;

    public ShieldLinkBehaviour(SmartBlockEntity be, Pair<ValueBoxTransform, ValueBoxTransform> slots) {
        super(be);
        frequencyFirst = Frequency.EMPTY;
        frequencyLast = Frequency.EMPTY;
        firstSlot = slots.getLeft();
        secondSlot = slots.getRight();
    }

    public void setFrequency(boolean first, ItemStack stack) {
        stack = stack.copy();
        stack.setCount(1);
        if (first)
            frequencyFirst = Frequency.of(stack);
        else
            frequencyLast = Frequency.of(stack);
        blockEntity.setChanged();
        blockEntity.sendData();
    }

    public Couple<Frequency> getNetworkKey() {
        return Couple.create(frequencyFirst, frequencyLast);
    }

    public boolean testHit(Boolean first, Vec3 hit) {
        BlockState state = blockEntity.getBlockState();
        Vec3 localHit = hit.subtract(Vec3.atLowerCornerOf(blockEntity.getBlockPos()));
        return (first ? firstSlot : secondSlot).testHit(getWorld(), getPos(), state, localHit);
    }

    @Override
    public BehaviourType<?> getType() {
        return TYPE;
    }

    @Override
    public boolean isSafeNBT() { return true; }

    @Override
    public void write(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(nbt, registries, clientPacket);
        nbt.put("FrequencyFirst", frequencyFirst.getStack().saveOptional(registries));
        nbt.put("FrequencyLast", frequencyLast.getStack().saveOptional(registries));
    }

    @Override
    public void read(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(nbt, registries, clientPacket);
        frequencyFirst = Frequency.of(ItemStack.parseOptional(registries, nbt.getCompound("FrequencyFirst")));
        frequencyLast = Frequency.of(ItemStack.parseOptional(registries, nbt.getCompound("FrequencyLast")));
    }

    public boolean isAlive() {
        Level level = getWorld();
        BlockPos pos = getPos();
        if (blockEntity.isChunkUnloaded())
            return false;
        if (blockEntity.isRemoved())
            return false;
        if (!level.isLoaded(pos))
            return false;
        return level.getBlockEntity(pos) == blockEntity;
    }
}
