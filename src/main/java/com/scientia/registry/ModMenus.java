package com.scientia.registry;

import com.scientia.ScientiaMod;
import com.scientia.menu.MachineMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, ScientiaMod.MODID);

    /** One menu type serves every machine; the screen chooses its panel from the block entity. */
    public static final RegistryObject<MenuType<MachineMenu>> MACHINE = MENUS.register("machine",
            () -> IForgeMenuType.create((id, inv, buf) -> new MachineMenu(id, inv, buf.readBlockPos())));

    private ModMenus() {
    }
}
