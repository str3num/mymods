package com.guoqiang.clockworkblock.content;

import com.guoqiang.clockworkblock.ClockworkMenuTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class ShieldBlockMenu extends AbstractContainerMenu {

    private final BlockPos pos;
    private int phi;
    private int flow;
    private int range;

    public ShieldBlockMenu(int id, Inventory inv, BlockPos pos, int phi, int flow, int range) {
        super(ClockworkMenuTypes.SHIELD_BLOCK.get(), id);
        this.pos = pos;
        this.phi = phi;
        this.flow = flow;
        this.range = range;
    }

    public ShieldBlockMenu(int id, Inventory inv, RegistryFriendlyByteBuf extraData) {
        super(ClockworkMenuTypes.SHIELD_BLOCK.get(), id);
        this.pos = extraData.readBlockPos();
        this.phi = extraData.readInt();
        this.flow = extraData.readInt();
        this.range = extraData.readInt();
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        Level level = player.level();
        if (level.isClientSide)
            return true;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof ShieldBlockEntity shield))
            return false;

        int paramIndex = buttonId >> 8;
        int value = buttonId & 0xFF;
        switch (paramIndex) {
            case 0 -> shield.setPhi(value);
            case 1 -> shield.setFlow(value);
            case 2 -> shield.setRange(value);
        }

        this.phi = shield.getPhi();
        this.flow = shield.getFlow();
        this.range = shield.getRange();

        return true;
    }

    public int getPhi() {
        return phi;
    }

    public int getFlow() {
        return flow;
    }

    public int getRange() {
        return range;
    }

    public BlockPos getBlockPos() {
        return pos;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
