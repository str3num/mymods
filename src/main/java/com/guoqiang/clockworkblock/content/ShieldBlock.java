package com.guoqiang.clockworkblock.content;

import com.guoqiang.clockworkblock.ClockworkBlockEntityTypes;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class ShieldBlock extends DirectionalKineticBlock implements IBE<ShieldBlockEntity> {

    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public ShieldBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(POWERED, false));
        MovementBehaviour.REGISTRY.register(this, new ShieldBlockMovementBehaviour());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(POWERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face == state.getValue(FACING).getOpposite();
    }

    @Override
    public Axis getRotationAxis(BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        return InteractionResult.FAIL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        return InteractionResult.PASS;
    }

    /** Red face (left side): set frequency with item. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                               Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (player.isShiftKeyDown())
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

        // Dye: right-click any face with dye to set particle color
        if (stack.getItem() instanceof net.minecraft.world.item.DyeItem dye) {
            if (!level.isClientSide) {
                withBlockEntityDo(level, pos, be ->
                    be.setParticleColor(dye.getDyeColor()));
            }
            return ItemInteractionResult.SUCCESS;
        }

        if (hitResult.getDirection() != getFrequencyFace(state))
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

        if (!level.isClientSide) {
            withBlockEntityDo(level, pos, be ->
                be.setFrequency(isFirstSlot(state, hitResult), stack));
        }
        return ItemInteractionResult.SUCCESS;
    }

    /** Red face: left side. After X rotation, west stays west. */
    public static Direction getFrequencyFace(BlockState state) {
        Direction facing = state.getValue(FACING);
        if (facing.getAxis().isHorizontal())
            return facing.getCounterClockWise();
        return Direction.WEST;
    }

    /** Blue face: right side. After X rotation, east stays east. */
    public static Direction getAngleFace(BlockState state) {
        Direction facing = state.getValue(FACING);
        if (facing.getAxis().isHorizontal())
            return facing.getClockWise();
        return Direction.EAST;
    }

    private static boolean isFirstSlot(BlockState state, BlockHitResult hit) {
        Direction freqFace = getFrequencyFace(state);
        if (hit.getDirection() != freqFace)
            return true;

        Vec3 local = hit.getLocation().subtract(Vec3.atLowerCornerOf(hit.getBlockPos()));
        if (freqFace.getAxis().isHorizontal()) {
            return local.y > 0.5;
        }
        return freqFace == Direction.DOWN ? local.z < 0.5 : local.z > 0.5;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader worldIn, BlockPos pos) {
        return true;
    }

    @Override
    public Class<ShieldBlockEntity> getBlockEntityClass() {
        return ShieldBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends ShieldBlockEntity> getBlockEntityType() {
        return ClockworkBlockEntityTypes.SHIELD_BLOCK.get();
    }
}
