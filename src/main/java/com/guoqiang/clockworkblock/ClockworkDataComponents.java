package com.guoqiang.clockworkblock;

import java.util.function.UnaryOperator;

import com.mojang.serialization.Codec;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ClockworkDataComponents {
    private static final DeferredRegister.DataComponents DATA_COMPONENTS =
        DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, ClockworkBlockMod.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> STORED_ENERGY = register(
        "stored_energy",
        builder -> builder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.INT)
    );
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> OUTPUT_SPEED = register(
        "output_speed",
        builder -> builder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.INT)
    );
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> RESISTANCE = register(
        "resistance",
        builder -> builder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.INT)
    );
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> POWER = register(
        "power",
        builder -> builder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.INT)
    );

    private ClockworkDataComponents() {
    }

    public static void register(IEventBus eventBus) {
        DATA_COMPONENTS.register(eventBus);
    }

    private static <T> DeferredHolder<DataComponentType<?>, DataComponentType<T>> register(String id, UnaryOperator<DataComponentType.Builder<T>> operator) {
        return DATA_COMPONENTS.register(id, () -> operator.apply(DataComponentType.builder()).build());
    }
}
