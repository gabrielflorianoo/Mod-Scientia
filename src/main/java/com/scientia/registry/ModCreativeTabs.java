package com.scientia.registry;

import com.scientia.ScientiaMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ScientiaMod.MODID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.scientia"))
            .icon(() -> new ItemStack(ModBlocks.PRECISION_BALANCE.get()))
            .displayItems((params, output) -> {
                for (var b : ModBlocks.ALL) output.accept(ForgeRegistries.ITEMS.getValue(b.getId()));
                for (var i : ModItems.TOOLS) output.accept(i.get());
            })
            .build());

    private ModCreativeTabs() {
    }
}
