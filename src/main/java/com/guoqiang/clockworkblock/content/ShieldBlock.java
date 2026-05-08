package com.guoqiang.clockworkblock.content;

import com.guoqiang.clockworkblock.ClockworkBlockEntityTypes;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.block.WrenchableDirectionalBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class ShieldBlock extends WrenchableDirectionalBlock implements IBE<ShieldBlockEntity> {

    public ShieldBlock(Properties properties) {
        super(properties);
        MovementBehaviour.REGISTRY.register(this, new ShieldBlockMovementBehaviour());
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        return InteractionResult.FAIL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            withBlockEntityDo(level, pos, be ->
                serverPlayer.openMenu(new SimpleMenuProvider(
                    (id, inv, p) -> new ShieldBlockMenu(id, inv, pos, be.getPhi(), be.getMaxRange()),
                    Component.translatable("block.clockworkblock.shield_block")
                ), buf -> {
                    buf.writeBlockPos(pos);
                    buf.writeInt(be.getPhi());
                    buf.writeInt(be.getMaxRange());
                })
            );
        }
        return InteractionResult.SUCCESS;
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
