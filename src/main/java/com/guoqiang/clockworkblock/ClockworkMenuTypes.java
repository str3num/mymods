package com.guoqiang.clockworkblock;

import java.util.function.Supplier;

import com.guoqiang.clockworkblock.content.ShieldBlockMenu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ClockworkMenuTypes {
    private static final DeferredRegister<MenuType<?>> MENU_TYPES =
        DeferredRegister.create(Registries.MENU, ClockworkBlockMod.MOD_ID);

    public static final Supplier<MenuType<ShieldBlockMenu>> SHIELD_BLOCK =
        MENU_TYPES.register("shield_block",
            () -> IMenuTypeExtension.create(ShieldBlockMenu::new));

    private ClockworkMenuTypes() {
    }

    public static void register(net.neoforged.bus.api.IEventBus eventBus) {
        MENU_TYPES.register(eventBus);
    }
}
