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
    private int maxRange;

    public ShieldBlockMenu(int id, Inventory inv, BlockPos pos, int phi, int maxRange) {
        super(ClockworkMenuTypes.SHIELD_BLOCK.get(), id);
        this.pos = pos;
        this.phi = phi;
        this.maxRange = maxRange;
    }

    public ShieldBlockMenu(int id, Inventory inv, RegistryFriendlyByteBuf extraData) {
        super(ClockworkMenuTypes.SHIELD_BLOCK.get(), id);
        this.pos = extraData.readBlockPos();
        this.phi = extraData.readInt();
        this.maxRange = extraData.readInt();
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        Level level = player.level();
        if (level.isClientSide)
            return true;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof ShieldBlockEntity shield))
            return false;

        switch (buttonId) {
            case 0: shield.adjustPhi(-15); break;   // phi--
            case 1: shield.adjustPhi(15);  break;    // phi++
            case 2: shield.adjustMaxRange(-2); break; // maxRange--
            case 3: shield.adjustMaxRange(2);  break; // maxRange++
        }

        this.phi = shield.getPhi();
        this.maxRange = shield.getMaxRange();

        return true;
    }

    public int getPhi() {
        return phi;
    }

    public int getMaxRange() {
        return maxRange;
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
