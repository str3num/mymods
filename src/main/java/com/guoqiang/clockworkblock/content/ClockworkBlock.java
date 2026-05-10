package com.guoqiang.clockworkblock.content;

import java.util.List;

import com.guoqiang.clockworkblock.ClockworkBlockEntityTypes;
import com.guoqiang.clockworkblock.ClockworkBlocks;
import com.guoqiang.clockworkblock.ClockworkDataComponents;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class ClockworkBlock extends DirectionalKineticBlock implements IBE<ClockworkBlockEntity> {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public ClockworkBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(POWERED, false));
    }

    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face == getActiveShaftFace(state);
    }

    @Override
    public Axis getRotationAxis(BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    @Override
    protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
        builder.add(POWERED);
        super.createBlockStateDefinition(builder);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return super.getStateForPlacement(context)
            .setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    @Override
    protected boolean areStatesKineticallyEquivalent(BlockState oldState, BlockState newState) {
        return super.areStatesKineticallyEquivalent(oldState, newState)
            && oldState.getValue(POWERED) == newState.getValue(POWERED);
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return getBlockEntityOptional(level, pos)
            .map(ClockworkBlockEntity::getComparatorOutput)
            .orElse(0);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        return InteractionResult.PASS;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) {
            return;
        }
        withBlockEntityDo(level, pos, blockEntity -> {
            blockEntity.setStoredEnergy(stack.getOrDefault(ClockworkDataComponents.STORED_ENERGY.get(), 0));
            blockEntity.setOutputSpeed(stack.getOrDefault(ClockworkDataComponents.OUTPUT_SPEED.get(), ClockworkBlockEntity.DEFAULT_OUTPUT_SPEED));
            int storedPower = stack.getOrDefault(
                ClockworkDataComponents.POWER.get(),
                stack.getOrDefault(ClockworkDataComponents.RESISTANCE.get(), ClockworkBlockEntity.DEFAULT_POWER)
            );
            blockEntity.setPower(storedPower);
        });
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (level.isClientSide) {
            return;
        }
        boolean powered = level.hasNeighborSignal(pos);
        if (state.getValue(POWERED) != powered) {
            KineticBlockEntity.switchToBlockState(level, pos, state.setValue(POWERED, powered));
        }
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        List<ItemStack> drops = super.getDrops(state, builder);
        BlockEntity blockEntity = builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (!(blockEntity instanceof ClockworkBlockEntity clockworkBlockEntity)) {
            return drops;
        }

        return drops.stream()
            .peek(stack -> applyStoredState(stack, clockworkBlockEntity))
            .toList();
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        ItemStack stack = new ItemStack(ClockworkBlocks.CLOCKWORK_BLOCK_ITEM.get());
        getBlockEntityOptional(level, pos).ifPresent(blockEntity -> applyStoredState(stack, blockEntity));
        return stack;
    }

    @Override
    public Class<ClockworkBlockEntity> getBlockEntityClass() {
        return ClockworkBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends ClockworkBlockEntity> getBlockEntityType() {
        return ClockworkBlockEntityTypes.CLOCKWORK_BLOCK.get();
    }

    private static void applyStoredState(ItemStack stack, ClockworkBlockEntity blockEntity) {
        stack.set(ClockworkDataComponents.STORED_ENERGY.get(), blockEntity.getStoredEnergy());
        stack.set(ClockworkDataComponents.OUTPUT_SPEED.get(), blockEntity.getOutputSpeed());
        stack.set(ClockworkDataComponents.POWER.get(), blockEntity.getPower());
    }

    public static Direction getActiveShaftFace(BlockState state) {
        return state.getValue(POWERED) ? getOutputFace(state) : getInputFace(state);
    }

    public static Direction getOutputFace(BlockState state) {
        return state.getValue(FACING);
    }

    public static Direction getInputFace(BlockState state) {
        return getOutputFace(state).getOpposite();
    }
}
